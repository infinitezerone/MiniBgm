package com.infinitezerone.minibgm.core.data.repository.resolver

private val A_TAG_REGEX =
    Regex("""<a\b[^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)

private val IGNORED_LINK_EXTS =
    setOf(".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2")

private val IGNORED_PATH_PREFIXES =
    setOf("notify", "about", "contact", "login", "register", "faq", "donate")

internal fun extractEpisodeLinks(
    html: String,
    pageUrl: String,
    targetEp: Float,
): List<String> {
    if (targetEp <= 0f) return emptyList()
    val origin = pageOrigin(pageUrl)
    val epInt = targetEp.toInt()
    val paddedEp = epInt.toString().padStart(2, '0')
    val candidates = mutableListOf<String>()
    val categoryFallbacks = mutableListOf<String>()

    val matches = A_TAG_REGEX.findAll(html).toList()
    for (match in matches) {
        val rawHref = match.groupValues[1].trim()
        val rawTag = match.value
        val text = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
        if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:", ignoreCase = true)) {
            continue
        }
        val lowerHref = rawHref.lowercase()
        if (IGNORED_LINK_EXTS.any { lowerHref.endsWith(it) }) continue

        val fullUrl = absoluteUrl(rawHref, origin) ?: continue
        if (fullUrl == pageUrl) continue

        // 只在本注册域内下探，挡住 Twitter / Telegram 这类社交外链；
        // 主站与 m./v. 子站分属不同 host 但同域，按 host 相等会误杀
        if (!sameRegistrableDomain(pageUrl, fullUrl)) continue

        val pathAfterOrigin = fullUrl.removePrefix(origin ?: "").trim('/')
        if (pathAfterOrigin.isBlank()) continue
        val firstSegment = pathAfterOrigin.substringBefore('/').lowercase()
        if (IGNORED_PATH_PREFIXES.contains(firstSegment)) continue

        if (firstSegment == "category" || rawTag.contains("""rel="category tag"""", ignoreCase = true)) {
            categoryFallbacks.add(fullUrl)
            continue
        }

        val bracketMatch =
            Regex("""[\[\(【]\s*(?:$epInt|$paddedEp)(?:\.0)?\s*[\]\)】]""").containsMatchIn(text)
        val episodeWordMatch =
            Regex("""第\s*(?:$epInt|$paddedEp)\s*[集话話]""").containsMatchIn(text)
        val epTokenMatch =
            Regex("""(?i)\b(?:ep|e)\s*(?:$epInt|$paddedEp)\b""").containsMatchIn(text)
        val pureNumberMatch = text == epInt.toString() || text == paddedEp || text == targetEp.toString()
        val urlEpNumber = episodeNumberFromUrl(fullUrl, allowWeak = false)

        if (bracketMatch || episodeWordMatch || epTokenMatch || pureNumberMatch || urlEpNumber == targetEp) {
            candidates.add(fullUrl)
        }
    }
    val results = candidates.distinct()
    if (results.isNotEmpty()) {
        return results.take(2)
    }
    return categoryFallbacks.distinct().take(1)
}
