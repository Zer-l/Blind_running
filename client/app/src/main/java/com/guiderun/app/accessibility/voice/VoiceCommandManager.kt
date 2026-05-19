package com.guiderun.app.accessibility.voice

import android.content.Context
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.asr.AsrEngine
import com.guiderun.app.accessibility.asr.AsrResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视障端语音指令系统协调器。
 *
 * 工作流：
 * 1. 触发层（[com.guiderun.app.ui.blind.BaseBlindActivity] 长按音量+）调用 [startListening]
 * 2. 震动 + TTS 朗读"请说指令"；TTS 结束后启动 ASR
 * 3. 收到 [AsrResult.Final] → [CommandParser] 解析
 * 4. 优先派发给注册的 [VoiceCommandContextHandler]（当前 Fragment）；
 *    否则交给 [CommandExecutor] 执行
 * 5. 反馈 TTS：成功执行 / 未识别 / 上下文不支持
 *
 * 状态机：Idle → Listening → Parsing → Idle，禁止并发。
 */
@Singleton
class VoiceCommandManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val asrEngine: AsrEngine,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val executor: CommandExecutor,
) {
    enum class State { Idle, Listening, Parsing }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val parser = CommandParser()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var contextHandler: VoiceCommandContextHandler? = null

    fun registerContextHandler(handler: VoiceCommandContextHandler) {
        contextHandler = handler
    }

    fun unregisterContextHandler(handler: VoiceCommandContextHandler) {
        if (contextHandler === handler) {
            contextHandler = null
        }
    }

    /** 入口：长按音量+触发 / 备注框麦克风按钮等场景。 */
    fun startListening() {
        if (_state.value != State.Idle) {
            Timber.w("voice command already in progress, state=${_state.value}, ignore")
            ttsManager.speak(
                context.getString(R.string.voice_command_tts_busy),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.warning()
            return
        }
        if (!asrEngine.isAvailable) {
            ttsManager.speak(
                context.getString(R.string.voice_command_tts_engine_not_ready),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.warning()
            return
        }
        _state.value = State.Listening
        hapticFeedback.confirm()
        scope.launch {
            // 先等 prompt 朗读完成，避免 prompt 和录音同时进行
            ttsManager.speakAndWait(
                context.getString(R.string.voice_command_tts_prompt),
                TtsManager.Priority.HIGH,
                timeoutMs = 3_000L,
            )
            asrEngine.start(::onAsrResult)
        }
    }

    /** 取消正在进行的识别（音量+长按取消手势 / Activity 退出）。 */
    fun cancel() {
        if (_state.value != State.Idle) {
            asrEngine.cancel()
            _state.value = State.Idle
        }
    }

    private fun onAsrResult(result: AsrResult) {
        when (result) {
            is AsrResult.Final -> {
                _state.value = State.Parsing
                handleFinalText(result.text)
            }
            is AsrResult.Error -> {
                Timber.w("ASR error: ${result.code} ${result.message}")
                ttsManager.speak(result.message, TtsManager.Priority.HIGH)
                hapticFeedback.error()
            }
            AsrResult.Idle -> {
                _state.value = State.Idle
            }
            else -> Unit
        }
    }

    private fun handleFinalText(text: String) {
        if (text.isBlank()) {
            ttsManager.speak(
                context.getString(R.string.voice_command_tts_not_understood),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.warning()
            return
        }
        val command = parser.parse(text)
        if (command == null) {
            Timber.d("unrecognized voice text: '$text'")
            ttsManager.speak(
                context.getString(R.string.voice_command_tts_not_understood),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.warning()
            return
        }
        dispatch(command)
    }

    private fun dispatch(command: VoiceCommand) {
        // 1) 上下文处理器优先
        if (contextHandler?.handle(command) == true) {
            speakExecuting(command)
            return
        }
        // 2) 全局执行器兜底
        when (executor.execute(command)) {
            CommandExecutor.Result.Handled -> speakExecuting(command)
            CommandExecutor.Result.NoActivity -> {
                ttsManager.speak(
                    context.getString(R.string.voice_command_tts_engine_not_ready),
                    TtsManager.Priority.HIGH,
                )
                hapticFeedback.warning()
            }
            CommandExecutor.Result.NeedsContext -> {
                ttsManager.speak(
                    context.getString(
                        R.string.voice_command_tts_unavailable_here,
                        context.getString(command.labelRes),
                    ),
                    TtsManager.Priority.HIGH,
                )
                hapticFeedback.warning()
            }
        }
    }

    private fun speakExecuting(command: VoiceCommand) {
        // STATUS 和 HELP 的反馈本身就是朗读内容，不需要再播"正在为您..."
        if (command == VoiceCommand.STATUS || command == VoiceCommand.HELP) return
        ttsManager.speak(
            context.getString(
                R.string.voice_command_tts_executing,
                context.getString(command.labelRes),
            ),
            TtsManager.Priority.HIGH,
        )
        hapticFeedback.confirm()
    }
}
