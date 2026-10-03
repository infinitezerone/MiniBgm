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
 * - 多条目响应优先取 [title] 对得上的那一段（同名续作、剧场版混在搜索结果里时避免播错番）；
 *   **一段都对不上时退回原来"取第一段"的行为**——源站 `vod_name` 可能是日文原名而 App 侧是中文译名，
 *   硬拦会把本来能用的源判死，这里只做"能认出来就用对的"，不做"认不出就拒绝"；
 * - 分隔符：`$$$` 分线路、`#` 分集、`集名$地址`；集名为空时用地址里的集号定位
 *   （从地址真实抽取，不按话数猜号）；
 * - 判定扩展名前先剥 query 与 fragment，否则 `x.m3u8?sign=…` 这类带签名直链会被整条丢掉。
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
    val segment =
        segments.firstOrNull { entry -> segmentVodName(entry)?.let { titleMatches(it, title) } ?: true }
            ?: segments.firstOrNull()
            ?: return emptyList()

    val targetEpInt = epNumber.toInt()
    val paddedEp = targetEpInt.toString().padStart(2, '0')
    val referer = pageOrigin(pageUrl)?.let { mapOf("Referer" to it) }.orEmpty()
    val results = mutableListOf<PlayableSource>()
    MACCMS_PLAY_URL_REGEX.find(segment)?.let { match ->
        val playListStr = match.groupValues[1].replace("\\/", "/")
        for (entry in playListStr.split("$$$").flatMap { it.split('#') }) {
            val parts = entry.split('$', limit = 2)
            if (parts.size < 2) continue
            val entryLabel = parts[0].trim()
            val mediaUrl = parts[1].trim()
            if (!isPlayableMediaUrl(mediaUrl)) continue
            if (epNumber > 0f && !entryMatchesEpisode(entryLabel, mediaUrl, targetEpInt, paddedEp)) continue
            results.add(
                PlayableSource(
                    url = mediaUrl,
                    kind = PlaylistEntryKind.DIRECT,
                    label = entryLabel,
                    episodeSort = epNumber,
                    siteName = siteName,
                    pageUrl = pageUrl,
                    headers = referer,
                ),
            )
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
