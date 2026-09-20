package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.serialization.Serializable

/** 单次请求解析的页面数上限，避免一次找源退化成批量抓取 */
private const val MAX_PAGES = 5

/** 单个页面最多采纳的候选来源数 */
private const val MAX_CANDIDATES_PER_PAGE = 12

/** 单份流清单最多采纳的条目数（用户配置的接口可整季返回，比页面抓取宽） */
private const val MAX_MANIFEST_ENTRIES = 50

private val MEDIA_URL_REGEX =
    Regex("""https?://[^"'()\s<>]+?\.(?:m3u8|mp4|mkv|flv|webm|ts)(?:\?[^"'()\s<>]*)?""", RegexOption.IGNORE_CASE)

private val MEDIA_TAG_REGEX =
    Regex("""<(?:source|video)\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val IFRAME_TAG_REGEX =
    Regex("""<iframe\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val INVALID_MEDIA_HOSTS = setOf("nflxso.net", "netflix.com", "nflximg.net", "akamaized.net")

private fun isInvalidMediaHost(url: String): Boolean {
    val host =
        url
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
    return INVALID_MEDIA_HOSTS.any { host.endsWith(it) }
}

/** 流清单条目：Stremio `/stream` 响应的兼容子集——url 必填，title/headers 可选，未知字段忽略 */
@Serializable
internal data class StreamManifestEntry(
    val url: String = "",
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/**
 * 流清单信封：`streams` 字段缺失表示响应不是清单（调用方回退正则抽取），
 * 空数组表示接口明确宣告无结果。
 */
@Serializable
internal data class StreamManifest(
    val streams: List<StreamManifestEntry>? = null,
)

/**
 * 把调用方给定的播放页解析成可播放来源。
 *
 * 只做"取一次页面 + 抽候选地址"，不跟踪站点、不执行 JS：抽不到就返回空列表，
 * 由上层降级为"没有可用结果"。
 */
interface PlaybackResolverRepository {
    suspend fun resolvePages(
        pageUrls: List<String>,
        epNumber: Float = 0f,
        siteName: String = "",
    ): List<PlayableSource>

    /** 按用户自备的模板接口地址取一次并抽取可播放地址；[headers] 随请求发出并回传给播放器 */
    suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String> = emptyMap(),
        epNumber: Float = 0f,
        siteName: String = "",
    ): List<PlayableSource>
}

class PlaybackResolverRepositoryImpl(
    private val pageFetchService: PageFetchService,
) : PlaybackResolverRepository {
    override suspend fun resolvePages(
        pageUrls: List<String>,
        epNumber: Float,
        siteName: String,
    ): List<PlayableSource> =
        pageUrls
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PAGES)
            .flatMap { page ->
                val fetched = pageFetchService.fetchHtml(page) ?: return@flatMap emptyList()
                sniffPageOrSubPages(fetched, epNumber, siteName, pageFetchService)
            }.distinctBy { it.url }

    override suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String>,
        epNumber: Float,
        siteName: String,
    ): List<PlayableSource> {
        val fetched = pageFetchService.fetchHtml(url, headers) ?: return emptyList()
        // 用户配置的取源接口优先识别结构化流清单（兼容 Stremio /stream 形态），
        // 非清单响应回退到页面正则与智能嗅探
        parseStreamManifest(fetched.html, fetched.url, epNumber, siteName, headers)?.let { return it }
        val sources = sniffPageOrSubPages(fetched, epNumber, siteName, pageFetchService)
        return sources
            .distinctBy { it.url }
            .map { source -> if (headers.isEmpty()) source else source.copy(headers = source.headers + headers) }
    }
}

/**
 * 识别结构化流清单（`{"streams":[{"url","title","headers"}]}`，Stremio /stream 兼容）。
 *
 * 返回 null 表示响应不是清单（无 `streams` 字段或不是 JSON 对象），由调用方回退正则；
 * 返回空列表表示清单合法但接口明确无结果。清单条目自带请求头，规则请求头追加其后
 * （与正则路径一致：规则头优先）。
 */
