package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.BgmHttpClient
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
                extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
            }.distinctBy { it.url }

    override suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String>,
        epNumber: Float,
        siteName: String,
    ): List<PlayableSource> {
        val fetched = pageFetchService.fetchHtml(url, headers) ?: return emptyList()
        // 用户配置的取源接口优先识别结构化流清单（兼容 Stremio /stream 形态），
        // 非清单响应回退到页面正则抽取
        parseStreamManifest(fetched.html, fetched.url, epNumber, siteName, headers)?.let { return it }
        return extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
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
    val path = url.substringBefore('?').substringBefore('#').replace(URL_FILE_EXTENSION, "")
    val afterScheme = path.substringAfter("://")
    val scopes = listOf(afterScheme.substringAfterLast('/'), afterScheme)
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
