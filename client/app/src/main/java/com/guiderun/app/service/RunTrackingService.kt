package com.guiderun.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.local.entity.RunTrackBufferEntity
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.PaceCalculator
import com.guiderun.app.util.SimpleKalmanFilter
import com.guiderun.app.util.SpeedSmoother
import java.util.LinkedList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 跑步轨迹采集 + 实时数据聚合的前台服务。
 *
 * 设计要点：
 * 1. **唯一写入源**：所有 RunSessionStats 字段（距离/时长/瞬时配速/平均配速）只在该 Service 内部写入，
 *    上游 ViewModel 仅订阅 stats，不再各自起 ticker —— 避免多源写同字段产生闪烁/跳变。
 * 2. **单调时钟**：时长基于 [SystemClock.elapsedRealtime]，不受系统时间被改影响；点位时间优先
 *    使用 [com.guiderun.app.domain.model.GeoPoint.realtimeMs]（来自 Location.elapsedRealtimeNanos）。
 * 3. **位移 / 速度门控**：
 *    - 精度差（accuracy > 阈值）→ 丢弃
 *    - 相邻点位移 < MIN_DELTA_M 且 dt < MIN_DELTA_TIME_MS → 不计入距离（屏蔽静止漂移）
 *    - 瞬时速度 > MAX_SPEED_MPS（≈ 43 km/h）→ 视为离群点，整帧丢弃
 * 4. **配速**：基于多普勒速度（Location.getSpeed）做 [SpeedSmoother] 5s 滑动平均，单层平滑。
 * 5. **距离精度**：累加器使用 [Double]，长跑无累加误差。
 * 6. **写入节奏**：每帧 location 触发一次 stats 写；额外 1 Hz 兜底 tick 保证无新点时时长仍涨。
 * 7. **写入并发**：通过 [statsMutex] 串行化 stats 读 / 写，避免 location 帧与 1Hz tick 同时 upsert。
 */
