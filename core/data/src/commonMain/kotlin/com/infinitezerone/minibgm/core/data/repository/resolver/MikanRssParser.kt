package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.MikanTorrentResource

/**
 * 蜜柑计划（Mikan Project）公开 RSS XML 纯 Kotlin 解析器。
 *
 * 遵循 Fail-Open 架构原则：
 * - 面对非标准/残缺 XML 绝不抛异常崩溃，降级为返回空列表或跳过问题条目；
 * - 纯 Kotlin 字符串处理，无平台依赖，完全兼容 Android / JVM / iOS (KMP)；
 * - 提取字幕组名称、集数（单集 vs 合集）、分辨率、BT InfoHash 与磁力链。
 */
object MikanRssParser {
    private val ITEM_REGEX = Regex("""<item>([\s\S]*?)</item>""", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("""<title>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val LINK_REGEX = Regex("""<link>([\s\S]*?)</link>""", RegexOption.IGNORE_CASE)
    private val DESCRIPTION_REGEX = Regex("""<description>([\s\S]*?)</description>""", RegexOption.IGNORE_CASE)
    private val CONTENT_LENGTH_REGEX = Regex("""<contentLength>(\d+)</contentLength>""", RegexOption.IGNORE_CASE)
    private val PUB_DATE_REGEX = Regex("""<pubDate>([\s\S]*?)</pubDate>""", RegexOption.IGNORE_CASE)
    private val ENCLOSURE_URL_REGEX = Regex("""<enclosure[^>]+url=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ENCLOSURE_LEN_REGEX = Regex("""<enclosure[^>]+length=["'](\d+)["']""", RegexOption.IGNORE_CASE)

    private val HASH_REGEX = Regex("""(?:/Episode/|/)([0-9a-fA-F]{40})(?:\.torrent|/|$)""")
    private val FANSUB_REGEX = Regex("""^\s*[\[【]([^\]】]+)[\]】]""")
    private val SIZE_FROM_DESC_REGEX = Regex("""\[(\d+(?:\.\d+)?\s*(?:[KMG]B|Bytes|gb|mb|kb))\]$""")

    private val BATCH_KEYWORDS = listOf("合集", "全集", "Fin]", "Fin)", "【Fin】", "[Fin]")
    private val BATCH_EP_RANGE_REGEX =
        Regex("""(?i)(?:\[\s*(?:ep)?\s*\d{1,3}\s*[-~至到]\s*\d{1,3}\s*\]|\b(?:ep|e)\s*\d{1,3}\s*[-~至到]\s*\d{1,3}\b|0\d\s*[-~至到]\s*\d{1,3})""")

    private const val TRACKERS =
        "&tr=http%3A%2F%2Ftr.cili001.com%3A8070%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce"

    /**
     * 解析 Mikan RSS XML 文本为 [MikanTorrentResource] 列表。
     */
    fun parse(xmlContent: String): List<MikanTorrentResource> {
        val trimmed = xmlContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<MikanTorrentResource>()
        for (match in ITEM_REGEX.findAll(trimmed)) {
            val itemBlock = match.groupValues[1]
            val rawTitle = extractTagText(TITLE_REGEX, itemBlock)
            if (rawTitle.isBlank()) continue

            val title = unescapeXml(rawTitle)
            val link = extractTagText(LINK_REGEX, itemBlock)
            val enclosureUrl =
                ENCLOSURE_URL_REGEX
                    .find(itemBlock)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()

            val infoHash = extractHash(link).ifBlank { extractHash(enclosureUrl) }
            val magnetUrl =
                if (infoHash.isNotBlank()) {
                    "magnet:?xt=urn:btih:${infoHash.lowercase()}$TRACKERS"
                } else {
                    ""
                }

            val torrentUrl =
                enclosureUrl.ifBlank {
                    if (infoHash.isNotBlank()) "https://mikanani.me/Download/$infoHash.torrent" else ""
                }

            val rawLen =
                CONTENT_LENGTH_REGEX
                    .find(itemBlock)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?: ENCLOSURE_LEN_REGEX
                        .find(itemBlock)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull()
                    ?: 0L

            val description = extractTagText(DESCRIPTION_REGEX, itemBlock)
            val formattedSize =
                extractFormattedSize(description).ifBlank { formatFileSize(rawLen) }

            val pubDate = extractTagText(PUB_DATE_REGEX, itemBlock)
            val fansubName = extractFansub(title)
            val resolution = extractResolution(title)
            val episodeSort = extractEpisodeSort(title)

            results.add(
                MikanTorrentResource(
                    title = title,
                    fansubName = fansubName,
                    episodeSort = episodeSort,
                    resolution = resolution,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    contentLength = rawLen,
                    formattedSize = formattedSize,
                    pubDate = pubDate,
                    infoHash = infoHash,
                ),
            )
        }
        return results
    }

    private fun extractTagText(
        regex: Regex,
        block: String,
    ): String {
        val raw =
            regex
                .find(block)
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()
        return if (raw.startsWith("<![CDATA[") && raw.endsWith("]]>")) {
            raw.removeSurrounding("<![CDATA[", "]]>").trim()
        } else {
            raw
        }
    }

    private fun extractHash(text: String): String =
        HASH_REGEX
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            .orEmpty()

    private fun extractFansub(title: String): String =
        FANSUB_REGEX
            .find(title)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()

    private fun extractResolution(title: String): String =
        when {
            Regex("""(?i)\b(?:4k|2160p)\b""").containsMatchIn(title) -> "4K"
            Regex("""(?i)(?:1080p|1920x1080)""").containsMatchIn(title) -> "1080P"
            Regex("""(?i)(?:720p|1280x720)""").containsMatchIn(title) -> "720P"
            Regex("""(?i)(?:540p|960x540)""").containsMatchIn(title) -> "540P"
            else -> ""
        }

    /**
     * 从标题提取单集集数。合集/全集/季度大包返回 null。
     */
    private fun extractEpisodeSort(title: String): Float? {
        if (BATCH_KEYWORDS.any { title.contains(it) }) return null
        if (BATCH_EP_RANGE_REGEX.containsMatchIn(title)) return null

        // 形式 1: " - 25 " 或 " - 09"
        val dashMatch = Regex("""\s+-\s+(\d{1,4}(?:\.\d)?)(?:\s+|\[|\(|$)""").find(title)
        if (dashMatch != null) {
            dashMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // 形式 2: "第01话" / "第 25 集" / "第12.5話"
        val cnMatch = Regex("""第\s*(\d+(?:\.\d+)?)\s*[话集話]""").find(title)
        if (cnMatch != null) {
            cnMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // 形式 3: "[25]" / "【09】" (排除分辨率 720/1080/2160 等)
        val bracketMatch = Regex("""[\[\(【]\s*(\d{1,3}(?:\.\d)?)\s*[\]\)】]""").find(title)
        if (bracketMatch != null) {
            val num = bracketMatch.groupValues[1].toFloatOrNull()
            if (num != null && num !in listOf(720f, 1080f, 2160f, 540f, 480f)) {
                return num
            }
        }

        // 形式 4: "EP25" / "ep 08" / "E03"
        val epMatch = Regex("""(?i)\b(?:ep|e)\s*\.?\s*(\d{1,4}(?:\.\d)?)\b""").find(title)
        if (epMatch != null) {
            epMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        return null
    }

    private fun extractFormattedSize(description: String): String =
        SIZE_FROM_DESC_REGEX
            .find(description)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) {
            val rounded = (gb * 100.0).toLong() / 100.0
            return "$rounded GB"
        }
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        val rounded = (mb * 100.0).toLong() / 100.0
        return "$rounded MB"
    }

    private fun unescapeXml(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
}
