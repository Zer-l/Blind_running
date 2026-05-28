package com.guiderun.app.accessibility

import android.content.Context
import com.guiderun.app.R
import com.guiderun.app.accessibility.asr.AsrEngine
import com.guiderun.app.accessibility.asr.AsrResult
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * 兼容包装：保留旧的 `SpeechRecognizerManager` 类签名，内部已切换到讯飞 [AsrEngine]，
 * 调用方 (CreateRequestFragment 的批量语音输入按钮 / 其他单字段录入入口) 无需改动。
 *
 * 与原系统 `android.speech.SpeechRecognizer` 版本相比：
 * - [isAvailable] 由讯飞 SDK 状态决定，不再因国产 ROM 缺识别服务而失败
 * - 错误信息已在 [AsrEngine] 实现内 humanize 为中文提示
 * - 流式 partial 结果忽略，仅在最终结果 [AsrResult.Final] 回调 [onResult]
 *
 * 用法不变：构造 → 检查 [isAvailable] → [start] → 完成后 [destroy]。
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (errorMessage: String) -> Unit = {},
    private val onStartListening: () -> Unit = {},
) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AsrEngineEntryPoint {
        fun asrEngine(): AsrEngine
        fun ttsManager(): TtsManager
        fun hapticFeedback(): HapticFeedback
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AsrEngineEntryPoint::class.java,
        )
    }
    private val asrEngine: AsrEngine by lazy { entryPoint.asrEngine() }
    private val ttsManager: TtsManager by lazy { entryPoint.ttsManager() }
    private val hapticFeedback: HapticFeedback by lazy { entryPoint.hapticFeedback() }

    private var listening: Boolean = false

    val isAvailable: Boolean
        get() = asrEngine.isAvailable

    /**
     * 启动 ASR 听写。
     *
     * 调用前会先 [TtsManager.beginAsr] 关闭 TTS 输出，避免扬声器声音被麦克风录入污染识别结果。
     * 监听结束（Final / Error / Idle）后再 [TtsManager.endAsr] 解锁，允许业务方在回调里
     * 立即朗读"已识别 xxx"作为回放——此时 mute 已解除。
     */
    /**
     * @param eosMillis 后端点静音时长。批量录入传 [AsrEngine.BATCH_EOS_MILLIS] 容忍分段停顿，
     *        默认沿用指令听写的 [AsrEngine.DEFAULT_EOS_MILLIS]。
     */
    fun start(eosMillis: Int = AsrEngine.DEFAULT_EOS_MILLIS) {
        if (!isAvailable) {
            onError(context.getString(R.string.voice_input_unavailable))
            return
        }
        if (listening) return
        listening = true
        ttsManager.beginAsr()
        asrEngine.start({ result ->
            when (result) {
                AsrResult.Ready -> {
                    // 录音开始：震动提示用户"可以说话了"
                    hapticFeedback.tick()
                    onStartListening()
                }
                AsrResult.EndOfSpeech -> {
                    // 收音结束（进入识别）：震动提示用户"已收到，正在识别"
                    hapticFeedback.tick()
                }
                is AsrResult.Final -> {
                    // 先解锁 TTS，再回调；调用方可在 onResult 内立即朗读回放
                    ttsManager.endAsr()
                    listening = false
                    if (result.text.isNotBlank()) onResult(result.text)
                }
                is AsrResult.Error -> {
                    Timber.w("SpeechRecognizerManager error: ${result.code}")
                    ttsManager.endAsr()
                    listening = false
                    onError(result.message)
                }
                AsrResult.Idle -> {
                    ttsManager.endAsr()
                    listening = false
                }
                else -> Unit  // partial 当前不暴露
            }
        }, eosMillis)
    }

    fun stop() {
        asrEngine.stop()
        // ASR 引擎可能在 stop 后还会异步回调 Idle 才真正释放，这里立即解锁防止 UI 卡 mute
        if (listening) {
            ttsManager.endAsr()
            listening = false
        }
    }

    fun destroy() {
        asrEngine.cancel()
        if (listening) {
            ttsManager.endAsr()
            listening = false
        }
    }
}
