package com.guiderun.app.accessibility.voice

/**
 * 页面级语音指令处理器。
 *
 * 一些指令（如 [VoiceCommand.CONFIRM] / [VoiceCommand.CANCEL] / [VoiceCommand.PAUSE_RUN] / [VoiceCommand.END_RUN]）
 * 的具体行为依赖当前所在的页面（确认什么？取消什么？暂停哪个 Run？）。
 *
 * 约定：
 * - Fragment 在 `onResume` 调用 [VoiceCommandManager.registerContextHandler] 注册，`onPause` 反注册。
 * - [handle] 返回 true 表示已被本页面处理，[VoiceCommandManager] 将不再走全局执行。
 */
fun interface VoiceCommandContextHandler {
    /** @return true 表示已处理；false 让全局 Executor 兜底处理。 */
    fun handle(command: VoiceCommand): Boolean
}
