package com.infinitezerone.minibgm.core.common

/**
 * 动漫片名清洗与核心候选词提炼工具。
 *
 * 核心目标：提高第三方采集站（MacCMS / TVBox）的「首发命中率（First-shot Hit Rate）」。
 *
 * 背景与痛点：
 * 1. Bangumi 条目名通常为维基化学术名，经常附带季数、分段、副标题限定词（如「飙马野郎 JOJO的奇妙冒险 第二&第三赛段」）。
 * 2. 第三方采集站仅收录精简核心片名（如「JOJO的奇妙冒险飙马野郎」），不支持倒序或长限定词。
 * 3. 本工具在 0 开销、确定性正则下，剥离常见季数/赛段/篇章限定词并提炼独立 CJK 子短语，
 *    供取源引擎作为高优先级候选（Fast Track Racing）进行毫秒级秒播。
 */
object TitleSanitizer {
    /** 季数、期数、赛段、阶段、篇章等维基限定词正则 */
    private val QUALIFIER_REGEX =
        Regex(
            """(?:""" +
                """第\s*[0-9一二三四五六七八九十两]+\s*(?:[&＆、和\+至到\-~～]\s*第?\s*[0-9一二三四五六七八九十两]+)?\s*(?:季|期|部|赛段|阶段|卷|篇|章)""" +
                """|[0-9]+(?:st|nd|rd|th)\s*(?:[&＆、和\+至到\-~～]\s*[0-9]+(?:st|nd|rd|th))?\s*(?:STAGE|Season|Part)?""" +
                """|\b(?:Part|Season|STAGE)\.?\s*[0-9一二三四IVX]+""" +
                """|\b(?:The\s+)?Final\s+Season\b""" +
                """|最终季|完结篇|前篇|后篇|中篇|后半|前半|总集篇|特别篇""" +
                """|\b(?:OVA|OAD|SP)\b""" +
                """|\s+[^\s:：·•・/／\-_~～]{2,}篇(?:\s|$)""" +
                """)""",
            RegexOption.IGNORE_CASE,
        )

    /** 括号及其备注内容（如 (FGO)、【无修】等） */
    private val BRACKET_REGEX = Regex("""[\(（\[【][^\)）\]】]*[\)）\]】]""")

    /** 复合标题主分隔符（冒号、全宽波浪号、破折号、带空格的斜杠、空格等） */
    private val DELIMITER_REGEX = Regex("""\s*[:：~～\-–—]+\s*|\s+[/／]\s*|\s+""")

    /**
     * 提炼标题的纯净衍生形态（高辨识度核心子短语、剥离限定词后的纯名）。
     * 若输入已是纯净短标题，返回空列表。
     */
    fun extractDerivatives(title: String): List<String> {
        val trimmed = title.trim()
        if (trimmed.length < 2) return emptyList()

        val noBrackets = BRACKET_REGEX.replace(trimmed, " ").trim()
        val stripped = QUALIFIER_REGEX.replace(noBrackets, " ").trim()
        val cleaned = stripped.replace(Regex("""\s+"""), " ").trim()

        val results = mutableListOf<String>()

        // 核心子短语（例如："飙马野郎"、"JOJO的奇妙冒险"）
        val baseForSplit = if (cleaned.isNotEmpty()) cleaned else trimmed
        val parts =
            DELIMITER_REGEX
                .split(baseForSplit)
                .map { it.trim { ch -> ch <= ' ' || ch in "-_~～·•・" } }
                .filter { it.length >= 2 }

        if (parts.size > 1) {
            for (part in parts) {
                // 仅保留含有中日文字形的子短语（避免将纯数字或纯英文单词如 "The", "on" 单独拆出）
                if (hasCjk(part) && part !in results && part != trimmed) {
                    results.add(part)
                }
            }
        }

        // 剥离限定词后的干净全名（例如："飙马野郎 JOJO的奇妙冒险"）
        if (cleaned.length >= 2 && cleaned != trimmed && cleaned !in results) {
            results.add(cleaned)
        }

        return results
    }

    private fun hasCjk(text: String): Boolean =
        text.any { ch ->
            ch.code in 0x3040..0x30FF || ch.code in 0x3400..0x4DBF || ch.code in 0x4E00..0x9FFF
        }
}
