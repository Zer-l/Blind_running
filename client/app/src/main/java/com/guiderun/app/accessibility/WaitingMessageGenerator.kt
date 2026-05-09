package com.guiderun.app.accessibility

/**
 * Generates waiting messages following a 3-tier emotional curve:
 *  - Tier 1 (0–2 min):  positive and encouraging
 *  - Tier 2 (3–9 min):  empathetic and patient
 *  - Tier 3 (10+ min):  apologetic but persistent
 */
object WaitingMessageGenerator {

    private val tier1 = listOf(
        "志愿者正在赶来，请稍等片刻",
        "正在为您匹配附近的志愿者",
        "即将为您找到合适的跑步伙伴",
        "志愿者们都很乐意陪您奔跑",
        "好消息快来了，请稍候",
    )

    private val tier2 = listOf(
        "感谢您的耐心等待，正在积极匹配中",
        "已经在努力为您寻找志愿者，请再等等",
        "请再稍候，一定会有志愿者来的",
        "感谢您的信任，我们不会让您久等",
        "距离心仪的志愿者又近了一步",
    )

    private val tier3 = listOf(
        "非常抱歉让您久等，我们绝不放弃",
        "感谢您的坚持，志愿者正在路上",
        "对不起，让您等了这么久，继续为您匹配",
        "您的耐心令我们感动，马上就能找到",
        "我们已经扩大了匹配范围，请再给我们一点时间",
    )

    fun getMessage(waitingSeconds: Long): String {
        val minutes = waitingSeconds / 60
        val pool = when {
            minutes < 3  -> tier1
            minutes < 10 -> tier2
            else         -> tier3
        }
        return pool.random()
    }
}