abstract class RunTrackingService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var runRequestRepository: RunRequestRepository
    @Inject lateinit var trackBufferDao: RunTrackBufferDao
    @Inject lateinit var sessionStatsDao: RunSessionStatsDao
    @Inject lateinit var userPreferences: UserPreferences

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var uploadJob: Job? = null
    private var tickerJob: Job? = null

    private var startElapsedMs: Long = 0L
    private var currentRequestId: String = ""
    private var currentUserId: String = ""

    // 上一个有效"原始点"（用于距离累加：用未平滑坐标，避免滤波系统性低估距离）
    private var lastRawLat: Double? = null
    private var lastRawLng: Double? = null
    private var lastRawRealtimeMs: Long = 0L

    // 上一个"平滑后"点位（用于轨迹写入与展示）
    private var lastSmoothLat: Double? = null
    private var lastSmoothLng: Double? = null

    private var accumulatedDistanceM: Double = 0.0
    // 本帧"有效速度"（m/s）：多普勒优先，缺失时 fallback 位置差分。供距离门控 / 暂停判定 / maxSpeed / 轨迹缓存共用。
    private var lastEffectiveSpeedMps: Float = 0f
    // 最近一帧有效定位的 elapsedRealtime；用于 flushStats 判断帧是否新鲜（陈旧则按静止推进暂停）。
    private var lastFrameElapsedMs: Long = 0L

    // 多普勒健康检测：部分机型 / 模拟定位 hasSpeed()=true 但 speed 恒 0，
    // 会让距离/配速冻死、暂停无法恢复。累计"多普勒报~0 却位置明显持续移动"的帧，超阈值则本会话弃用多普勒。
    private var dopplerZeroMovingFrames = 0
    private var dopplerUnavailable = false

    // 配速平滑器：对多普勒速度做 5s 滑动平均，单层平滑（替代旧的 PaceWindow + EMA 双层）
    private val speedSmoother = SpeedSmoother()
    private val statsMutex = Mutex()

    // 采样间隔：活动中固定 1Hz；自动暂停时降频省电（暂停期配速冻结，不影响手感）
    private val locationIntervalFlow = MutableStateFlow(DEFAULT_INTERVAL_MS)

    // 自动暂停：连续低速 → 冻结"距离/配速/运动时长"
    private var isPaused: Boolean = false
    private var lowSpeedSinceRealtimeMs: Long = -1L
    private var pendingResumeSinceRealtimeMs: Long = -1L

    // 累计已结束的暂停时长；正在进行中的暂停在 flushStats 内单独扣除
    private var pausedAccumMs: Long = 0L
    private var pauseStartedAtRealtimeMs: Long = -1L

    // 滤波（仅用于轨迹与展示坐标，不影响距离计算）
    private val kalmanFilter = SimpleKalmanFilter(processNoise = 0.001, measurementNoise = 5.0)
    private val medianLatBuffer = LinkedList<Double>()
    private val medianLngBuffer = LinkedList<Double>()

    protected abstract val role: String
    protected abstract val notificationTitle: String
    protected abstract val notificationText: String
    protected abstract val locationIntervalMs: Long

    /**
     * 子类提供点击通知后跳转的 Intent（按角色拉回对应 Running 页）。
     * 返回 null 时通知不可点击。
     */
    protected abstract fun createContentIntent(requestId: String): Intent?

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
        // 固定 1Hz 采集：主流运动 App 标准，配速/距离/时长每秒平滑刷新
        const val DEFAULT_INTERVAL_MS = 1_000L
        // 自动暂停时降频省电（暂停期配速冻结，2s 仍足够快识别"又跑起来"）
        private const val PAUSED_INTERVAL_MS = 2_000L

        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "run_tracking"
        private const val UPLOAD_BATCH_SIZE = 100
        // 15s 周期增量上传：缩短窗口，弱网/中途退出时已上传的点更多，配合结束 flush 减少丢失
        private const val UPLOAD_INTERVAL_MS = 15_000L
        private const val TICK_INTERVAL_MS = 1_000L

        // 精度门控
        private const val ACCURACY_THRESHOLD_M = 30f
        // 多普勒速度精度门控：speedAccuracy 超过该值视为不可信，回退位置差分
        private const val SPEED_ACCURACY_THRESHOLD_MPS = 2f
        // 多普勒健康检测：speed < 该值视为"报 0"
        private const val DOPPLER_ZERO_EPS_MPS = 0.1f
        // 位置差分速度 > 该值视为"明显在动"（高于静止 GPS 抖动噪声，约等于慢走）
        private const val DOPPLER_HEALTH_SPEED_MPS = 1.0f
        // 连续 N 帧"多普勒报 0 但位置在动"才判定多普勒坏（≈8s，避免偶发抖动误判）
        private const val DOPPLER_HEALTH_FRAMES = 8

        // 离群点阈值（按瞬时速度判断）
        private const val MAX_SPEED_MPS = 12f         // ≈ 43 km/h
        // 移动门控：有效速度 ≥ 该值才累加距离，否则视为静止（杀 GPS 漂移）
        // 取代旧的 MIN_DELTA_M/isMicroMove/NOISE_ACCURACY 位移启发式（那套在 1Hz 下会误杀步行）
        private const val MOVING_SPEED_MPS = 0.5f

        // 帧新鲜度阈值：超过该时长无有效定位帧，flushStats 按"静止（速度 0）"推进暂停判定。
        // 取 3s（活动期望 1Hz 出帧），覆盖 GPS 未锁定 / 室内信号差 / 帧被精度门控丢弃等场景。
        private const val FRAME_STALE_MS = 3_000L

        // 自动暂停判定（用于距离/配速冻结）
        private const val PAUSE_SPEED_MPS = 0.7f      // 持续低于该速度才入暂停
        private const val PAUSE_DURATION_MS = 10_000L // 持续 10s 才入暂停
        private const val RESUME_SPEED_MPS = 1.0f     // 高于该速度才恢复
        private const val RESUME_DURATION_MS = 3_000L // 持续 3s 才退出暂停

    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestIdFromIntent = intent?.getStringExtra(EXTRA_REQUEST_ID)
        if (requestIdFromIntent != null) {
            // 幂等：同一 requestId + 已在跑的采集 Job 直接复用。
            // ViewModel 重建会调 startForegroundService，若不拦截会重新触发 restoreFromPersistedStats，
            // 把 startElapsedMs 前移而暂停状态字段仍残留旧绝对时间戳 → movingMs 变负 coerce 成 0（见 Bug A）。
            if (currentRequestId == requestIdFromIntent && trackingJob?.isActive == true) {
                startForeground(NOTIFICATION_ID, buildNotification(requestIdFromIntent))
                return START_REDELIVER_INTENT
            }
            initTracking(requestIdFromIntent)
        } else {
            // START_REDELIVER_INTENT 极端情况下 intent 丢失（进程被杀 + 系统重拉起）：
            // 立即 startForeground 避免主线程阻塞 / ANR，再异步从 DataStore 读 requestId 恢复采集。
            startForeground(NOTIFICATION_ID, buildNotification(""))
            scope.launch {
                val id = userPreferences.getActiveRequestId() ?: run { stopSelf(); return@launch }
                initTracking(id)
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun initTracking(requestId: String) {
        currentRequestId = requestId
        startForeground(NOTIFICATION_ID, buildNotification(requestId))
        locationIntervalFlow.value = locationIntervalMs
        scope.launch {
            currentUserId = userPreferences.getCurrentUserId() ?: ""
            // 杀 App 重启后从 DB 恢复历史累积，避免距离/时长归零
            restoreFromPersistedStats(requestId)
            startTracking(requestId)
            startStatsTicker(requestId)
            startPeriodicUpload(requestId)
        }
    }

    /**
     * 从 [RunSessionStatsEntity] 恢复杀 App 前的累积距离 / 时长，让 UI 接着算而不是从 0 开始。
     *
     * 关键技巧：把 [startElapsedMs] 倒推到 "现在 - 已跑时长"，
     * 后续 [flushStats] 用单调时钟差计算的 movingMs 自然含上恢复的时长。
     * pausedAccumMs 保持 0：已经把暂停时段视为合并进 totalDurationSeconds，不再单独还原。
     *
     * 必须重置所有暂停相关字段：[pauseStartedAtRealtimeMs] 是绝对 elapsedRealtime 时间戳，
     * 若沿用上一次进程的值会与新 [startElapsedMs] 基线错位 ——
     * 当 onStartCommand 在 service 已运行时被再次触发（ViewModel 重建调 startForegroundService），
     * `ongoingPauseMs` 会远大于 `nowElapsed - startElapsedMs`，让 movingMs 变负 coerce 成 0，
     * 进而把 totalDurationSeconds 写成 0，永久污染历史。
     */
    private suspend fun restoreFromPersistedStats(requestId: String) {
        val prev = sessionStatsDao.get(requestId, currentUserId)
        val now = SystemClock.elapsedRealtime()
        // 这些字段先全部归零；下面再按需恢复暂停态。
        // 全部归零是因为 pauseStartedAtRealtimeMs / lowSpeedSinceRealtimeMs / pausedAccumMs
        // 都是绝对 elapsedRealtime 或基于上次进程的累积，跨进程沿用会基线错位。
        isPaused = false
        pauseStartedAtRealtimeMs = -1L
        pausedAccumMs = 0L
        lowSpeedSinceRealtimeMs = -1L
        pendingResumeSinceRealtimeMs = -1L

        if (prev != null) {
            accumulatedDistanceM = prev.totalDistanceMeters.toDouble()
            startElapsedMs = now - prev.totalDurationSeconds * 1000L
            // 关键：若上次会话退出时是暂停态，重进必须继承，避免 UI 短暂闪现"运行中"再切回"暂停"。
            // 把 pauseStartedAt 锚到当下：等价于"从此刻开始暂停"——
            // movingMs = now - startElapsedMs - 0 - (now - now) = totalDurationSeconds × 1000，timer 不变。
            // 后续真实速度回升再正常退出暂停，pausedAccumMs 自动吃掉"在暂停态停留的时间"。
            if (prev.isPaused) {
                isPaused = true
                pauseStartedAtRealtimeMs = now
            }
        } else {
            startElapsedMs = now
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startTracking(requestId: String) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            locationIntervalFlow.flatMapLatest { interval ->
                locationProvider.locationUpdates(interval)
            }.collect { geoPoint ->
                handleLocation(requestId, geoPoint)
            }
        }
    }

    private suspend fun handleLocation(requestId: String, geo: com.guiderun.app.domain.model.GeoPoint) {
        // 1. 精度门控
        if (geo.accuracy > ACCURACY_THRESHOLD_M) return

        // 2. 单调时间戳：优先用 GPS 自带 elapsedRealtime，否则 fallback 到当前 elapsedRealtime
        val nowRealtimeMs = if (geo.realtimeMs > 0L) geo.realtimeMs else SystemClock.elapsedRealtime()
        val wallNowMs = System.currentTimeMillis()

        // 3. 位置差分（用原始坐标）：用于距离累加 + 多普勒缺失时的速度兜底
        val prevRawLat = lastRawLat
        val prevRawLng = lastRawLng
        val prevRawTime = lastRawRealtimeMs

        var deltaM = 0f
        var deltaTimeMs = 0L
        var posDiffSpeed = 0f
        if (prevRawLat != null && prevRawLng != null && prevRawTime > 0L) {
            deltaM = PaceCalculator.distanceMeters(prevRawLat, prevRawLng, geo.lat, geo.lng)
            deltaTimeMs = nowRealtimeMs - prevRawTime
            if (deltaTimeMs <= 0L) return // 时间倒退，丢弃
            posDiffSpeed = deltaM / (deltaTimeMs / 1000f)
        }

        // 4. 有效速度：多普勒优先（精度可信时），否则用位置差分兜底
        val effectiveSpeed = resolveSpeed(geo, posDiffSpeed)
        if (effectiveSpeed > MAX_SPEED_MPS) return // 离群点（≈43km/h 以上）整帧丢弃
        lastEffectiveSpeedMps = effectiveSpeed
        lastFrameElapsedMs = SystemClock.elapsedRealtime()

        // 5. 平滑（仅影响轨迹写入与展示坐标，不影响距离）
        val (medianLat, medianLng) = medianFilter(geo.lat, geo.lng)
        val (smoothLat, smoothLng) = kalmanFilter.update(medianLat, medianLng, geo.accuracy)

        // 6. 暂停判定已移到 flushStats（1Hz 驱动，不依赖帧到达，静止/无信号也能及时进暂停）。
        //    这里直接用上次判定的 isPaused 控制本帧累距，至多一帧延迟，无影响。

        // 7. 速度平滑器喂样本（暂停期不喂，恢复后窗口自然重建，避免 0 速污染）
        if (!isPaused) speedSmoother.add(effectiveSpeed, nowRealtimeMs)

        // 8. 距离累加：速度门控（有效速度 ≥ MOVING_SPEED_MPS 才算移动）。
        //    取代旧位移启发式——多普勒速度判定"是否在动"远比"位移阈值"准，且 1Hz 步行不再被误杀。
        val isMoving = effectiveSpeed >= MOVING_SPEED_MPS
        if (!isPaused && isMoving && prevRawLat != null && deltaTimeMs > 0L) {
            accumulatedDistanceM += deltaM
        }

        // 9. 更新"上一个原始点 / 平滑点"
        lastRawLat = geo.lat
        lastRawLng = geo.lng
        lastRawRealtimeMs = nowRealtimeMs
        lastSmoothLat = smoothLat
        lastSmoothLng = smoothLng

        // 10. 写入轨迹缓存（坐标用平滑值，speed 存有效速度=多普勒优先，供回放配速复用）
        //     暂停期间仍写轨迹点，便于回放看到"停在原地"的轨迹形态。
        trackBufferDao.insert(
            RunTrackBufferEntity(
                requestId = requestId,
                userId = currentUserId,
                role = role,
                timestamp = wallNowMs,
                lat = smoothLat,
                lng = smoothLng,
                accuracy = geo.accuracy,
                speed = effectiveSpeed,
            )
        )

        // 11. 采样间隔随暂停态切换（活动 1Hz / 暂停 2s）
        applyIntervalForState()

        // 12. 持久化 stats
        flushStats(requestId, wallNowMs)
    }

    /**
     * 有效速度：多普勒优先（精度可信时），否则位置差分兜底。
     *
     * 带健康检测：部分机型 / 模拟定位 hasSpeed()=true 但 speed 恒 0，会把距离/配速冻死、暂停锁死。
     * 当多普勒"恒报 ~0 却位置明显持续移动"累计超 [DOPPLER_HEALTH_FRAMES] 帧，本会话永久切位置差分；
     * 检测期间也返回位置差分，确保移动不被丢失（不会卡在暂停态出不来）。
     */
    private fun resolveSpeed(geo: com.guiderun.app.domain.model.GeoPoint, posDiffSpeed: Float): Float {
        val doppler = geo.speedMps ?: return posDiffSpeed
        val acc = geo.speedAccuracyMps
        if (dopplerUnavailable || (acc != null && acc > SPEED_ACCURACY_THRESHOLD_MPS)) return posDiffSpeed

        // 多普勒报 ~0 但位置在明显移动 → 计一帧；连续累计超阈值则判定多普勒不可用
        if (doppler < DOPPLER_ZERO_EPS_MPS && posDiffSpeed > DOPPLER_HEALTH_SPEED_MPS) {
            dopplerZeroMovingFrames++
            if (dopplerZeroMovingFrames >= DOPPLER_HEALTH_FRAMES) dopplerUnavailable = true
            return posDiffSpeed
        }
        dopplerZeroMovingFrames = 0
        return doppler.coerceAtLeast(0f)
    }

    /**
     * 自动暂停：连续 [PAUSE_DURATION_MS] 速度低于 [PAUSE_SPEED_MPS] → 入暂停；
     * 连续 [RESUME_DURATION_MS] 速度高于 [RESUME_SPEED_MPS] → 退出暂停。
     *
     * 用计时器累加而非速度均值，避免单点抖动让状态来回切换。
     */
    private fun updatePauseState(nowRealtimeMs: Long, speed: Float) {
        if (!isPaused) {
            if (speed in 0f..PAUSE_SPEED_MPS) {
                if (lowSpeedSinceRealtimeMs <= 0L) lowSpeedSinceRealtimeMs = nowRealtimeMs
                if (nowRealtimeMs - lowSpeedSinceRealtimeMs >= PAUSE_DURATION_MS) {
                    isPaused = true
                    // 暂停起点取"当下"而非回溯到低速起点：
                    // 回溯虽然精确（前 10s 静止不入账），但会让入暂停瞬间 timer 倒减 ~10s，
                    // UI 视觉不连贯。代价是每次入暂停多算 ~10s 运动时长（< 长跑总时长的 1%）。
                    pauseStartedAtRealtimeMs = nowRealtimeMs
                    pendingResumeSinceRealtimeMs = -1L
                }
            } else {
                lowSpeedSinceRealtimeMs = -1L
            }
        } else {
            if (speed >= RESUME_SPEED_MPS) {
                if (pendingResumeSinceRealtimeMs <= 0L) pendingResumeSinceRealtimeMs = nowRealtimeMs
                if (nowRealtimeMs - pendingResumeSinceRealtimeMs >= RESUME_DURATION_MS) {
                    isPaused = false
                    if (pauseStartedAtRealtimeMs > 0L) {
                        // 把刚结束的暂停段累入暂停总时长（含 RESUME_DURATION_MS 高速判定窗口）
                        pausedAccumMs += (nowRealtimeMs - pauseStartedAtRealtimeMs).coerceAtLeast(0L)
                        pauseStartedAtRealtimeMs = -1L
                    }
                    lowSpeedSinceRealtimeMs = -1L
                }
            } else {
                pendingResumeSinceRealtimeMs = -1L
            }
        }
    }

    /**
     * 1Hz 兜底 tick：保证暂停降频（2s）或无新帧时 UI 时长/配速仍每秒刷新。
     * 配速由 speedSmoother 当前均值算出（5s 窗口随时间自然衰减），不依赖新 location 帧。
     */
    private fun startStatsTicker(requestId: String) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                flushStats(requestId, System.currentTimeMillis())
            }
        }
    }

    private suspend fun flushStats(requestId: String, wallNowMs: Long) {
        statsMutex.withLock {
            val nowElapsed = SystemClock.elapsedRealtime()
            // 暂停判定在此 1Hz 驱动（而非帧驱动）：无新帧/帧被精度门控丢弃时也能按时间推进。
            // 帧陈旧（> FRAME_STALE_MS）按静止（速度 0）处理 → 静止 / GPS 未锁定也能在 10s 后及时进暂停。
            val frameFresh = lastFrameElapsedMs > 0L && (nowElapsed - lastFrameElapsedMs) <= FRAME_STALE_MS
            val speedForPause = if (frameFresh) lastEffectiveSpeedMps else 0f
            updatePauseState(nowElapsed, speedForPause)
            // 运动时长 = 总墙钟时长 - 已结束的暂停段 - 进行中暂停段
            val ongoingPauseMs = if (isPaused && pauseStartedAtRealtimeMs > 0L) {
                (nowElapsed - pauseStartedAtRealtimeMs).coerceAtLeast(0L)
            } else 0L
            val movingMs = (nowElapsed - startElapsedMs - pausedAccumMs - ongoingPauseMs)
                .coerceAtLeast(0L)
            val durationSec = (movingMs / 1000L).toInt()
            val totalDistM = accumulatedDistanceM.toInt()
            // 瞬时配速：来自多普勒速度的 5s 滑动平均（单层平滑）；暂停时置 null
            val currentPace = if (isPaused) null
                else speedSmoother.currentPaceSecondsPerKm(nowElapsed)
            val avgPace = PaceCalculator.avgPace(totalDistM, durationSec)
            val existing = sessionStatsDao.get(requestId, currentUserId)
            val maxSpeed = maxOf(existing?.maxSpeedMps ?: 0f, lastEffectiveSpeedMps)

            val stats = RunSessionStatsEntity(
                requestId = requestId,
                userId = currentUserId,
                totalDistanceMeters = totalDistM,
                totalDurationSeconds = durationSec,
                currentPaceSeconds = currentPace,
                avgPaceSeconds = avgPace,
                maxSpeedMps = maxSpeed.takeIf { it > 0f },
                isPaused = isPaused,
                lastUpdatedAt = wallNowMs,
            )
            sessionStatsDao.upsert(stats)
        }
    }

    /** 中值滤波：3 点窗口去尖刺。 */
    private fun medianFilter(lat: Double, lng: Double): Pair<Double, Double> {
        medianLatBuffer.addLast(lat)
        medianLngBuffer.addLast(lng)
        if (medianLatBuffer.size > 3) {
            medianLatBuffer.removeFirst()
            medianLngBuffer.removeFirst()
        }
        val sortedLat = medianLatBuffer.sorted()
        val sortedLng = medianLngBuffer.sorted()
        return Pair(sortedLat[sortedLat.size / 2], sortedLng[sortedLng.size / 2])
    }

    /**
     * 采样间隔随暂停态切换：活动中固定 1Hz（消除间隔抖动造成的配速不一致），
     * 自动暂停时降到 2s 省电（暂停期配速冻结，2s 仍能快速识别恢复）。
     */
    private fun applyIntervalForState() {
        val target = if (isPaused) PAUSED_INTERVAL_MS else DEFAULT_INTERVAL_MS
        if (locationIntervalFlow.value != target) {
            locationIntervalFlow.value = target
        }
    }

    private fun startPeriodicUpload(requestId: String) {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            while (true) {
                delay(UPLOAD_INTERVAL_MS)
                uploadPendingPoints(requestId)
            }
        }
    }

    /**
     * 循环上传未发送的轨迹点，直到 buffer 清空或一次失败。
     * 单批 [UPLOAD_BATCH_SIZE] 上限防止请求过大；上一批成功才取下一批，失败即停（下个周期/结束 flush 再试）。
     */
    private suspend fun uploadPendingPoints(requestId: String) {
        // 带上本端实时权威累计值（已扣暂停），服务端按此存储，供回放跨设备/清数据后保持一致
        val stats = sessionStatsDao.get(requestId, currentUserId)
        while (true) {
            val pending = trackBufferDao.getPendingPoints(requestId, UPLOAD_BATCH_SIZE)
            if (pending.isEmpty()) return
            val points = pending.map {
                com.guiderun.app.domain.model.TrackPoint(
                    t = it.timestamp,
                    lat = it.lat,
                    lng = it.lng,
                    acc = it.accuracy,
                    spd = it.speed,
                )
            }
            val ok = runCatching {
                runRequestRepository.uploadTracks(
                    requestId = requestId,
                    role = role,
                    points = points,
                    totalDistanceMeters = stats?.totalDistanceMeters,
                    totalDurationSeconds = stats?.totalDurationSeconds,
                    avgPaceSeconds = stats?.avgPaceSeconds,
                    maxSpeed = stats?.maxSpeedMps,
                )
            }.isSuccess
            if (!ok) return
            trackBufferDao.markUploaded(pending.map { it.id })
            if (pending.size < UPLOAD_BATCH_SIZE) return // 最后一批，传完即止
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        tickerJob?.cancel()
        uploadJob?.cancel()
        // 终态 flush 必须在 scope 被取消后仍能跑完，否则短跑 / 弱网时本次轨迹点全部丢失
        // （旧实现 scope.launch 后立刻 scope.cancel() 会把它当场杀掉 → 服务器空轨迹 → 回放无数据）。
        // 用独立的、不受 scope.cancel 影响的 scope 做最后一次全量上传。
        val rid = currentRequestId
        if (rid.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                uploadPendingPoints(rid)
            }
        }
        kalmanFilter.reset()
        medianLatBuffer.clear()
        medianLngBuffer.clear()
        speedSmoother.reset()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.guiderun.app.R.string.run_tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(requestId: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(com.guiderun.app.R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
        // 点击通知拉回对应 Running 页（按角色），保证视障/志愿者跑步中按 Home 后能回来
        createContentIntent(requestId)?.let { intent ->
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentIntent(pi)
        }
        return builder.build()
    }
}
