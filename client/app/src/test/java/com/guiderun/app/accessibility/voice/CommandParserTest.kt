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
    fun `编辑距离兜底 - 同音字误识别`() {
        // "开始跑步" → "开始炮步"（讯飞偶发把同音字搞错）
        assertEquals(VoiceCommand.CREATE_REQUEST, parser.parse("开始炮步"))
    }

    @Test
    fun `完全无关文本返回 null`() {
        assertNull(parser.parse("今天天气真不错"))
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
    fun `Levenshtein - 短 phrase 长度小于3 时不走 fuzzy`() {
        // phrase "救命"(2字) tolerance = min(1, 2/3=0) = 0 → continue，不参与模糊匹配
        // query "救鸣"(2字) 与 "救命" 距离 1，但被 tolerance=0 拒绝
        // 防止 SOS 等关键指令被同音误触
        assertNull(parser.parse("救鸣"))
    }

    @Test
    fun `Levenshtein - 长度差大于阈值时剪枝`() {
        // query "六甲乙丙丁十分钟"(8字)
        // 与所有 phrase 长度差均 > 1（最长 phrase "一百二十分钟" 仅 6字）
        // 触发 levenshtein 内 abs(la-lb) > threshold 剪枝，全部返回 MAX_VALUE
        assertNull(parser.parse("六甲乙丙丁十分钟"))
    }

    @Test
    fun `Levenshtein - 自定义 maxEditDistance=2 时允许两次替换`() {
        val tolerantParser = CommandParser(maxEditDistance = 2)
        // phrase "一百二十分钟"(6字) tolerance = min(2, 6/3=2) = 2
        // query "一百三十分种" 与 phrase 距离 = 2（二→三, 钟→种）
        // 默认 parser tolerance=1 不会命中；放宽到 2 后命中
        assertNull(parser.parse("一百三十分种"))
        assertEquals(VoiceCommand.DURATION_120, tolerantParser.parse("一百三十分种"))
    }

    @Test
    fun `Levenshtein - 模糊距离1 优先于距离2`() {
        val tolerantParser = CommandParser(maxEditDistance = 2)
        // "开始炮步"(4字) 与 "开始跑步"(CREATE_REQUEST) 距离 1
        // 比与 phrase "开始跑步"任意其他距离 2 的候选都更优
        assertEquals(VoiceCommand.CREATE_REQUEST, tolerantParser.parse("开始炮步"))
    }

    @Test
    fun `精确包含优先于编辑距离`() {
        // query "保存修改请求" 同时包含 SAVE("保存") 和 MODIFY_REQUEST("修改请求")
        // 走 exact 匹配阶段（取最长 phrase），不会回退到 fuzzy
        // "修改请求"(4字) > "保存"(2字) → MODIFY_REQUEST
        assertEquals(VoiceCommand.MODIFY_REQUEST, parser.parse("保存修改请求"))
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
