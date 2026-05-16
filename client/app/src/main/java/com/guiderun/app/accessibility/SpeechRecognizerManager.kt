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
 * 调用方 (CreateRequestFragment / EditRequestFragment 的备注框麦克风按钮) 无需改动。
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
    }

    private val asrEngine: AsrEngine by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AsrEngineEntryPoint::class.java,
        ).asrEngine()
    }

    private var listening: Boolean = false

    val isAvailable: Boolean
        get() = asrEngine.isAvailable

    fun start() {
        if (!isAvailable) {
            onError(context.getString(R.string.voice_input_unavailable))
            return
        }
        if (listening) return
        listening = true
        asrEngine.start { result ->
            when (result) {
                AsrResult.Ready -> onStartListening()
                is AsrResult.Final -> {
                    if (result.text.isNotBlank()) onResult(result.text)
                }
                is AsrResult.Error -> {
                    Timber.w("SpeechRecognizerManager error: ${result.code}")
                    onError(result.message)
                }
                AsrResult.Idle -> {
                    listening = false
                }
                else -> Unit  // partial / endOfSpeech 当前不暴露
            }
        }
    }

    fun stop() {
        asrEngine.stop()
    }

    fun destroy() {
        asrEngine.cancel()
        listening = false
    }
}