internal fun parseStreamManifest(
    body: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
    ruleHeaders: Map<String, String>,
): List<PlayableSource>? {
    if (!body.trimStart().startsWith("{")) return null
    val manifest =
        runCatching { BgmHttpClient.jsonConfig.decodeFromString<StreamManifest>(body) }.getOrNull()
            ?: return null
    val streams = manifest.streams ?: return null
    return streams
        .mapNotNull { entry ->
            val candidateUrl = entry.url.trim()
            if (!candidateUrl.startsWith("http://", ignoreCase = true) &&
                !candidateUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            PlayableSource(
                url = candidateUrl,
                kind = PlaylistEntryKind.DIRECT,
                label = entry.title.ifBlank { if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else "" },
                episodeSort = epNumber,
                siteName = siteName,
                pageUrl = pageUrl,
                headers = entry.headers + ruleHeaders,
            )
        }.distinctBy { it.url }
        .take(MAX_MANIFEST_ENTRIES)
}

/**
 * 从页面正文抽取候选地址。
 *
 * 先还原 JSON 里被转义的 `\/` 与 HTML 实体 `&amp;`，否则绝大多数直链都匹配不到；
 * 相对地址按页面 origin 补全，Referer 取页面站点根，播放器起播时通常需要它。
 * 分集标注优先从候选地址真实抽取（带 ep/E 前缀的数字为强信号），
 * 抽不到才回退到查询话数——避免把"查询第 N 话"当成"页面上所有候选都是第 N 话"。
 */
internal fun extractPlayableSources(
    html: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
): List<PlayableSource> {
    val normalized = html.replace("\\/", "/").replace("&amp;", "&")
    val origin = pageOrigin(pageUrl)
    val headers = origin?.let { mapOf("Referer" to it) }.orEmpty()

    fun buildSource(
        candidateUrl: String,
        kind: PlaylistEntryKind,
    ): PlayableSource {
        val fromUrl = episodeNumberFromUrl(candidateUrl, allowWeak = epNumber <= 0f)
        val sort = fromUrl ?: epNumber
        val label =
            when {
                fromUrl != null -> episodeLabelFromNumber(fromUrl)
                epNumber > 0f -> "第 ${epNumber.toInt()} 话"
                else -> ""
            }
        return PlayableSource(
            url = candidateUrl,
            kind = kind,
            label = label,
            episodeSort = sort,
            siteName = siteName,
            pageUrl = pageUrl,
            headers = headers,
        )
    }

    val direct: List<PlayableSource> =
        (
            MEDIA_URL_REGEX.findAll(normalized).map { it.value } +
                MEDIA_TAG_REGEX.findAll(normalized).map { it.groupValues[1] }
        ).mapNotNull { absoluteUrl(it, origin) }
            .filterNot { isInvalidMediaHost(it) }
            .map { buildSource(it, PlaylistEntryKind.DIRECT) }
            .toList()

    val pages: List<PlayableSource> =
        IFRAME_TAG_REGEX
            .findAll(normalized)
            .mapNotNull { absoluteUrl(it.groupValues[1], origin) }
            .filterNot { candidate -> direct.any { it.url == candidate } }
            .map { buildSource(it, PlaylistEntryKind.PAGE) }
            .toList()

    return (direct + pages).take(MAX_CANDIDATES_PER_PAGE)
}

private val URL_FILE_EXTENSION = Regex("""\.(m3u8|mp4|mkv|flv|webm|ts)$""", RegexOption.IGNORE_CASE)
private val URL_NUMBER_TOKEN = Regex("""\d+(?:\.\d+)?""")
private val URL_YEAR_TOKEN = Regex("""^(?:19|20)\d{2}$""")
private val NOISE_NUMBERS = setOf("1080", "720", "480", "360", "240", "2160", "1440", "4320", "60")

/**
 * 从候选地址抽分集号（只做展示与排序标注，不做剧集规律推算）。
 *
 * 分两级信号，避免把 CDN 路径里的编号（`/hls/123/`）误当成集数：
 * - 强信号：带 ep/E/e/p 前缀的数字（`ep12`、`E07`）——无条件采用；
 * - 弱信号：末段或路径里最后一个裸数字——仅当调用方没有指定话数（整季请求）时采用。
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
        val value = match.value
        if (isNoiseNumber(value, before)) continue
        val number = value.toFloatOrNull() ?: continue
        if (number <= 0f || number > 9999f) continue
        val strong = before == 'e' || before == 'p'
        if (strong || !strongOnly) return number
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

/** 分集号转展示标签：整数不带小数点，小数保留（7.5 话） */
internal fun episodeLabelFromNumber(value: Float): String {
    val text =
        if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    return "第 $text 话"
}

/** scheme://host/ 形式的站点根，作为 Referer 与相对地址的基准 */
private fun pageOrigin(url: String): String? {
    val schemeEnd = url.indexOf("://").takeIf { it > 0 } ?: return null
    val scheme = url.substring(0, schemeEnd)
    val rest = url.substring(schemeEnd + 3)
    val host = rest.substringBefore('/').substringBefore('?')
    if (host.isBlank()) return null
    return "$scheme://$host/"
}

/** 把协议相对/根相对/相对路径补成绝对地址；无法补全时返回 null */
private fun absoluteUrl(
    candidate: String,
    origin: String?,
): String? {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    if (origin == null) return null
    return when {
        trimmed.startsWith("//") -> origin.substringBefore("//") + trimmed
        trimmed.startsWith("/") -> origin.substringBeforeLast("/") + trimmed
        else -> origin + trimmed
    }
}

/** 针对单页或其子页面（单集页/iframe）执行流探测 */
private suspend fun sniffPageOrSubPages(
    fetched: FetchedPage,
    epNumber: Float,
    siteName: String,
    pageFetchService: PageFetchService,
): List<PlayableSource> {
    // 1. 尝试当前单页专有协议嗅探（Anime1）
    sniffAnime1Stream(fetched.html, fetched.url, epNumber, siteName, pageFetchService)?.let {
        return listOf(it)
    }

    // 2. 尝试当前页 MacCMS 嗅探
    val macCms = extractMacCmsSources(fetched.html, fetched.url, epNumber, siteName)
    if (macCms.isNotEmpty()) return macCms

    // 3. 常规直链与 iframe 抽取
    val extracted = extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
    if (extracted.any { it.kind == PlaylistEntryKind.DIRECT }) {
        return extracted
    }

    // 4. 检查单层 iframe
    val iframeCandidate = extracted.firstOrNull { it.kind == PlaylistEntryKind.PAGE }
    if (iframeCandidate != null && iframeCandidate.url.isNotBlank()) {
        val subFetched = pageFetchService.fetchHtml(iframeCandidate.url)
        if (subFetched != null) {
            sniffAnime1Stream(subFetched.html, subFetched.url, epNumber, siteName, pageFetchService)?.let {
                return listOf(it)
            }
            val subMac = extractMacCmsSources(subFetched.html, subFetched.url, epNumber, siteName)
            if (subMac.isNotEmpty()) return subMac
            val subExtracted = extractPlayableSources(subFetched.html, subFetched.url, epNumber, siteName)
            if (subExtracted.any { it.kind == PlaylistEntryKind.DIRECT }) {
                return subExtracted
            }
        }
    }

    // 5. 若页面无直接直链且无 iframe，尝试作为搜索列表页/目录页探测分集单集页面
    val epLinks = extractEpisodeLinks(fetched.html, fetched.url, epNumber)
    for (epLink in epLinks) {
        val epFetched = pageFetchService.fetchHtml(epLink) ?: continue
        sniffAnime1Stream(epFetched.html, epFetched.url, epNumber, siteName, pageFetchService)?.let {
            return listOf(it)
        }
        val epMac = extractMacCmsSources(epFetched.html, epFetched.url, epNumber, siteName)
        if (epMac.isNotEmpty()) return epMac
        val epExtracted = extractPlayableSources(epFetched.html, epFetched.url, epNumber, siteName)
        if (epExtracted.any { it.kind == PlaylistEntryKind.DIRECT }) {
            return epExtracted
        }
        val epIframe = epExtracted.firstOrNull { it.kind == PlaylistEntryKind.PAGE }
        if (epIframe != null && epIframe.url.isNotBlank()) {
            val epIframeFetched = pageFetchService.fetchHtml(epIframe.url)
            if (epIframeFetched != null) {
                val epIframeExtracted =
                    extractPlayableSources(epIframeFetched.html, epIframeFetched.url, epNumber, siteName)
                if (epIframeExtracted.any { it.kind == PlaylistEntryKind.DIRECT }) {
                    return epIframeExtracted
                }
            }
        }
    }

    return extracted
}

private val ANIME1_APIREQ_REGEX =
    Regex("""data-apireq\s*=\s*["']([^"'>]+)["']""", RegexOption.IGNORE_CASE)

private val ANIME1_SRC_REGEX =
    Regex(""""src"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

internal suspend fun sniffAnime1Stream(
    html: String,
    pageUrl: String,
    targetEp: Float,
    siteName: String,
    pageFetchService: PageFetchService,
): PlayableSource? {
    val apireqMatch = ANIME1_APIREQ_REGEX.find(html) ?: return null
    val rawApireq = apireqMatch.groupValues[1]
    val decoded = decodeUrlComponent(rawApireq)

    val apiPage =
        pageFetchService.postForm(
            url = "https://v.anime1.me/api",
            formData = mapOf("d" to decoded),
            requestHeaders = mapOf("Referer" to pageUrl),
        ) ?: return null

    val srcMatch = ANIME1_SRC_REGEX.find(apiPage.html) ?: return null
    val rawSrc = srcMatch.groupValues[1].replace("\\/", "/")
    val streamUrl =
        when {
            rawSrc.startsWith("//") -> "https:$rawSrc"
            rawSrc.startsWith("http://", ignoreCase = true) || rawSrc.startsWith("https://", ignoreCase = true) -> rawSrc
            else -> "https://v.anime1.me$rawSrc"
        }

    val streamHeaders = mutableMapOf<String, String>()
    streamHeaders["Referer"] = "https://anime1.me/"
    val cookie = apiPage.responseHeaders["Cookie"]
    if (!cookie.isNullOrBlank()) {
        streamHeaders["Cookie"] = cookie
    }

    val label = if (targetEp > 0f) "第 ${targetEp.toInt()} 话" else ""
    return PlayableSource(
        url = streamUrl,
        kind = PlaylistEntryKind.DIRECT,
        label = label,
        episodeSort = targetEp,
        siteName = siteName.ifBlank { "Anime1" },
        pageUrl = pageUrl,
        headers = streamHeaders,
    )
}

private val MACCMS_PLAY_URL_REGEX =
    Regex(""""url"\s*:\s*"([^"]*\$[a-zA-Z0-9_/:.\-%?&=#]+)"""")

internal fun extractMacCmsSources(
    html: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
): List<PlayableSource> {
    val match = MACCMS_PLAY_URL_REGEX.find(html) ?: return emptyList()
    val playListStr = match.groupValues[1].replace("\\/", "/")
    val entries = playListStr.split('#')
    val targetEpInt = epNumber.toInt()
    val results = mutableListOf<PlayableSource>()
    for (entry in entries) {
        val parts = entry.split('$')
        if (parts.size >= 2) {
            val title = parts[0].trim()
            val mediaUrl = parts[1].trim()
            val isMatch =
                if (epNumber > 0f) {
                    title.contains(targetEpInt.toString()) ||
                        title.contains("第${targetEpInt}集") ||
                        title.contains("第${targetEpInt}话")
                } else {
                    true
                }
            if (isMatch &&
                (mediaUrl.endsWith(".m3u8", ignoreCase = true) || mediaUrl.endsWith(".mp4", ignoreCase = true))
            ) {
                results.add(
                    PlayableSource(
                        url = mediaUrl,
                        kind = PlaylistEntryKind.DIRECT,
                        label = title,
                        episodeSort = epNumber,
                        siteName = siteName,
                        pageUrl = pageUrl,
                        headers = pageOrigin(pageUrl)?.let { mapOf("Referer" to it) } ?: emptyMap(),
                    ),
                )
            }
        }
    }
    return results
}

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

        // 强校验同源 Host，严禁跨域嗅探（防止误抓 Twitter / Telegram 等社交外链）
        val pageHost =
            pageUrl
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
        val fullHost =
            fullUrl
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
        if (pageHost != fullHost) continue

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
