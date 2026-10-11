package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 由用户输入推导待探测的标准 MacCMS 接口地址。
 *
 * 用户可能贴 `example.com`、`https://example.com`、甚至一整条规则模板——一律只取主机名，
 * 再按 MacCMS V10 的约定路径拼候选。https 优先、明文 http 回退（不少采集站只有 http，
 * 本应用已显式放开明文）。
 */
internal fun macCmsProbeCandidates(input: String): List<String> {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return emptyList()
    val withScheme =
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    val rest = withScheme.substringAfter("://", "")
    val host =
        rest
            .substringBefore('/')
            .substringBefore('?')
            .trim()
            .trimEnd('.')
    if (host.isBlank()) return emptyList()

    val preferHttps = withScheme.startsWith("https://", ignoreCase = true)
    val schemes = if (preferHttps) listOf("https", "http") else listOf("http", "https")
    return schemes.map { "$it://$host/api.php/provide/vod/?ac=list" }
}

/**
 * 判定响应体是否是 MacCMS 列表响应，并返回条目数；不是则返回 null。
 *
 * 只认 `{"list":[…]}` 这一结构（MacCMS V10 的固定约定）。**空数组也算命中**——
 * 接口存在但当前没内容是常态，跟"这里没有接口"是两回事，不能混为一谈。
 */
internal fun countMacCmsListItems(body: String): Int? {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.first() != '{') return null
    val root = runCatching { BgmHttpClient.jsonConfig.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject ?: return null
    return (root["list"] as? JsonArray)?.size
}

