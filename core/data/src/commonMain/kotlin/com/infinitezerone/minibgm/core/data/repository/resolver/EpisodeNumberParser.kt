package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.toEpisodeLabel

private val SKIP_SUFFIXES =
    setOf(
        ".css",
        ".js",
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".ico",
        ".svg",
        ".rss",
        ".xml",
        ".woff",
        ".woff2",
        ".ttf",
        ".eot",
        ".map",
        ".json",
    )
private val NON_EPISODE_KEYWORDS =
    listOf(
        "/category/",
        "/tag/",
        "/page/",
        "/author/",
        "/notify/",
        "关于",
        "留言板",
        "login",
        "register",
        "cart",
        "checkout",
        "account",
        "/feed",
    )
private val PLAY_KEYWORDS = listOf("/watch", "/play", "/video", "/bangumi", "/view", "/anime", "?cat=")
private val NUMERIC_PAGE_REGEX = Regex("""^(?:https?://[^/]+)?/(?:archives/|p/)?\d+/?$""")

/** 站点详情页（条目页）的典型路径特征——首页上这些链接的锚文本就是站内条目名 */
private val DETAIL_LINK_KEYWORDS =
    listOf(
        "/voddetail/",
        "/vod/detail/",
        "/detail/",
        "/vod/",
        "/show/",
        "/subject/",
        "/bangumi/",
        "/anime/",
    )

/** 探查样本的合理片名长度：比这短的多是单字导航，比这长的多是整句推荐语 */
private const val MIN_SAMPLE_TITLE_LENGTH = 2
private const val MAX_SAMPLE_TITLE_LENGTH = 40

/** 锚文本命中这些词的多是站内导航位，不是条目名 */
private val NON_ENTRY_TITLE_KEYWORDS =
    listOf("首页", "主页", "排行", "排行榜", "最新", "全部", "更多", "登录", "注册", "公告", "资讯", "新闻")

/**
 * 从站点首页挑一个**真实存在**的条目标题当探查样本。
 *
 * 详情页链接的锚文本就是站内条目的名字，比编一个通用番名可靠得多——
 * 样本必须在目标站上真实存在，否则搜索页返回空列表，探不出搜索参数模式。
 */
