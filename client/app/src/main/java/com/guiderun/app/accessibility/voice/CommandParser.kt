package com.guiderun.app.accessibility.voice

/**
 * 把 ASR 听写出的自由文本映射到 [VoiceCommand]。
 *
 * 解析策略（按优先级）：
 * 1. **包含匹配**：归一化后的文本包含某 phrase → 命中；命中多条时取 phrase 最长者（更具体）。
 * 2. **编辑距离兜底**：若步骤 1 失败，对每个 phrase 计算 Levenshtein 距离，距离 ≤ [maxEditDistance] 且不超过 phrase 长度的 1/3 时命中。
 *    - 用于纠正"开始跑步"→"开始炮步"等同音误识别。
 *
 * 归一化规则：去除空白、ASCII 标点、中文标点。大小写统一为小写（理论上中文无影响，预防英文混入）。
 *
 * 线程安全：纯函数，无状态。
 */
class CommandParser(
    private val maxEditDistance: Int = 1,
) {
    /** 返回最匹配的指令；未命中返回 null。 */
    fun parse(rawText: String): VoiceCommand? {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) return null

        // 1) 包含匹配：取最长 phrase 命中者
        var bestExact: Pair<VoiceCommand, Int>? = null  // command -> phrase length
        for (cmd in VoiceCommand.entries) {
            for (phrase in cmd.phrases) {
                val normPhrase = normalize(phrase)
                if (normPhrase.isNotEmpty() && normalized.contains(normPhrase)) {
                    val curLen = normPhrase.length
                    val best = bestExact
                    if (best == null || curLen > best.second) {
                        bestExact = cmd to curLen
                    }
                }
            }
        }
        if (bestExact != null) return bestExact.first

        // 2) 编辑距离兜底
        var bestFuzzy: Pair<VoiceCommand, Int>? = null  // command -> distance
        for (cmd in VoiceCommand.entries) {
            for (phrase in cmd.phrases) {
                val normPhrase = normalize(phrase)
                if (normPhrase.isEmpty()) continue
                val tolerance = minOf(maxEditDistance, normPhrase.length / 3)
                if (tolerance <= 0) continue
                val distance = levenshtein(normalized, normPhrase, threshold = tolerance)
                if (distance in 1..tolerance) {
                    val best = bestFuzzy
                    if (best == null || distance < best.second) {
                        bestFuzzy = cmd to distance
                    }
                }
            }
        }
        return bestFuzzy?.first
    }

    private fun normalize(text: String): String {
        return buildString(text.length) {
            for (ch in text) {
                if (ch.isLetterOrDigit() || isCjk(ch)) {
                    append(ch.lowercaseChar())
                }
            }
        }
    }

    private fun isCjk(ch: Char): Boolean {
        return ch.code in 0x4E00..0x9FFF
    }

    /**
     * 经典 Levenshtein 距离，带剪枝：
     * 若长度差已超过阈值，直接返回 [Int.MAX_VALUE]，避免遍历整张表。
     */
    private fun levenshtein(a: String, b: String, threshold: Int): Int {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > threshold) return Int.MAX_VALUE
        if (la == 0) return lb
        if (lb == 0) return la

        var prev = IntArray(lb + 1) { it }
        var curr = IntArray(lb + 1)
        for (i in 1..la) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,        // 插入
                    prev[j] + 1,            // 删除
                    prev[j - 1] + cost,     // 替换
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > threshold) return Int.MAX_VALUE
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[lb]
    }
}