private val MACCMS_PLAY_URL_REGEX =
    Regex(""""(?:vod_play_url|url)"\s*:\s*"([^"]*\$[a-zA-Z0-9_/:.\-%?&=#]+)"""")

private val MACCMS_VOD_NAME_REGEX =
    Regex(""""vod_name"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

/**
 * 把 MacCMS 响应按 `vod_name` 切成逐条目片段。
 *
 * 搜索接口一次返回多部片子（同名续作、剧场版、不同季），只认第一段必然出现"播错番"——
 * 与列表页下探时校验片名归属是同一类问题。没有 `vod_name` 的响应
 * （播放页内嵌串、单条目详情接口）整体作为唯一一段，行为与从前一致。
 */
internal fun splitMacCmsEntries(html: String): List<String> {
    val names = MACCMS_VOD_NAME_REGEX.findAll(html).toList()
    if (names.isEmpty()) return listOf(html)
    return names.mapIndexed { index, match ->
        val start = match.range.first
        val end = names.getOrNull(index + 1)?.range?.first ?: html.length
        html.substring(start, end)
    }
}

private fun segmentVodName(segment: String): String? = MACCMS_VOD_NAME_REGEX.find(segment)?.groupValues?.get(1)

/**
 * 抽取 MacCMS 剧集直链。
 *
 * - 多条目响应优先取 [title] 对得上且通过季数/版本断言的那一段（同名续作、剧场版混在搜索结果里时避免播错番）；
 * - 当指定了 [title] 时，只有满足标题与季数兼容的条目才会被采用；杜绝无条件回退第一段导致的盲目串季；
 * - 季数拓扑纠偏：若分集连续编号（如第二季标为 13..24），自动纠偏为对应集；
 * - 分隔符：`$$$` 分线路、`#` 分集、`集名$地址`；支持多线路标注与展开，避免首线异常全盘判死。
 */
internal fun extractMacCmsSources(
    html: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
    title: String = "",
): List<PlayableSource> {
    // HTML 内嵌 JSON 会把 & 转义成 &amp;，不还原则直链带着 &amp; 去播放必然取不到流
    val normalized = html.replace("\\/", "/").replace("&amp;", "&")
    val segments = splitMacCmsEntries(normalized)
    val matchedSegment =
        if (title.isNotBlank()) {
            segments.firstOrNull { entry ->
                val vodName = segmentVodName(entry)
                if (vodName != null) {
                    titleMatches(vodName, title) && EpisodeSanityChecker.isSeasonAndEditionCompatible(title, vodName)
                } else {
                    false
                }
            } ?: if (segments.size == 1) {
                // 单条目响应（单番详情接口或只有一个结果）：
                // 仅当 vodName 为空，或通过季数/版本断言时采纳
                val singleVodName = segmentVodName(segments.first())
                if (singleVodName == null || EpisodeSanityChecker.isSeasonAndEditionCompatible(title, singleVodName)) {
                    segments.first()
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            segments.firstOrNull()
        } ?: return emptyList()

    val vodTitle = segmentVodName(matchedSegment).orEmpty()
    val referer = pageOrigin(pageUrl)?.let { mapOf("Referer" to it) }.orEmpty()
    val results = mutableListOf<PlayableSource>()

    MACCMS_PLAY_URL_REGEX.find(matchedSegment)?.let { match ->
        val playListStr = match.groupValues[1].replace("\\/", "/")
        val routes = playListStr.split("$$$")

        routes.forEachIndexed { routeIndex, routeContent ->
            val routeEntries =
                routeContent.split('#').mapNotNull { raw ->
                    val parts = raw.split('$', limit = 2)
                    if (parts.size >= 2) {
                        val entryLabel = parts[0].trim()
                        val mediaUrl = parts[1].trim()
                        if (isPlayableMediaUrl(mediaUrl)) {
                            entryLabel to mediaUrl
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            if (routeEntries.isEmpty()) return@forEachIndexed

            // 收集当前线路的所有可用集号以进行拓扑判定
            val availableSorts =
                routeEntries.mapNotNull { (label, url) ->
                    episodeNumberFromLabel(label) ?: episodeNumberFromUrl(url, allowWeak = true)
                }
            val targetEp =
                if (epNumber > 0f) {
                    EpisodeSanityChecker.normalizeEpisodeIndex(epNumber, availableSorts) ?: epNumber
                } else {
                    epNumber
                }
            val targetEpInt = targetEp.toInt()
            val paddedEp = targetEpInt.toString().padStart(2, '0')

            val routeLabel = if (routes.size > 1) " · 线路${routeIndex + 1}" else ""
            val finalSiteName = if (siteName.isNotBlank()) "$siteName$routeLabel" else siteName

            for ((entryLabel, mediaUrl) in routeEntries) {
                if (epNumber > 0f && !entryMatchesEpisode(entryLabel, mediaUrl, targetEpInt, paddedEp)) continue
                results.add(
                    PlayableSource(
                        url = mediaUrl,
                        kind = PlaylistEntryKind.DIRECT,
                        label = entryLabel,
                        episodeSort = epNumber,
                        siteName = finalSiteName,
                        pageUrl = pageUrl,
                        headers = referer,
                        matchedVodName = vodTitle,
                    ),
                )
            }
        }
    }
    return results.distinctBy { it.url }.take(MAX_MANIFEST_ENTRIES)
}

private fun entryMatchesEpisode(
    entryLabel: String,
    mediaUrl: String,
    targetEpInt: Int,
    paddedEp: String,
): Boolean {
    if (entryLabel.isBlank()) {
        // 采集串偶尔不给集名（形如 `$http://…`），这时只能用地址里的集号定位
        return episodeNumberFromUrl(mediaUrl, allowWeak = true)?.toInt() == targetEpInt
    }
    return entryLabel.contains("第${targetEpInt}集") ||
        entryLabel.contains("第${paddedEp}集") ||
        entryLabel.contains("第${targetEpInt}话") ||
        entryLabel.contains("第${paddedEp}话") ||
        entryLabel.contains("[$targetEpInt]") ||
        entryLabel.contains("[$paddedEp]") ||
        entryLabel == targetEpInt.toString() ||
        entryLabel == paddedEp ||
        Regex("""\b(?:ep|e)?\s*0*$targetEpInt\b""", RegexOption.IGNORE_CASE).containsMatchIn(entryLabel)
}
