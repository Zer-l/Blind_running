package com.guiderun.app.accessibility

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: com.guiderun.app.data.local.UserPreferences,
) {
    sealed interface TtsState {
        data object Initializing : TtsState
        data object Ready : TtsState
        data class Degraded(val reason: String) : TtsState
    }

    /**
     * 4 档优先级 + INTERACTION 锁机制，详见 [speak]。
     *
     * 设计意图：避免「页面入场播报 / 异步状态更新」与「用户主动操作反馈」互相抢占。
     * 长按倒计时正在播"5、4、3..."时，外部系统消息（如"志愿者已到达"）应该入队等待，
     * 而不是把倒计时打断让用户失去手势节奏。
     */
    enum class Priority {
        /** SOS、严重错误。FLUSH 一切（包括 INTERACTION 锁），紧急覆盖 */
        CRITICAL,
        /** 操作反馈：手势 / 语音指令 / 权限弹窗 / 退出登录等用户主动触发的反馈。
         *  默认 FLUSH 当前播报；锁定期间任何 HIGH / NORMAL 入队等待，不打断 */
        INTERACTION,
        /** 业务状态变化、错误反馈、订单状态翻转。
         *  无 INTERACTION 锁时 FLUSH 低优；有锁时入队等待 */
        HIGH,
        /** 页面入场播报、数据更新、定位结果。入队等待 */
        NORMAL,
    }

    private val _state = MutableStateFlow<TtsState>(TtsState.Initializing)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var engine: TextToSpeech? = null
    private val refCount = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var retryCount = 0

    /** 当前 TTS 音量倍率（由 UserPreferences.getBlindTtsVolume 自动更新） */
    @Volatile private var currentVolume: Float = 1.0f

    /**
     * 未完成的 INTERACTION 数量计数器（FLUSH 时清零，每次 INTERACTION speak 自增，
     * 对应 utterance onDone/onError 时自减）。
     * count>0 期间，HIGH / NORMAL 强制入队，保护操作反馈连贯性。
     */
    private val pendingInteractionCount = AtomicInteger(0)

    private data class PendingUtterance(
        val priority: Priority,
        val deferred: CompletableDeferred<Unit>?,
    )

    /** utteranceId → 元信息：用于 onDone 时判断是否需要释放 INTERACTION 锁。 */
    private val pendingUtterances = ConcurrentHashMap<String, PendingUtterance>()

    /**
     * ASR 静音门：true 期间所有 [speak] / [speakAndWait] 直接 no-op。
     *
     * 设计意图：讯飞 IAT 听写时若 TTS 仍在播报，扬声器声音会被麦克风录入污染识别结果。
     * 调用顺序：SpeechRecognizerManager.start() → [beginAsr] → ASR Final/Error/Idle → [endAsr]。
     *
     * 与 INTERACTION 锁正交：mute 不消费 pendingInteractionCount，避免静音期跳过的反馈在
     * endAsr 之后还要补播（被静音的话本来就不需要播）。CRITICAL 也尊重 mute——SOS 与 ASR
     * 都由用户主动触发，不会并行；如真冲突可由调用方先 endAsr 再 speak。
     */
    private val asrMuted = AtomicBoolean(false)

    fun init() {
        retryCount = 0
        engine = TextToSpeech(context) { status -> onEngineInit(status) }
    }

    private fun onEngineInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Timber.w("TTS engine init failed with status=$status, retryCount=$retryCount")
            if (retryCount < MAX_RETRIES) {
                val delayMs = RETRY_BASE_DELAY_MS * (retryCount + 1)
                retryCount++
                scope.launch {
                    delay(delayMs)
                    engine?.shutdown()
                    engine = TextToSpeech(context) { s -> onEngineInit(s) }
                }
            } else {
                _state.value = TtsState.Degraded("语音合成引擎初始化失败")
            }
            return
        }
        retryCount = 0
        val langResult = engine?.setLanguage(Locale.CHINA) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Timber.w("TTS Chinese language not supported: result=$langResult")
            _state.value = TtsState.Degraded("请安装中文语音包")
            return
        }

        // 设置 UtteranceProgressListener 用于播报完成回调 + INTERACTION 锁释放
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                completePending(utteranceId)
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                completePending(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                completePending(utteranceId)
            }
        })

        _state.value = TtsState.Ready
        scope.launch {
            userPreferences.getTtsSpeechRate().collect { rate ->
                engine?.setSpeechRate(rate)
            }
        }
        scope.launch {
            userPreferences.getBlindTtsVolume().collect { volume ->
                currentVolume = volume.coerceIn(0f, 1f)
            }
        }
    }

    fun acquire() {
        refCount.incrementAndGet()
    }

    fun release() {
        if (refCount.decrementAndGet() <= 0) {
            engine?.stop()
            refCount.set(0)
            clearAllPending()
        }
    }

    /**
     * Fire-and-forget 播报。优先级行为：
     * - CRITICAL：FLUSH 一切 + 清 INTERACTION 锁
     * - INTERACTION 默认 flush=true：FLUSH 当前 + count++（外部 HIGH/NORMAL 在 count>0 时入队等待）
     * - INTERACTION flush=false：QUEUE_ADD + count++（用于倒计时数字这类"接力"反馈）
     * - HIGH：count>0 时 QUEUE_ADD（不打断 INTERACTION）；count=0 时 QUEUE_FLUSH（打断 NORMAL）
     * - NORMAL：QUEUE_ADD
     */
    fun speak(text: String, priority: Priority = Priority.NORMAL, flush: Boolean = defaultFlush(priority)) {
        if (asrMuted.get()) return
        enqueueSpeak(text, priority, flush, deferred = null)
    }

    /**
     * 播报并等待完成。用于倒计时等需要精确时序的场景。
     * @param timeoutMs 最大等待时长（毫秒），超时自动返回，防止永久挂起
     */
    suspend fun speakAndWait(
        text: String,
        priority: Priority = Priority.NORMAL,
        flush: Boolean = defaultFlush(priority),
        timeoutMs: Long = 10_000L,
    ) {
        if (_state.value !is TtsState.Ready) return
        if (asrMuted.get()) return
        val deferred = CompletableDeferred<Unit>()
        val utteranceId = enqueueSpeak(text, priority, flush, deferred) ?: return

        // 等待 onDone 回调，带超时保护
        try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: run {
                // 超时清理
                pendingUtterances.remove(utteranceId)?.let { entry ->
                    if (entry.priority == Priority.INTERACTION || entry.priority == Priority.CRITICAL) {
                        pendingInteractionCount.decrementAndGet().coerceAtLeast(0)
                    }
                }
                Timber.w("TTS speakAndWait timed out for text='$text'")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 结构化并发：必须向上传播取消信号
            pendingUtterances.remove(utteranceId)
            throw e
        } catch (e: Exception) {
            // 异常清理
            pendingUtterances.remove(utteranceId)?.let { entry ->
                if (entry.priority == Priority.INTERACTION || entry.priority == Priority.CRITICAL) {
                    pendingInteractionCount.decrementAndGet().coerceAtLeast(0)
                }
            }
            Timber.w(e, "TTS speakAndWait failed for text='$text'")
        }
    }

    /**
     * 统一发包入口：根据 priority + flush 决定 mode 与锁状态，注册 pending 用于 onDone 回调。
     * @return utteranceId（成功），null（state 未就绪 / 引擎错误）
     */
    private fun enqueueSpeak(
        text: String,
        priority: Priority,
        flush: Boolean,
        deferred: CompletableDeferred<Unit>?,
    ): String? {
        if (_state.value !is TtsState.Ready) return null

        val mode = resolveMode(priority, flush)
        if (mode == TextToSpeech.QUEUE_FLUSH) {
            // FLUSH 会清空当前队列；引擎 onDone 会回调被清掉的 utterance，但保险起见也手动结算一遍
            settleAllOnFlush(priority)
        }

        val utteranceId = generateUtteranceId(text)
        pendingUtterances[utteranceId] = PendingUtterance(priority, deferred)
        if (priority == Priority.INTERACTION || priority == Priority.CRITICAL) {
            pendingInteractionCount.incrementAndGet()
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume)
        }
        val result = engine?.speak(text, mode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            pendingUtterances.remove(utteranceId)
            if (priority == Priority.INTERACTION || priority == Priority.CRITICAL) {
                pendingInteractionCount.decrementAndGet().coerceAtLeast(0)
            }
            deferred?.complete(Unit)
            Timber.w("TTS speak failed for text='$text' priority=$priority, scheduling reconnect")
            reconnect()
            return null
        }
        return utteranceId
    }

    private fun resolveMode(priority: Priority, flush: Boolean): Int = when (priority) {
        Priority.CRITICAL -> TextToSpeech.QUEUE_FLUSH
        Priority.INTERACTION -> if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        Priority.HIGH ->
            if (pendingInteractionCount.get() > 0) TextToSpeech.QUEUE_ADD
            else if (flush) TextToSpeech.QUEUE_FLUSH
            else TextToSpeech.QUEUE_ADD
        Priority.NORMAL -> TextToSpeech.QUEUE_ADD
    }

    private fun defaultFlush(priority: Priority): Boolean = when (priority) {
        Priority.CRITICAL, Priority.INTERACTION -> true
        Priority.HIGH -> true  // HIGH 在锁外才真的 FLUSH，锁内被降级为 QUEUE_ADD
        Priority.NORMAL -> false
    }

    /**
     * FLUSH 模式下提前结算所有 pending：清空 deferred + 同步锁计数。
     * 必要：引擎对被 FLUSH 的 utterance 不保证回调时机，这里防止 INTERACTION 锁泄漏。
     */
    private fun settleAllOnFlush(incomingPriority: Priority) {
        if (incomingPriority == Priority.CRITICAL) {
            // CRITICAL 清空一切锁，包括 INTERACTION
            pendingInteractionCount.set(0)
        }
        // 仅清空被 FLUSH 的 entries（FLUSH 后引擎也可能回调，但 remove 是幂等的）
        val snapshot = pendingUtterances.toMap()
        snapshot.forEach { (id, entry) ->
            // 不在这里强制移除——保留给 onDone/onError 自然回调路径，避免 race
            // 仅做"对 deferred 提前 complete"以释放可能的 await 者
            // 实际锁计数交由 completePending 处理
            entry.deferred?.takeIf { !it.isCompleted }?.complete(Unit)
            // 移除引用以避免 deferred 二次 complete
            if (entry.deferred != null) {
                pendingUtterances[id] = entry.copy(deferred = null)
            }
        }
    }

    private fun completePending(utteranceId: String?) {
        val id = utteranceId ?: return
        val entry = pendingUtterances.remove(id) ?: return
        if (entry.priority == Priority.INTERACTION || entry.priority == Priority.CRITICAL) {
            pendingInteractionCount.decrementAndGet().coerceAtLeast(0)
        }
        entry.deferred?.takeIf { !it.isCompleted }?.complete(Unit)
    }

    private fun clearAllPending() {
        pendingUtterances.forEach { (_, entry) ->
            entry.deferred?.takeIf { !it.isCompleted }?.complete(Unit)
        }
        pendingUtterances.clear()
        pendingInteractionCount.set(0)
    }

    fun stop() {
        engine?.stop()
        clearAllPending()
    }

    /**
     * 进入 ASR 静音态：清空当前 TTS 队列 + 关闭后续播报开关。
     *
     * 由 [SpeechRecognizerManager.start] 在真正调起讯飞 IAT 之前调用，避免：
     * ① 仍在播报的"页面入场提示"被麦克风录入污染识别结果
     * ② 异步触发的 NORMAL/HIGH 播报（如倒计时、状态更新）在 ASR 期间继续抢音频焦点
     *
     * 幂等：重复调用安全。与 INTERACTION 锁正交（不动 pendingInteractionCount）。
     */
    fun beginAsr() {
        asrMuted.set(true)
        engine?.stop()
        clearAllPending()
    }

    /** 退出 ASR 静音态。仅恢复 mute gate，不补放被吞掉的播报。 */
    fun endAsr() {
        asrMuted.set(false)
    }

    /** 手动触发重新初始化 TTS 引擎。 */
    fun reconnect() {
        if (_state.value is TtsState.Initializing) return
        retryCount = 0
        clearAllPending()
        engine?.shutdown()
        engine = null
        _state.value = TtsState.Initializing
        engine = TextToSpeech(context) { status -> onEngineInit(status) }
    }

    private fun generateUtteranceId(text: String): String {
        // 使用时间戳 + hashCode 确保唯一性（相同文本连续播报不冲突）
        return "tts_${System.nanoTime()}_${text.hashCode()}"
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1_000L
    }
}
