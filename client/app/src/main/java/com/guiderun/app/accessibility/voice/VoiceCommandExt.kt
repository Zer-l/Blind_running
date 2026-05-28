package com.guiderun.app.accessibility.voice

import androidx.annotation.StringRes
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
 *
 * @param promptRes 自定义语音提示语（如发起页用批量录入格式提示），null 用默认"请说指令"。
 * @param rawTextInterceptor 原始识别文本拦截器：在指令解析前抢先消费（如批量录入回填），
 *        返回 true 表示已消费，不再走指令解析；返回 false 回落到 [handle]/全局执行。
 */
fun Fragment.bindVoiceCommands(
    @StringRes promptRes: Int? = null,
    rawTextInterceptor: ((String) -> Boolean)? = null,
    handle: (VoiceCommand) -> Boolean,
) {
    val activity = activity as? BaseBlindActivity ?: return
    val manager = activity.voiceCommandManager
    val handler = VoiceCommandContextHandler { handle(it) }
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            manager.registerContextHandler(handler, promptRes, rawTextInterceptor)
        }

        override fun onPause(owner: LifecycleOwner) {
            manager.unregisterContextHandler(handler)
        }
    })
}
