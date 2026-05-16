package com.guiderun.app.accessibility.voice

import android.content.Context
import com.guiderun.app.R
import com.guiderun.app.accessibility.SosCoordinator
import com.guiderun.app.accessibility.TtsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把已解析的 [VoiceCommand] 翻译成具体动作（导航、SOS、电话、TTS 朗读等）。
 *
 * 与 [VoiceCommandManager] 的分工：
 * - Manager 负责"录音→识别→分发"流程编排；
 * - Executor 负责"指令→动作"映射，对 UI 层 [VoiceCommandHost] 解耦。
 *
 * 注意：上下文相关指令（CONFIRM / CANCEL / PAUSE_RUN / END_RUN）返回 [Result.NeedsContext]，
 * 由 Manager 优先派发给当前页面注册的 [VoiceCommandContextHandler]。
 */
@Singleton
class CommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager,
    private val sosCoordinator: SosCoordinator,
) {
    sealed interface Result {
        /** 已执行 */
        data object Handled : Result
        /** 当前没有视障端 Activity 在前台，无法执行 */
        data object NoActivity : Result
        /** 指令需要页面级处理器；当前页面未注册 → 调用方反馈"当前页面无法执行" */
        data object NeedsContext : Result
    }

    private var hostRef: WeakReference<VoiceCommandHost>? = null

    fun bind(host: VoiceCommandHost) {
        hostRef = WeakReference(host)
    }

    fun unbind(host: VoiceCommandHost) {
        if (hostRef?.get() === host) {
            hostRef = null
        }
    }

    fun execute(command: VoiceCommand): Result {
        val host = hostRef?.get() ?: return Result.NoActivity
        return when (command) {
            VoiceCommand.CREATE_REQUEST -> {
                host.voiceNavigate(VoiceDestination.CREATE_REQUEST)
                Result.Handled
            }
            VoiceCommand.VIEW_HISTORY -> {
                host.voiceNavigate(VoiceDestination.VIEW_HISTORY)
                Result.Handled
            }
            VoiceCommand.PROFILE -> {
                host.voiceNavigate(VoiceDestination.PROFILE)
                Result.Handled
            }
            VoiceCommand.CALL_PEER -> {
                host.voiceCallPeer()
                Result.Handled
            }
            VoiceCommand.SOS -> {
                sosCoordinator.trigger(host.activeRequestId)
                Result.Handled
            }
            VoiceCommand.GO_HOME -> {
                host.voiceNavigateToHome()
                Result.Handled
            }
            VoiceCommand.HELP -> {
                ttsManager.speak(
                    context.getString(R.string.voice_command_tts_help),
                    TtsManager.Priority.HIGH,
                )
                Result.Handled
            }
            VoiceCommand.STATUS -> {
                ttsManager.speak(host.voiceDescribeStatus(), TtsManager.Priority.HIGH)
                Result.Handled
            }
            // 以下指令均需要当前 Fragment 注册的 contextHandler 处理；
            // 若 manager 未派发到 handler 才落到这里，统一返回 NeedsContext 让 Manager 朗读"当前页面无法执行"。
            VoiceCommand.CONFIRM,
            VoiceCommand.CANCEL,
            VoiceCommand.PAUSE_RUN,
            VoiceCommand.END_RUN,
            VoiceCommand.SAVE,
            VoiceCommand.MODIFY_REQUEST,
            VoiceCommand.SKIP,
            VoiceCommand.RETRY,
            VoiceCommand.REFRESH,
            VoiceCommand.DURATION_30,
            VoiceCommand.DURATION_60,
            VoiceCommand.DURATION_90,
            VoiceCommand.DURATION_120,
            VoiceCommand.RATE_1,
            VoiceCommand.RATE_2,
            VoiceCommand.RATE_3,
            VoiceCommand.RATE_4,
            VoiceCommand.RATE_5,
            VoiceCommand.FILTER_FINISHED,
            VoiceCommand.FILTER_CANCELLED,
            VoiceCommand.FILTER_ALL,
            VoiceCommand.OPEN_PROFILE_EDIT,
            VoiceCommand.OPEN_EMERGENCY_CONTACTS,
            VoiceCommand.OPEN_STATS,
            VoiceCommand.OPEN_ACCESSIBILITY,
            VoiceCommand.ADD_CONTACT,
            VoiceCommand.SPEED_FASTER,
            VoiceCommand.SPEED_SLOWER -> Result.NeedsContext
        }
    }
}
