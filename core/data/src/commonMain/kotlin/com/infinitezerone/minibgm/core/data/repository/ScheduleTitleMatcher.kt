package com.infinitezerone.minibgm.core.data.repository

/**
 * 片名归一化与容错匹配的纯字符串逻辑（自 [ScheduleRepositoryImpl] 提升为顶层，无类状态依赖）。
 */

internal fun titlesRoughlyEqual(
    a: String,
    b: String,
): Boolean {
    if (a.isBlank() || b.isBlank()) return false
    return canonicalTitleKey(a) == canonicalTitleKey(b)
}

/**
 * 片名归一化：全角数字/字母 → 半角、罗马数字 → 阿拉伯、季/期/部/クール/season 归一，
 * 再抹平连接符/中点/空白；让「第2季 / 第二季 / 2nd Season / シーズン2」等同起来。
 */
internal fun canonicalTitleKey(raw: String): String {
    if (raw.isBlank()) return ""
    val s =
        raw
            .trim()
            .lowercase()
            .map { c ->
                val code = c.code
                if ((code in 0xFF10..0xFF19) || (code in 0xFF21..0xFF3A) || (code in 0xFF41..0xFF5A)) {
                    (code - 0xFEE0).toChar()
                } else {
                    c
                }
            }.joinToString("")
            .replace(ROMAN_NUMERAL_REGEX) { ROMAN_NUMERALS[it.value] ?: it.value }
            .replace(Regex("第\\s*([一二三四五六七八九十]+)\\s*[季期部章]")) { "s" + chineseNumberToArabic(it.groupValues[1]) }
            .replace(Regex("第\\s*(\\d+)\\s*[季期部章]"), "s$1")
            .replace(Regex("(?:season|シーズン)\\s*(\\d+)"), "s$1")
            .replace(Regex("(\\d+)\\s*(?:st|nd|rd|th)\\s+season"), "s$1")
            .replace(Regex("(\\d+)\\s*クール"), "s$1")
    return s.replace(TITLE_NOISE_REGEX, "")
}

internal fun chineseNumberToArabic(cn: String): String {
    val digits = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    return when {
        cn == "十" -> "10"
        cn.startsWith("十") -> (10 + (digits[cn.getOrNull(1)] ?: 0)).toString()
        cn.contains("十") -> {
            val parts = cn.split("十")
            val tens = digits[parts[0].firstOrNull()] ?: 1
            val ones = parts.getOrNull(1)?.firstOrNull()?.let { digits[it] } ?: 0
            (tens * 10 + ones).toString()
        }
        else -> cn.mapNotNull { digits[it] }.joinToString("").ifBlank { cn }
    }
}

/** 片名归一化时抹掉的连接符/中点/空白（含全角变体） */
private val TITLE_NOISE_REGEX = Regex("[・&＆\\-\\s　·]")

private val ROMAN_NUMERAL_REGEX = Regex("[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫⅰⅱⅲⅳⅴⅵⅶⅷⅸⅹⅺⅻ]")
private val ROMAN_NUMERALS =
    mapOf(
        "Ⅰ" to "1",
        "Ⅱ" to "2",
        "Ⅲ" to "3",
        "Ⅳ" to "4",
        "Ⅴ" to "5",
        "Ⅵ" to "6",
        "Ⅶ" to "7",
        "Ⅷ" to "8",
        "Ⅸ" to "9",
        "Ⅹ" to "10",
        "Ⅺ" to "11",
        "Ⅻ" to "12",
        "ⅰ" to "1",
        "ⅱ" to "2",
        "ⅲ" to "3",
        "ⅳ" to "4",
        "ⅴ" to "5",
        "ⅵ" to "6",
        "ⅶ" to "7",
        "ⅷ" to "8",
        "ⅸ" to "9",
        "ⅹ" to "10",
        "ⅺ" to "11",
        "ⅻ" to "12",
    )
