package com.guiderun.app.accessibility.voice

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.guiderun.app.ui.blind.BaseBlindActivity

/**
 * 在 Fragment 中注册页面级语音指令处理器。
 *
 * 用法：在 `onViewCreated` 调用一次，绑定到 `viewLifecycleOwner`，
 * 自动在 onResume/onPause 注册和反注册，避免泄漏到下一个 Fragment。
 *
 * 例如：
 * ```kotlin
 * override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *     bindVoiceCommands { cmd ->
 *         when (cmd) {
 *             VoiceCommand.SAVE -> { viewModel.onSavePressed(); true }
 *             VoiceCommand.CANCEL -> { findNavController().popBackStack(); true }
 *             else -> false
 *         }
 *     }
 * }
 * ```
 *
 * 返回值约定：`true` = 已处理，`false` = 让全局 [CommandExecutor] 兜底。
 */
fun Fragment.bindVoiceCommands(handle: (VoiceCommand) -> Boolean) {
    val activity = activity as? BaseBlindActivity ?: return
    val manager = activity.voiceCommandManager
    val handler = VoiceCommandContextHandler { handle(it) }
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            manager.registerContextHandler(handler)
        }

        override fun onPause(owner: LifecycleOwner) {
            manager.unregisterContextHandler(handler)
        }
    })
}
