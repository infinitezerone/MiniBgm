package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.PageFetchService

/** 单次请求解析的页面数上限，避免一次找源退化成批量抓取 */
private const val MAX_PAGES = 5

/** 单个页面最多采纳的候选来源数 */
private const val MAX_CANDIDATES_PER_PAGE = 12

private val MEDIA_URL_REGEX =
    Regex("""https?://[^"'()\s<>]+?\.(?:m3u8|mp4|mkv|flv|webm|ts)(?:\?[^"'()\s<>]*)?""", RegexOption.IGNORE_CASE)

private val MEDIA_TAG_REGEX =
    Regex("""<(?:source|video)\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val IFRAME_TAG_REGEX =
    Regex("""<iframe\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

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
                extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
            }.distinctBy { it.url }

    override suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String>,
        epNumber: Float,
        siteName: String,
    ): List<PlayableSource> {
        val fetched = pageFetchService.fetchHtml(url, headers) ?: return emptyList()
        return extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
            .distinctBy { it.url }
            .map { source -> if (headers.isEmpty()) source else source.copy(headers = source.headers + headers) }
    }
}

/**
 * 从页面正文抽取候选地址。
 *
 * 先还原 JSON 里被转义的 `\/` 与 HTML 实体 `&amp;`，否则绝大多数直链都匹配不到；
 * 相对地址按页面 origin 补全，Referer 取页面站点根，播放器起播时通常需要它。
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
    val label = if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else ""

    val direct: List<PlayableSource> =
        (
            MEDIA_URL_REGEX.findAll(normalized).map { it.value } +
                MEDIA_TAG_REGEX.findAll(normalized).map { it.groupValues[1] }
        ).mapNotNull { absoluteUrl(it, origin) }
            .map {
                PlayableSource(
                    url = it,
                    kind = PlaylistEntryKind.DIRECT,
                    label = label,
                    episodeSort = epNumber,
                    siteName = siteName,
                    pageUrl = pageUrl,
                    headers = headers,
                )
            }.toList()

    val pages: List<PlayableSource> =
        IFRAME_TAG_REGEX
            .findAll(normalized)
            .mapNotNull { absoluteUrl(it.groupValues[1], origin) }
            .filterNot { candidate -> direct.any { it.url == candidate } }
            .map {
                PlayableSource(
                    url = it,
                    kind = PlaylistEntryKind.PAGE,
                    label = label,
                    episodeSort = epNumber,
                    siteName = siteName,
                    pageUrl = pageUrl,
                    headers = headers,
                )
            }.toList()

    return (direct + pages).take(MAX_CANDIDATES_PER_PAGE)
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
