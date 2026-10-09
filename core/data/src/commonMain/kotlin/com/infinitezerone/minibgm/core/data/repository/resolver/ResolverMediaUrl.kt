package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.serialization.Serializable

/** 单个页面最多采纳的候选来源数 */
private const val MAX_CANDIDATES_PER_PAGE = 12

/** 单份流清单最多采纳的条目数（用户配置的接口可整季返回，比页面抓取宽） */
internal const val MAX_MANIFEST_ENTRIES = 50

private val MEDIA_URL_REGEX =
    Regex("""https?://[^"'()\s<>]+?\.(?:m3u8|mp4|mkv|flv|webm|ts|mpd)(?:\?[^"'()\s<>]*)?""", RegexOption.IGNORE_CASE)

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

internal val URL_FILE_EXTENSION = Regex("""\.(m3u8|mp4|mkv|flv|webm|ts|mpd)$""", RegexOption.IGNORE_CASE)
internal val URL_NUMBER_TOKEN = Regex("""\d+(?:\.\d+)?""")
internal val URL_YEAR_TOKEN = Regex("""^(?:19|20)\d{2}$""")
internal val NOISE_NUMBERS = setOf("1080", "720", "480", "360", "240", "2160", "1440", "4320", "60")

internal fun isPlayableMediaUrl(url: String): Boolean = URL_FILE_EXTENSION.containsMatchIn(url.substringBefore('#').substringBefore('?'))

internal val NOISE_NUMBERS_FLOAT: Set<Float> = NOISE_NUMBERS.mapNotNull { it.toFloatOrNull() }.toSet()

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
