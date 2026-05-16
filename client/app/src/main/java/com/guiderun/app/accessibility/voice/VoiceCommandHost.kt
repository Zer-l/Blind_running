package com.guiderun.app.accessibility.voice

/**
 * 视障端语音指令系统的导航目的地抽象。
 *
 * 用枚举而不是 Fragment id，使得跨 Activity 复用：
 * - 在 [com.guiderun.app.ui.blind.BlindActivity] 内 → 翻译为 NavController.navigate(@IdRes)
 * - 在 [com.guiderun.app.MainActivity]（HomeScreen）→ 翻译为 BlindActivity.start(...)
 */
enum class VoiceDestination {
    CREATE_REQUEST,
    VIEW_HISTORY,
    PROFILE,
}

/**
 * Activity 暴露给语音指令执行器的能力接口。
 *
 * 由 BlindActivity / MainActivity 实现，在 `onResume` 调用
 * [com.guiderun.app.accessibility.voice.CommandExecutor.bind] 注册，`onPause` 反注册。
 *
 * 设计目的：让 accessibility 包不反向依赖具体 Activity；CommandExecutor 通过该接口操作 Activity。
 */
interface VoiceCommandHost {
    /** 当前页面绑定的活跃订单 ID（用于 SOS/状态朗读等需要订单上下文的指令）。 */
    val activeRequestId: String?

    /**
     * 跳转到抽象目的地。
     * @return true 表示已发起导航；false 表示已在该目的地或无法跳转。
     */
    fun voiceNavigate(destination: VoiceDestination): Boolean

    /** 拨打对方电话（视障端：志愿者；志愿者端：跑者）。无可拨号对象时朗读提示。 */
    fun voiceCallPeer()

    /** 清空 Fragment 栈，回到主页面。 */
    fun voiceNavigateToHome()

    /** 朗读用文本：当前页面 + 订单状态简要说明。供 [VoiceCommand.STATUS] 调用。 */
    fun voiceDescribeStatus(): String
}
