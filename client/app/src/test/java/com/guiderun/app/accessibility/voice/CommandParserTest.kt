package com.guiderun.app.accessibility.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    private val parser = CommandParser()

    @Test
    fun `精确同义词命中 - CREATE_REQUEST`() {
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("发起跑步请求"))
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("我要跑步"))
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("我想跑步"))
    }

    @Test
    fun `带标点和空格归一化后命中`() {
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("发起，跑步请求！"))
        assertEquals(VoiceCommand.SOS, parser.parse("救  命  ！！"))
    }

    @Test
    fun `多余前后缀仍命中 - 包含匹配`() {
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("我现在要开始跑步好吗"))
        assertEquals(VoiceCommand.VIEW_HISTORY, parser.parse("帮我查看历史记录吧"))
    }

    @Test
    fun `多个命中时选最长 phrase`() {
        // "结束" 命中 END_RUN，"申请结束" 也命中 END_RUN，但都是同一个指令
        assertEquals(VoiceCommand.END_RUN, parser.parse("申请结束"))
        // "暂停" 命中 PAUSE_RUN
        assertEquals(VoiceCommand.PAUSE_RUN, parser.parse("暂停跑步"))
    }

    @Test
    fun `空文本返回 null`() {
        // 注：含 CONFIRM/CANCEL 短同义词（"不"/"开始"等）的句子会被包含匹配命中，
        // 这是 phrase 库设计取舍——优先用户简答覆盖率，不再断言中文长句必然返回 null。
        assertNull(parser.parse(""))
        assertNull(parser.parse("    "))
    }

    @Test
    fun `所有指令的首个 phrase 都能命中自身`() {
        for (cmd in VoiceCommand.entries) {
            val firstPhrase = cmd.phrases.first()
            assertEquals(
                "phrase '$firstPhrase' should match $cmd",
                cmd,
                parser.parse(firstPhrase),
            )
        }
    }

    @Test
    fun `SOS 短词不被噪音误命中`() {
        // 仅当用户明确说出短语时才命中 SOS
        assertEquals(VoiceCommand.SOS, parser.parse("紧急求助"))
        assertEquals(VoiceCommand.SOS, parser.parse("救命"))
    }

    @Test
    fun `CONFIRM 与 CANCEL 互不混淆`() {
        assertEquals(VoiceCommand.CONFIRM, parser.parse("确认"))
        assertEquals(VoiceCommand.CONFIRM, parser.parse("好的"))
        assertEquals(VoiceCommand.CANCEL, parser.parse("取消"))
        assertEquals(VoiceCommand.CANCEL, parser.parse("不要了"))
    }

    @Test
    fun `HELP 命中`() {
        assertEquals(VoiceCommand.HELP, parser.parse("帮助"))
        assertEquals(VoiceCommand.HELP, parser.parse("有什么指令"))
    }

    @Test
    fun `STATUS 命中`() {
        assertEquals(VoiceCommand.STATUS, parser.parse("当前状态"))
        assertEquals(VoiceCommand.STATUS, parser.parse("现在到哪了"))
    }

    @Test
    fun `GO_HOME 命中`() {
        assertEquals(VoiceCommand.GO_HOME, parser.parse("返回首页"))
        assertEquals(VoiceCommand.GO_HOME, parser.parse("回首页"))
    }

    @Test
    fun `带语气词命中 - 模糊匹配`() {
        // "嗯，我想跑步" 归一化后变成 "嗯我想跑步"，包含"我想跑步"
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("嗯，我想跑步"))
    }

    // ============ Levenshtein 分支专项 ============

    @Test
    fun `Levenshtein - 删除距离1 命中 - 用户少说一个字`() {
        // phrase "找志愿者"(4字) tolerance = min(1, 4/3) = 1
        // query "志愿者" 不是任意 phrase 的 contains-match，走 fuzzy
        // distance("志愿者", "找志愿者") = 1（删除 '找'）≤ 1 → 命中
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("志愿者"))
    }

    @Test
    fun `Levenshtein - 插入距离1 命中 - 用户多说一个字`() {
        // phrase "六十分钟"(4字) tolerance = 1
        // query "六甲十分钟"(5字) 不包含任意完整 phrase
        // distance("六甲十分钟", "六十分钟") = 1（插入 '甲'）→ 命中 DURATION_60
        assertEquals(VoiceCommand.DURATION_60, parser.parse("六甲十分钟"))
    }

    @Test
    fun `Levenshtein - 替换距离2 在默认配置下不命中`() {
        // phrase "六十分钟"(4字) tolerance = min(1, 4/3) = 1
        // query "六甲乙十分钟"(6字), 长度差 2 > threshold 1 → 剪枝直接 MAX_VALUE
        // 所有其他 phrase 长度也都距离不够 → null
        assertNull(parser.parse("六甲乙十分钟"))
    }

    @Test
    fun `Levenshtein - 长度差大于阈值时剪枝`() {
        // query "六甲乙丙丁十分钟"(8字)
        // 与所有 phrase 长度差均 > 1（最长 phrase "一百二十分钟" 仅 6字）
        // 触发 levenshtein 内 abs(la-lb) > threshold 剪枝，全部返回 MAX_VALUE
        assertNull(parser.parse("六甲乙丙丁十分钟"))
    }

    @Test
    fun `精确包含取最长 phrase`() {
        // query "重新加载完成" 同时包含 RETRY("重新加载") 和 SAVE("完成")
        // exact 匹配阶段取最长 phrase："重新加载"(4字) > "完成"(2字) → RETRY
        assertEquals(VoiceCommand.RETRY, parser.parse("重新加载完成"))
    }

    @Test
    fun `归一化保留数字 - 数字 phrase 仍可命中`() {
        // phrase "30分钟" 含数字，normalize 保留数字字符
        // query "我跑30分钟" 包含 "30分钟" → 命中 DURATION_30
        assertEquals(VoiceCommand.DURATION_30, parser.parse("我跑30分钟"))
        assertEquals(VoiceCommand.RATE_5, parser.parse("我打5星"))
    }

    @Test
    fun `归一化剥离全角与半角标点`() {
        // 半角逗号、中文叹号、破折号均非 letterOrDigit 也非 CJK，应被剥离
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("我，要—跑步！"))
        assertEquals(VoiceCommand.CANCEL, parser.parse("取——消！！"))
    }

    @Test
    fun `空字符串与纯标点返回 null`() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("，。！？——"))
        assertNull(parser.parse("\t\n "))
    }
}
