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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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

    enum class Priority { NORMAL, HIGH }

    private val _state = MutableStateFlow<TtsState>(TtsState.Initializing)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var engine: TextToSpeech? = null
    private val refCount = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var retryCount = 0

    // ★ 播报完成回调机制：utteranceId → CompletableDeferred
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

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

        // ★ 设置 UtteranceProgressListener 用于播报完成回调
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { id ->
                    pendingUtterances.remove(id)?.complete(Unit)
                }
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { id ->
                    pendingUtterances.remove(id)?.complete(Unit) // 出错也算完成，避免永久挂起
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { id ->
                    pendingUtterances.remove(id)?.complete(Unit)
                }
            }
        })

        _state.value = TtsState.Ready
        scope.launch {
            userPreferences.getTtsSpeechRate().collect { rate ->
                engine?.setSpeechRate(rate)
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
            // 释放时清理所有挂起的回调
            pendingUtterances.forEach { (_, deferred) -> deferred.complete(Unit) }
            pendingUtterances.clear()
        }
    }

    /**
     * Fire-and-forget 播报。
     * HIGH 优先级会打断当前播报，NORMAL 排队。
     */
    fun speak(text: String, priority: Priority = Priority.NORMAL) {
        if (_state.value !is TtsState.Ready) return
        val utteranceId = generateUtteranceId(text)
        val mode = if (priority == Priority.HIGH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine?.speak(text, mode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Timber.w("TTS speak failed for text='$text', scheduling reconnect")
            reconnect()
        }
    }

    /**
     * ★ 播报并等待完成。用于倒计时等需要精确时序的场景。
     * @param timeoutMs 最大等待时长（毫秒），超时自动返回，防止永久挂起
     */
    suspend fun speakAndWait(
        text: String,
        priority: Priority = Priority.NORMAL,
        timeoutMs: Long = 10_000L,
    ) {
        if (_state.value !is TtsState.Ready) return

        val utteranceId = generateUtteranceId(text)
        val deferred = CompletableDeferred<Unit>()
        pendingUtterances[utteranceId] = deferred

        val mode = if (priority == Priority.HIGH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine?.speak(text, mode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            pendingUtterances.remove(utteranceId)
            Timber.w("TTS speakAndWait failed for text='$text', scheduling reconnect")
            reconnect()
            return
        }

        // 等待 onDone 回调，带超时保护
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                // 监听 deferred 完成
                scope.launch {
                    deferred.await()
                    if (cont.isActive) cont.resume(Unit)
                }
                // 协程取消时清理
                cont.invokeOnCancellation {
                    pendingUtterances.remove(utteranceId)
                }
            }
        } ?: run {
            // 超时：清理并继续
            pendingUtterances.remove(utteranceId)
            Timber.w("TTS speakAndWait timed out for text='$text'")
        }
    }

    fun stop() {
        engine?.stop()
        // 停止时清理所有挂起的回调
        pendingUtterances.forEach { (_, deferred) -> deferred.complete(Unit) }
        pendingUtterances.clear()
    }

    /** 手动触发重新初始化 TTS 引擎。 */
    fun reconnect() {
        if (_state.value is TtsState.Initializing) return
        retryCount = 0
        // 清理挂起的回调
        pendingUtterances.forEach { (_, deferred) -> deferred.complete(Unit) }
        pendingUtterances.clear()
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