internal fun findHomeEntryTitle(
    html: String,
    origin: String?,
): String? {
    val aTagRegex = Regex("""<a\b([^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val tagRegex = Regex("""<[^>]*>""")
    val spaceRegex = Regex("""\s+""")
    val originPath = origin?.trimEnd('/')?.lowercase()
    return aTagRegex
        .findAll(html)
        .mapNotNull { match ->
            val href =
                hrefRegex
                    .find(match.groupValues[1])
                    ?.groupValues
                    ?.get(1)
                    ?.trim() ?: return@mapNotNull null
            val cleanPath =
                href
                    .substringBefore('?')
                    .substringBefore('#')
                    .trim()
                    .lowercase()
            if (DETAIL_LINK_KEYWORDS.none { cleanPath.contains(it) }) return@mapNotNull null
            if (originPath != null && cleanPath == originPath) return@mapNotNull null
            val text = spaceRegex.replace(tagRegex.replace(match.groupValues[2], " "), " ").trim()
            text.takeIf { it.looksLikeEntryTitle() }
        }.firstOrNull()
}

private fun String.looksLikeEntryTitle(): Boolean =
    length in MIN_SAMPLE_TITLE_LENGTH..MAX_SAMPLE_TITLE_LENGTH &&
        NON_EPISODE_KEYWORDS.none { contains(it, ignoreCase = true) } &&
        NON_ENTRY_TITLE_KEYWORDS.none { contains(it) } &&
        any { it.isLetterOrDigit() } &&
        !all { it.isDigit() }

internal data class AnchorCandidate(
    val href: String,
    val isBookmark: Boolean,
    val cleanPath: String,
)

internal fun findCandidateEpisodeUrl(
    html: String,
    origin: String?,
): String? {
    val aTagRegex = Regex("""<a\b([^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val relRegex = Regex("""rel=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    val validLinks =
        aTagRegex
            .findAll(html)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                val href =
                    hrefRegex
                        .find(attrs)
                        ?.groupValues
                        ?.get(1)
                        ?.trim() ?: return@mapNotNull null
                val rel =
                    relRegex
                        .find(attrs)
                        ?.groupValues
                        ?.get(1)
                        ?.lowercase()
                        .orEmpty()
                val isBookmark = rel.contains("bookmark")
                val cleanPath =
                    href
                        .substringBefore('?')
                        .substringBefore('#')
                        .trim()
                        .lowercase()
                AnchorCandidate(href = href, isBookmark = isBookmark, cleanPath = cleanPath)
            }.filter { link ->
                val clean = link.cleanPath
                SKIP_SUFFIXES.none { clean.endsWith(it) } &&
                    NON_EPISODE_KEYWORDS.none { clean.contains(it) } &&
                    !clean.endsWith("#") &&
                    (clean.startsWith("/") || clean.startsWith("http"))
            }.toList()

    val bookmarkMatch = validLinks.firstOrNull { it.isBookmark }
    if (bookmarkMatch != null) {
        return absoluteUrl(bookmarkMatch.href, origin)
    }

    val numericMatch = validLinks.firstOrNull { NUMERIC_PAGE_REGEX.matches(it.cleanPath) }
    if (numericMatch != null) {
        return absoluteUrl(numericMatch.href, origin)
    }

    val playKeywordMatch = validLinks.firstOrNull { link -> PLAY_KEYWORDS.any { link.cleanPath.contains(it) } }
    if (playKeywordMatch != null) {
        return absoluteUrl(playKeywordMatch.href, origin)
    }

    val fallbackMatch = validLinks.firstOrNull { it.cleanPath != "/" && it.cleanPath != origin?.lowercase() }
    return fallbackMatch?.let { absoluteUrl(it.href, origin) }
}

/**
 * 从候选地址抽分集号（只做展示与排序标注，不做剧集规律推算）。
 *
 * 分两级信号，避免把 CDN 路径里的编号（`/hls/123/`）误当成集数：
 * - 强信号：带 ep/E/e/p 前缀的数字（`ep12`、`E07`）——无条件采用；
 * - 弱信号：路径里独占一段的裸数字（`/hls/12/`）——仅当调用方没有指定话数（整季请求）时采用。
 * 分辨率（1080p）、编码（h264/x265）、年份与超长 ID 段一律跳过。抓不到返回 null。
 * 末段（文件名）优先于整条路径。
 */
internal fun episodeNumberFromUrl(
    url: String,
    allowWeak: Boolean,
): Float? {
    val afterScheme = url.substringAfter("://")
    val pathWithSlash = afterScheme.substringAfter('/', missingDelimiterValue = "")
    if (pathWithSlash.isBlank()) return null
    val path = pathWithSlash.substringBefore('?').substringBefore('#').replace(URL_FILE_EXTENSION, "")
    val trimmedPath = path.trim('/')
    if (trimmedPath.isBlank()) return null

    val scopes = listOf(trimmedPath.substringAfterLast('/'), trimmedPath)
    for (scope in scopes) {
        val number = scope.numberToken(strongOnly = true) ?: continue
        return number
    }
    if (!allowWeak) return null
    for (scope in scopes) {
        val number = scope.numberToken(strongOnly = false) ?: continue
        return number
    }
    return null
}

private fun String.numberToken(strongOnly: Boolean): Float? {
    for (match in URL_NUMBER_TOKEN.findAll(this).toList().asReversed()) {
        val before = getOrNull(match.range.first - 1)?.lowercaseChar()
        val after = getOrNull(match.range.last + 1)?.lowercaseChar()
        val value = match.value
        if (isNoiseNumber(value, before)) continue
        val number = value.toFloatOrNull() ?: continue
        if (number <= 0f || number > 9999f) continue
        val strong = before == 'e' || before == 'p'
        if (strong) return number
        if (strongOnly) continue
        // 裸数字只有在独占一段路径（两侧都是分隔符或端点）时才当集号：
        // 嵌在哈希/ID 片段里的数字串（ac4395ad、3f1d0069、4703_...）不是分集信息——
        // 实测（dcc3.com 播放页）证实按"最后一个数字 token"抽取会标出"第 4395 话"
        val standalone = (before == null || before == '/') && (after == null || after == '/')
        if (standalone) return number
    }
    return null
}

private fun isNoiseNumber(
    value: String,
    before: Char?,
): Boolean =
    value in NOISE_NUMBERS ||
        URL_YEAR_TOKEN.matches(value) ||
        (before == 'h' || before == 'x') &&
        (value == "264" || value == "265")

/** 分集号转展示标签：整数不带小数点，小数保留（7.5 话）；统一复用 core:model 的格式化规则 */
internal fun episodeLabelFromNumber(value: Float): String = "第 ${value.toEpisodeLabel()} 话"

/**
 * 从集名/标注文本里抽分集号（`第03集`、`[12]`、`EP07`、纯数字）。
 *
 * 与 [entryMatchesEpisode] 的判定形态对齐，但这里是"抽取"而非"断言"——
 * 规则引擎拿它给 EXTRACT_STREAM 的候选列表做真实集数标注。
 * 抽不到返回 null，调用方回退到地址抽取，不做猜测。
 */
internal fun episodeNumberFromLabel(label: String): Float? {
    val text = label.trim()
    if (text.isEmpty()) return null
    Regex("""第\s*(\d+(?:\.\d+)?)\s*[集话話]""")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.toFloatOrNull()
        ?.let { return it }
    Regex("""[\[\(【]\s*(\d+(?:\.\d+)?)\s*[\]\)】]""")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.toFloatOrNull()
        ?.let { return it }
    Regex("""(?i)\b(?:ep|e)\s*\.?\s*(\d+(?:\.\d+)?)\b""")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.toFloatOrNull()
        ?.let { return it }
    if (URL_NUMBER_TOKEN.matches(text)) {
        return text.toFloatOrNull()?.takeIf { it > 0f && it <= 9999f && it !in NOISE_NUMBERS_FLOAT }
    }
    return null
}

internal fun decodeUrlComponent(encoded: String): String {
    val sb = StringBuilder()
    var i = 0
    val len = encoded.length
    while (i < len) {
        val c = encoded[i]
        if (c == '%' && i + 2 < len) {
            val hex = encoded.substring(i + 1, i + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        } else if (c == '+') {
            sb.append(' ')
            i++
            continue
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}
