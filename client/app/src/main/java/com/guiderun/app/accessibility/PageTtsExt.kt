package com.guiderun.app.accessibility

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 视障端页面入页 TTS 统一播报工具。
 *
 * 关键约束（来自 TTS engine 行为）：
 * - HIGH / INTERACTION 默认 `flush=true`（QUEUE_FLUSH），背靠背两次同优先级 [TtsManager.speak]
 *   时第二次会立即打断第一次，结果只能听到 hint 而听不到页面名。
 * - 唯一可靠的串行模式：第一句用 [TtsManager.speakAndWait] 阻塞协程直到 onDone，
 *   再让第二句入队，由 onDone 回调后续触发，第二句才能真正接在第一句之后播完。
 *
 * 所有视障端非 Compose 页面统一通过本扩展在 onResume 触发入页播报，避免每个 Fragment
 * 自己 launch + 手写 speakAndWait 时序漂移（已观察到背靠背 INTERACTION speak 导致页面名被吞）。
 *
 * @param ttsManager 调用方注入的 TtsManager 单例
 * @param pageRes    页面名（如 `tts_page_settings`，对应"个人设置页面"）
 * @param hintRes    操作描述（如 `tts_hint_settings`，对应"可编辑资料、查看紧急联系人..."）
 */
fun Fragment.speakPageEntry(
    ttsManager: TtsManager,
    @StringRes pageRes: Int,
    @StringRes hintRes: Int,
) {
    if (view == null) return
    // 协程绑定 viewLifecycleOwner：onDestroyView 时自动取消，避免 view 销毁后还在播
    viewLifecycleOwner.lifecycleScope.launch {
        ttsManager.speakAndWait(getString(pageRes), TtsManager.Priority.HIGH)
        ttsManager.speak(getString(hintRes), TtsManager.Priority.HIGH)
    }
}
