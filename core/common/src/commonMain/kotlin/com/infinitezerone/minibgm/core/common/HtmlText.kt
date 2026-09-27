package com.infinitezerone.minibgm.core.common

private val HTML_ENTITY_REGEX = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);")

private val NAMED_ENTITIES =
    mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
    )

/**
 * 还原 HTML 实体（`&amp;` / `&#39;` / `&quot;` / `&#x27;` 等）。
 *
 * bgm.tv 的 legacy 搜索接口与部分 bangumi-data 条目的标题是 HTML 转义的
 * （如 `Let&#39;s Go 怪奇组`、`2nd &amp; 3rd STAGE`），直接入库/展示会在 UI 上露出
 * `&#39;`/`&amp;` 这类符号。所有来自这些源的文本在进入仓库前都应先过一遍本函数。
 */
fun String.unescapeHtmlEntities(): String {
    if ('&' !in this) return this
    return HTML_ENTITY_REGEX.replace(this) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x") ->
                entity
                    .substring(2)
                    .toIntOrNull(16)
                    ?.takeIf { it in 0..0x10FFFF }
                    ?.let { codePointToString(it) } ?: match.value
            entity.startsWith("#") ->
                entity
                    .substring(1)
                    .toIntOrNull()
                    ?.takeIf { it in 0..0x10FFFF }
                    ?.let { codePointToString(it) } ?: match.value
            else -> NAMED_ENTITIES[entity.lowercase()] ?: match.value
        }
    }
}

private fun codePointToString(codePoint: Int): String =
    if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        val offset = codePoint - 0x10000
        val high = (0xD800 + (offset shr 10)).toChar()
        val low = (0xDC00 + (offset and 0x3FF)).toChar()
        "$high$low"
    }
