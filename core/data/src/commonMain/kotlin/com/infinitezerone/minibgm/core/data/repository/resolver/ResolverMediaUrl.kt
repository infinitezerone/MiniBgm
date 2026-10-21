package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.data.repository.decodeBase64String
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

/** 流清单条目：Stremio `/stream` 响应的兼容子集——url 必填，title/name/headers 可选，未知字段忽略 */
@Serializable
internal data class StreamManifestEntry(
    val url: String = "",
    val title: String = "",
    val name: String = "",
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
            val labelText =
                buildString {
                    if (entry.name.isNotBlank()) {
                        append(entry.name.replace("\n", " ").trim())
                    }
                    if (entry.title.isNotBlank()) {
                        if (isNotEmpty()) append(" - ")
                        append(entry.title.replace("\n", " ").trim())
                    }
                }.ifBlank {
                    if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else ""
                }
            PlayableSource(
                url = candidateUrl,
                kind = PlaylistEntryKind.DIRECT,
                label = labelText,
                episodeSort = epNumber,
                siteName = siteName,
                pageUrl = pageUrl,
                headers = entry.headers + ruleHeaders,
            )
        }.distinctBy { it.url }
        .take(MAX_MANIFEST_ENTRIES)
}

private val PLAYER_CONFIG_REGEX =
    Regex("""(?:player_aaaa|player_data)\s*=\s*(\{[^;]+?\})""", RegexOption.IGNORE_CASE)

private val PLAYER_URL_FIELD_REGEX =
    Regex(""""url"\s*:\s*"([^"]+)"""")

private val PLAYER_ENCRYPT_FIELD_REGEX =
    Regex(""""encrypt"\s*:\s*([0-9]+)""")

private val DPLAYER_URL_REGEX =
    Regex("""(?:video|player)\s*:\s*\{[^}]*?url\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val IFRAME_MEDIA_PARAM_REGEX =
    Regex("""[?&](?:url|v|vid|source|play)=([^&"'>\s]+)""", RegexOption.IGNORE_CASE)

private fun safeUrlDecode(input: String): String =
    runCatching {
        buildString {
            var i = 0
            while (i < input.length) {
                if (input[i] == '%' && i + 2 < input.length) {
                    val hex = input.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        append(code.toChar())
                        i += 3
                        continue
                    }
                }
                append(input[i])
                i++
            }
        }
    }.getOrDefault(input)

internal fun extractEmbeddedPlayerUrls(html: String): List<String> {
    val results = mutableListOf<String>()

    // 1. 匹配 DPlayer / Artplayer 等常见播放器 video: { url: "..." }
    DPLAYER_URL_REGEX.findAll(html).forEach { match ->
        val candidate = match.groupValues[1].trim()
        if (isPlayableMediaUrl(candidate) ||
            candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        ) {
            results.add(candidate)
        }
    }

    // 2. 匹配 MacCMS / 苹果CMS 等内嵌的 player_aaaa / player_data 配置对象
    PLAYER_CONFIG_REGEX.findAll(html).forEach { match ->
        val jsonStr = match.groupValues[1]
        val rawUrl =
            PLAYER_URL_FIELD_REGEX
                .find(jsonStr)
                ?.groupValues
                ?.get(1)
                ?.trim()
        val encrypt =
            PLAYER_ENCRYPT_FIELD_REGEX
                .find(jsonStr)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        if (!rawUrl.isNullOrBlank()) {
            val decodedUrl =
                when (encrypt) {
                    1 -> decodeBase64String(rawUrl).trim()
                    2 -> safeUrlDecode(rawUrl).trim()
                    else -> rawUrl
                }
            if (decodedUrl.isNotBlank() &&
                (
                    isPlayableMediaUrl(decodedUrl) ||
                        decodedUrl.startsWith("http://", ignoreCase = true) ||
                        decodedUrl.startsWith("https://", ignoreCase = true)
                )
            ) {
                results.add(decodedUrl)
            }
        }
    }

    return results
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

    val rawIframeUrls =
        IFRAME_TAG_REGEX
            .findAll(normalized)
            .mapNotNull { absoluteUrl(it.groupValues[1], origin) }
            .toList()

    // 从 iframe 参数（如 player.php?url=https://...m3u8）中直接提取内嵌直链
    val iframeDirectUrls =
        rawIframeUrls.mapNotNull { iframeUrl ->
            val match = IFRAME_MEDIA_PARAM_REGEX.find(iframeUrl) ?: return@mapNotNull null
            val rawParam = safeUrlDecode(match.groupValues[1].trim())
            if (isPlayableMediaUrl(rawParam) ||
                (rawParam.startsWith("http", ignoreCase = true) && (rawParam.contains(".m3u8") || rawParam.contains(".mp4")))
            ) {
                rawParam
            } else {
                null
            }
        }

    val directCandidates =
        (
            MEDIA_URL_REGEX.findAll(normalized).map { it.value } +
                MEDIA_TAG_REGEX.findAll(normalized).map { it.groupValues[1] } +
                extractEmbeddedPlayerUrls(normalized) +
                iframeDirectUrls
        ).mapNotNull { absoluteUrl(it, origin) }
            .filterNot { isInvalidMediaHost(it) }
            .distinct()
            .toList()

    val direct: List<PlayableSource> =
        directCandidates
            .map { buildSource(it, PlaylistEntryKind.DIRECT) }

    val pages: List<PlayableSource> =
        rawIframeUrls
            .filterNot { iframeUrl ->
                direct.any { it.url == iframeUrl } || iframeDirectUrls.any { iframeUrl.contains(it) }
            }.map { buildSource(it, PlaylistEntryKind.PAGE) }

    return (direct + pages).take(MAX_CANDIDATES_PER_PAGE)
}
