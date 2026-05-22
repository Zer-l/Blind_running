package com.guiderun.app.accessibility.voice

/**
 * 视障端"发起跑步请求"页面的语音批量录入解析器。
 *
 * 用户语音格式示例：
 *   "地点 图书馆南门 时长 60 分钟 备注 慢跑节奏"
 *   "地点图书馆南门，时长一小时，备注慢跑"
 *   "时长 90 地点 操场"
 *
 * 解析规则：
 * 1. 三个字段都可省略；至少命中一个 [ParsedRequest] 字段才算解析成功
 * 2. 字段顺序不强求；按关键字"地点 / 时长 / 备注"做切分
 * 3. 时长支持阿拉伯数字 + 部分中文数字（半/一/两小时 等），归一到 {30, 60, 90, 120}
 * 4. 全部字段为空 → 返回 null，Fragment 回退到"未识别格式，请重说"
 */
data class ParsedRequest(
    val location: String?,
    val durationMinutes: Int?,
    val notes: String?,
) {
    /** 至少有一个字段被解析出来时才视为成功。 */
    val hasAnyField: Boolean
        get() = location != null || durationMinutes != null || notes != null
}

object RequestVoiceParser {

    /**
     * 解析批量语音文本。
     * @return 解析结果；若三个字段全部 null 则返回 null（调用方据此回放"未识别"提示）
     */
    fun parse(raw: String): ParsedRequest? {
        val normalized = normalizeWhitespace(raw)
        if (normalized.isBlank()) return null

        val segments = extractSegments(normalized)

        val location = segments[KEY_LOCATION]?.takeIf { it.isNotBlank() }
        val duration = segments[KEY_DURATION]?.let { parseDuration(it) }
        val notes = segments[KEY_NOTES]?.takeIf { it.isNotBlank() }

        val parsed = ParsedRequest(location, duration, notes)
        return parsed.takeIf { it.hasAnyField }
    }

    /** 提取每个关键字后到下一个关键字之前的文本片段。 */
    private fun extractSegments(text: String): Map<String, String> {
        // 找到所有关键字出现位置（带可选冒号），按位置排序后切片
        val markers = mutableListOf<Triple<Int, Int, String>>() // (start, endOfMarker, key)
        for ((aliases, key) in KEYWORD_ALIASES) {
            for (alias in aliases) {
                var idx = 0
                while (true) {
                    val found = text.indexOf(alias, idx)
                    if (found < 0) break
                    val afterMarker = skipColon(text, found + alias.length)
                    markers.add(Triple(found, afterMarker, key))
                    idx = found + alias.length
                }
            }
        }
        if (markers.isEmpty()) return emptyMap()

        // 同一位置可能被多个 alias 重复命中，按 start 排序去重（保留 endOfMarker 最大的）
        val sorted = markers
            .sortedWith(compareBy({ it.first }, { -it.second }))
            .distinctBy { it.first }

        val result = mutableMapOf<String, String>()
        for (i in sorted.indices) {
            val (_, endOfMarker, key) = sorted[i]
            val nextStart = sorted.getOrNull(i + 1)?.first ?: text.length
            val raw = text.substring(endOfMarker, nextStart).trim().trimEnd(*TRIM_CHARS)
            if (raw.isNotBlank()) {
                // 同一 key 多次命中时取最后一次（用户重说覆盖前次）
                result[key] = raw
            }
        }
        return result
    }

    private fun skipColon(text: String, idx: Int): Int {
        var i = idx
        while (i < text.length && (text[i] == ':' || text[i] == '：' || text[i] == ' ')) i++
        return i
    }

    /** 时长字段：先尝试阿拉伯数字，再尝试中文短语。归一到 {30, 60, 90, 120}。 */
    private fun parseDuration(raw: String): Int? {
        // 阿拉伯数字优先
        val arabicMatch = ARABIC_NUMBER_REGEX.find(raw)
        if (arabicMatch != null) {
            val n = arabicMatch.value.toIntOrNull() ?: return null
            // 简单启发：n<=4 视为小时；其余视为分钟
            val minutes = if (n in 1..4 && containsHourUnit(raw)) n * 60 else n
            return snapToBucket(minutes)
        }
        // 中文短语映射
        for ((kw, mins) in CHINESE_DURATION_MAP) {
            if (raw.contains(kw)) return mins
        }
        return null
    }

    private fun containsHourUnit(raw: String): Boolean =
        raw.contains("小时") || raw.contains("钟头") || raw.contains("个钟")

    /** 归一到 4 档：偏向就近选择，避免随便归到 30。 */
    private fun snapToBucket(minutes: Int): Int? {
        if (minutes <= 0) return null
        return BUCKETS.minByOrNull { kotlin.math.abs(it - minutes) }
    }

    private fun normalizeWhitespace(raw: String): String =
        raw.replace('　', ' ').trim()

    private val ARABIC_NUMBER_REGEX = Regex("""\d+""")

    private val CHINESE_DURATION_MAP = listOf(
        "半小时" to 30,
        "半个钟" to 30,
        "三十分钟" to 30,
        "三十" to 30,
        "一个半小时" to 90,
        "一小时半" to 90,
        "九十分钟" to 90,
        "九十" to 90,
        "一小时" to 60,
        "一个小时" to 60,
        "一个钟头" to 60,
        "一个钟" to 60,
        "六十分钟" to 60,
        "六十" to 60,
        "两小时" to 120,
        "两个小时" to 120,
        "两个钟头" to 120,
        "两个钟" to 120,
        "一百二十分钟" to 120,
        "一百二十" to 120,
    )

    private val BUCKETS = intArrayOf(30, 60, 90, 120)
    private val TRIM_CHARS = charArrayOf(',', '，', '。', '.', '、', ';', '；')

    private const val KEY_LOCATION = "location"
    private const val KEY_DURATION = "duration"
    private const val KEY_NOTES = "notes"

    /** 关键字别名列表：识别口语化变体。aliases 越长越具体越靠前，便于最长匹配。 */
    private val KEYWORD_ALIASES = listOf(
        listOf("集合地点", "集合点", "地点", "位置", "地方") to KEY_LOCATION,
        listOf("时长", "时间", "跑多久", "跑步时长") to KEY_DURATION,
        listOf("备注", "说明", "注释") to KEY_NOTES,
    )
}
