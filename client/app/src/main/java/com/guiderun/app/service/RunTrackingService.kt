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
import kotlinx.coroutines.runBlocking
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.local.entity.RunTrackBufferEntity
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.PaceCalculator
import com.guiderun.app.util.PaceWindow
import com.guiderun.app.util.SimpleKalmanFilter
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
 * 4. **配速窗口**：使用 [PaceWindow]，"最近 30s 或 100m" 双约束，与采样间隔解耦。
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
    private var lastSmoothSpeedMps: Float = 0f

    private val paceWindow = PaceWindow()
    private val statsMutex = Mutex()

    // 动态采样间隔
    private val locationIntervalFlow = MutableStateFlow(DEFAULT_INTERVAL_MS)
    private var isStationary = false
    private var stationarySinceMs: Long = -1L

    // 自动暂停（独立于 isStationary）
    // - isStationary：用于把采集间隔拉到 30s 省电（30s 触发，体感慢）
    // - isPaused：用于"距离/配速/运动时长 冻结"（10s 触发，体感快）
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
        const val DEFAULT_INTERVAL_MS = 3_000L

        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "run_tracking"
        private const val UPLOAD_BATCH_SIZE = 100
        private const val UPLOAD_INTERVAL_MS = 30_000L
        private const val TICK_INTERVAL_MS = 1_000L

        // 精度门控
        private const val ACCURACY_THRESHOLD_M = 30f

        // 离群点阈值（按瞬时速度判断，比固定距离阈值更准）
        private const val MAX_SPEED_MPS = 12f         // ≈ 43 km/h
        // 位移 < 5m 视为静止漂移：实测视障端原地不动时 GPS 单帧漂移常见 3-6m，原来 2m 太低
        private const val MIN_DELTA_M = 5f
        // isMicroMove 的 dt 下限固定 5s；上限改为跟随采样间隔（见 handleLocation），
        // 避免 30s 静止省电模式下 deltaTimeMs ≈ 30s 跌出窗口、微漂移直接入账
        private const val MIN_DELTA_TIME_MS = 5_000L
        // 相对精度门控：位移必须显著大于定位误差，否则一定是噪声
        // accuracy * 0.5 经验值：accuracy=20m 的点要求位移 > 10m 才算真位移
        private const val NOISE_ACCURACY_RATIO = 0.5f

        // 静止判定（用于采集间隔降频）
        private const val STATIONARY_SPEED_MPS = 0.5f
        private const val STATIONARY_DURATION_MS = 30_000L
        private const val STATIONARY_INTERVAL_MS = 30_000L

        // 自动暂停判定（用于距离/配速冻结）
        // 0.7 m/s ≈ 2.5 km/h：原 0.5 太接近 GPS 噪声速度（6m/5s=1.2m/s），导致原地噪声反复踢出暂停
        private const val PAUSE_SPEED_MPS = 0.7f
        private const val PAUSE_DURATION_MS = 10_000L // 持续 10s 才入暂停
        private const val RESUME_SPEED_MPS = 1.0f     // 高于该速度即恢复
        // 3s 高速持续才退出暂停：原来 0 单帧噪声就能恢复，造成静止时距离仍在涨
        private const val RESUME_DURATION_MS = 3_000L

        // 速度→采集间隔自适应
        private const val SPEED_WALK = 2.0f
        private const val SPEED_JOG = 4.0f
        private const val INTERVAL_WALK_MS = 5_000L
        private const val INTERVAL_JOG_MS = 3_000L
        private const val INTERVAL_RUN_MS = 2_000L
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_REDELIVER_INTENT 通常会重传原 intent，但极端情况（进程未启动 + 系统拉起）可能丢失。
        // 此时降级读 ACTIVE_REQUEST_ID，确保前台服务仍能恢复采集而不是 stopSelf。
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
            ?: runBlocking { userPreferences.getActiveRequestId() }
            ?: run { stopSelf(); return START_NOT_STICKY }

        // 幂等：同一 requestId + 已在跑的采集 Job 直接复用。
        // ViewModel 重建会调 startForegroundService，若不拦截会重新触发 restoreFromPersistedStats，
        // 把 startElapsedMs 前移而暂停状态字段仍残留旧绝对时间戳 → movingMs 变负 coerce 成 0（见 Bug A）。
        if (currentRequestId == requestId && trackingJob?.isActive == true) {
            startForeground(NOTIFICATION_ID, buildNotification(requestId))
            return START_REDELIVER_INTENT
        }

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
        return START_REDELIVER_INTENT
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

        // 3. 离群速度过滤（用原始坐标计算瞬时速度）
        val prevRawLat = lastRawLat
        val prevRawLng = lastRawLng
        val prevRawTime = lastRawRealtimeMs

        var deltaM = 0f
        var deltaTimeMs = 0L
        if (prevRawLat != null && prevRawLng != null && prevRawTime > 0L) {
            deltaM = PaceCalculator.distanceMeters(prevRawLat, prevRawLng, geo.lat, geo.lng)
            deltaTimeMs = nowRealtimeMs - prevRawTime
            if (deltaTimeMs <= 0L) return // 时间倒退，丢弃
            val speed = if (deltaTimeMs > 0L) deltaM / (deltaTimeMs / 1000f) else 0f
            if (speed > MAX_SPEED_MPS) return // 离群点
            lastSmoothSpeedMps = speed
        }

        // 4. 平滑（仅影响轨迹写入与展示坐标，不影响距离）
        val (medianLat, medianLng) = medianFilter(geo.lat, geo.lng)
        val (smoothLat, smoothLng) = kalmanFilter.update(medianLat, medianLng, geo.accuracy)

        // 5. 自动暂停状态机（必须在距离累加之前判定，使本帧位移直接被冻结）
        updatePauseState(nowRealtimeMs)

        // 6. 位移门控 + 暂停冻结：屏蔽静止漂移对距离的污染；暂停期间距离/配速完全不动
        //   - isMicroMove：位移 < 5m 且 dt 在合理采样窗口内 → 微漂移（dt 上限跟随当前采样间隔的 1.5 倍，
        //     兼容 30s 静止模式，否则 30s 间隔下漂移会跌出窗口被错误计入距离）
        //   - isNoiseMove：位移小于定位误差的 0.5 倍 → 一定是噪声（accuracy=20m 时要求位移 > 10m）
        val maxMicroDt = (locationIntervalFlow.value * 3 / 2).coerceAtLeast(MIN_DELTA_TIME_MS)
        val isMicroMove = deltaM < MIN_DELTA_M && deltaTimeMs in 1..maxMicroDt
        val isNoiseMove = deltaM < geo.accuracy * NOISE_ACCURACY_RATIO
        if (!isPaused && prevRawLat != null && prevRawLng != null && !isMicroMove && !isNoiseMove) {
            accumulatedDistanceM += deltaM
            paceWindow.addSegment(deltaM, deltaTimeMs, nowRealtimeMs)
        }

        // 7. 更新"上一个原始点 / 平滑点"
        lastRawLat = geo.lat
        lastRawLng = geo.lng
        lastRawRealtimeMs = nowRealtimeMs
        lastSmoothLat = smoothLat
        lastSmoothLng = smoothLng

        // 8. 写入轨迹缓存（用平滑后的坐标，但保留原始 accuracy）
        //    暂停期间仍写轨迹点，便于回放看到"停在原地"的轨迹形态。
        trackBufferDao.insert(
            RunTrackBufferEntity(
                requestId = requestId,
                userId = currentUserId,
                role = role,
                timestamp = wallNowMs,
                lat = smoothLat,
                lng = smoothLng,
                accuracy = geo.accuracy,
                speed = lastSmoothSpeedMps,
            )
        )

        // 9. 静止判定 & 自适应间隔
        checkAndUpdateStationary(nowRealtimeMs)
        adjustIntervalBySpeed()

        // 10. 持久化 stats
        flushStats(requestId, wallNowMs)
    }

    /**
     * 自动暂停：连续 [PAUSE_DURATION_MS] 速度低于 [PAUSE_SPEED_MPS] → 入暂停；
     * 连续 [RESUME_DURATION_MS] 速度高于 [RESUME_SPEED_MPS] → 退出暂停。
     *
     * 用计时器累加而非速度均值，避免单点抖动让状态来回切换。
     */
    private fun updatePauseState(nowRealtimeMs: Long) {
        val speed = lastSmoothSpeedMps
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
     * 1Hz 兜底 tick：当采样间隔被拉到 30s（静止）时，仍保证 UI 时长 1 秒一跳。
     * 该 tick 只更新时长字段，不动距离/配速 —— 距离/配速由 location 帧驱动。
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
            // 运动时长 = 总墙钟时长 - 已结束的暂停段 - 进行中暂停段
            val ongoingPauseMs = if (isPaused && pauseStartedAtRealtimeMs > 0L) {
                (nowElapsed - pauseStartedAtRealtimeMs).coerceAtLeast(0L)
            } else 0L
            val movingMs = (nowElapsed - startElapsedMs - pausedAccumMs - ongoingPauseMs)
                .coerceAtLeast(0L)
            val durationSec = (movingMs / 1000L).toInt()
            val totalDistM = accumulatedDistanceM.toInt()
            // 暂停时立即把瞬时配速置 null，不再依赖 PaceWindow evict 自然衰减
            val currentPace = if (isPaused) null
                else paceWindow.currentPaceSecondsPerKm(nowElapsed)
            val avgPace = PaceCalculator.avgPace(totalDistM, durationSec)
            val existing = sessionStatsDao.get(requestId, currentUserId)
            val maxSpeed = maxOf(existing?.maxSpeedMps ?: 0f, lastSmoothSpeedMps)

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

    private fun checkAndUpdateStationary(nowRealtimeMs: Long) {
        val nowStationary = lastSmoothSpeedMps in 0f..STATIONARY_SPEED_MPS
        if (nowStationary && !isStationary) {
            if (stationarySinceMs <= 0L) stationarySinceMs = nowRealtimeMs
            if (nowRealtimeMs - stationarySinceMs >= STATIONARY_DURATION_MS) {
                isStationary = true
                locationIntervalFlow.value = STATIONARY_INTERVAL_MS
            }
        } else if (!nowStationary) {
            stationarySinceMs = -1L
            if (isStationary) {
                isStationary = false
                adjustIntervalBySpeed()
            }
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

    private fun adjustIntervalBySpeed() {
        // 暂停期间：强制保持高频采样，便于尽早识别"用户又跑起来"。
        // 不让 isStationary 把间隔拉到 30s —— 否则恢复要等一个 30s 周期，体感卡死。
        if (isPaused) {
            if (locationIntervalFlow.value != INTERVAL_RUN_MS) {
                locationIntervalFlow.value = INTERVAL_RUN_MS
            }
            return
        }
        if (isStationary) return
        val target = when {
            lastSmoothSpeedMps < SPEED_WALK -> INTERVAL_WALK_MS
            lastSmoothSpeedMps < SPEED_JOG -> INTERVAL_JOG_MS
            else -> INTERVAL_RUN_MS
        }
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

    private suspend fun uploadPendingPoints(requestId: String) {
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
        runCatching { runRequestRepository.uploadTracks(requestId, role, points) }
            .onSuccess { trackBufferDao.markUploaded(pending.map { it.id }) }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        tickerJob?.cancel()
        uploadJob?.cancel()
        scope.launch {
            if (currentRequestId.isNotEmpty()) uploadPendingPoints(currentRequestId)
        }
        kalmanFilter.reset()
        medianLatBuffer.clear()
        medianLngBuffer.clear()
        paceWindow.reset()
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
