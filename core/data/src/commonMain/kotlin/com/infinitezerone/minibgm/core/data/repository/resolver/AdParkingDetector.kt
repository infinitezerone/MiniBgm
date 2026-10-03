package com.infinitezerone.minibgm.core.data.repository.resolver

internal fun detectAdParking(
    title: String,
    html: String,
): Boolean =
    title.contains("域名出售", ignoreCase = true) ||
        title.contains("Domain for Sale", ignoreCase = true) ||
        title.contains("404 Not Found", ignoreCase = true) ||
        html.contains("该域名已过期", ignoreCase = true) ||
        (html.length < 200 && title.isEmpty())

internal fun detectSearchUrlPattern(
    html: String,
    siteUrl: String,
    origin: String?,
): Pair<Boolean, String?> {
    val formMatch =
        Regex(
            """<form\b[^>]*action=["']([^"']*)["'][^>]*>(.*?)</form>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ).findAll(html)
            .firstOrNull { form ->
                val content = form.groupValues[2]
                content.contains("name=\"s\"", ignoreCase = true) ||
                    content.contains("name=\"wd\"", ignoreCase = true) ||
                    content.contains("name=\"keyword\"", ignoreCase = true) ||
                    content.contains("name=\"search\"", ignoreCase = true) ||
                    content.contains("name=\"q\"", ignoreCase = true)
            }

    if (formMatch != null) {
        val action = formMatch.groupValues[1]
        val formContent = formMatch.groupValues[2]
        val inputName =
            Regex("""<input\b[^>]*name=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                .findAll(formContent)
                .map { it.groupValues[1] }
                .firstOrNull { name ->
                    name in listOf("s", "wd", "keyword", "search", "q", "query")
                } ?: "s"

        val absAction = absoluteUrl(action.ifBlank { "/" }, origin) ?: siteUrl
        val pattern =
            if (absAction.contains("?")) {
                "$absAction&$inputName={title}"
            } else {
                "$absAction?$inputName={title}"
            }
        return true to pattern
    }

    if (html.contains("?s=") || html.contains("/search/")) {
        val pattern =
            if (html.contains("?s=")) {
                "${origin ?: siteUrl}?s={title}"
            } else {
                "${origin ?: siteUrl}/search/{title}"
            }
        return true to pattern
    }

    return false to null
}
