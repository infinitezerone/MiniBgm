package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.DownloadStorageUsage
import com.infinitezerone.minibgm.core.model.TorrentMediaResource
import com.infinitezerone.minibgm.core.model.magnetInfoHash
import com.infinitezerone.minibgm.core.model.normalizeInfoHash

/**
 * 通用动漫 BT 种子 / RSS XML 纯 Kotlin 解析器。
 *
 * 遵循 Fail-Open 架构原则：
 * - 面对非标准/残缺 XML 绝不抛异常崩溃，降级为返回空列表或跳过问题条目；
 * - 纯 Kotlin 字符串处理，无平台依赖，完全兼容 Android / JVM / iOS (KMP)；
 * - 提取字幕组名称、集数（单集 vs 合集）、分辨率、BT InfoHash 与磁力链。
 */
object TorrentRssParser {
    private val ITEM_REGEX = Regex("""<item>([\s\S]*?)</item>""", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("""<title>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val LINK_REGEX = Regex("""<link>([\s\S]*?)</link>""", RegexOption.IGNORE_CASE)
    private val DESCRIPTION_REGEX = Regex("""<description>([\s\S]*?)</description>""", RegexOption.IGNORE_CASE)
    private val CONTENT_LENGTH_REGEX =
        Regex("""<(?:\w+:)?(?:contentLength|size)>([^<]+)</(?:\w+:)?(?:contentLength|size)>""", RegexOption.IGNORE_CASE)
    private val INFO_HASH_TAG_REGEX =
        Regex("""<(?:\w+:)?infoHash>([^<]+)</(?:\w+:)?infoHash>""", RegexOption.IGNORE_CASE)
    private val PUB_DATE_REGEX = Regex("""<pubDate>([\s\S]*?)</pubDate>""", RegexOption.IGNORE_CASE)
    private val ENCLOSURE_URL_REGEX = Regex("""<enclosure[^>]+url=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ENCLOSURE_LEN_REGEX = Regex("""<enclosure[^>]+length=["'](\d+)["']""", RegexOption.IGNORE_CASE)

    private val HASH_REGEX = Regex("""(?:/Episode/|/)([0-9a-fA-F]{40})(?:\.torrent|/|$)""")
    private val FANSUB_REGEX = Regex("""^\s*[\[【]([^\]】]+)[\]】]""")
    private val SIZE_TEXT_REGEX =
        Regex("""\b(\d+(?:\.\d+)?\s*(?:GiB|MiB|KiB|GB|MB|KB|Bytes))\b""", RegexOption.IGNORE_CASE)

    private val BATCH_KEYWORDS = listOf("合集", "全集", "Fin]", "Fin)", "【Fin】", "[Fin]")
    private val BATCH_EP_RANGE_REGEX =
        Regex("""(?i)(?:\[\s*(?:ep)?\s*\d{1,3}\s*[-~至到]\s*\d{1,3}\s*\]|\b(?:ep|e)\s*\d{1,3}\s*[-~至到]\s*\d{1,3}\b|0\d\s*[-~至到]\s*\d{1,3})""")

    /** 影视媒体文件最小有效体积阈值（<= 1KB 视为 RSS 占位伪长度，如 DMHY 的 length="1"） */
    private const val MIN_VALID_MEDIA_BYTES = 1024L

    /**
     * 解析任意标准动漫 BT RSS XML 文本为 [TorrentMediaResource] 列表。
     */
    fun parse(
        xmlContent: String,
        fallbackHost: String = "",
    ): List<TorrentMediaResource> {
        val trimmed = xmlContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<TorrentMediaResource>()
        for (match in ITEM_REGEX.findAll(trimmed)) {
            val itemBlock = match.groupValues[1]
            val rawTitle = extractTagText(TITLE_REGEX, itemBlock)
            if (rawTitle.isBlank()) continue

            val title = unescapeXml(rawTitle)
            // Feed 的 link / enclosure 普遍把 & 转义成 &amp;，不还原则磁链自带的
            // Tracker 声明（&amp;tr=...）会被下游当作无效参数丢弃，种子直链也会带着
            // &amp; 去请求而必然取不到。
            val link = unescapeXml(extractTagText(LINK_REGEX, itemBlock))
            val enclosureUrl =
                unescapeXml(
                    ENCLOSURE_URL_REGEX
                        .find(itemBlock)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty(),
                )

            val tagHash = extractTagText(INFO_HASH_TAG_REGEX, itemBlock)
            val infoHash =
                tagHash
                    .ifBlank {
                        magnetInfoHash(link)
                            .ifBlank { magnetInfoHash(enclosureUrl) }
                            .ifBlank { extractHash(link) }
                            .ifBlank { extractHash(enclosureUrl) }
                    }.let(::normalizeInfoHash)

            // 合成磁链只保留 infohash：严禁注入硬编码公共 Tracker（会把本机 IP 与
            // infohash 广播给未知第三方，且域名存活期腐烂）。寻道交给 DHT，Feed 自带
            // 磁链则原样透传其自身声明的 Tracker。
            val magnetUrl =
                when {
                    link.startsWith("magnet:?", ignoreCase = true) -> link
                    enclosureUrl.startsWith("magnet:?", ignoreCase = true) -> enclosureUrl
                    infoHash.isNotBlank() -> "magnet:?xt=urn:btih:${infoHash.lowercase()}"
                    else -> ""
                }

            val torrentUrl =
                when {
                    enclosureUrl.endsWith(".torrent", ignoreCase = true) -> enclosureUrl
                    link.endsWith(".torrent", ignoreCase = true) -> link
                    enclosureUrl.isNotBlank() && !enclosureUrl.startsWith("magnet:?", ignoreCase = true) -> enclosureUrl
                    infoHash.isNotBlank() && fallbackHost.isNotBlank() -> "https://$fallbackHost/Download/$infoHash.torrent"
                    else -> ""
                }

            val rawSizeTag = extractTagText(CONTENT_LENGTH_REGEX, itemBlock)
            val enclosureLen =
                ENCLOSURE_LEN_REGEX
                    .find(itemBlock)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?: 0L

            var rawLen = 0L
            var formattedSize = ""

            if (rawSizeTag.isNotBlank()) {
                if (rawSizeTag.all { it.isDigit() }) {
                    val bytes = rawSizeTag.toLongOrNull() ?: 0L
                    if (bytes > MIN_VALID_MEDIA_BYTES) {
                        rawLen = bytes
                        formattedSize = formatFileSize(rawLen)
                    }
                } else {
                    formattedSize = rawSizeTag
                    rawLen = parseSizeToBytes(rawSizeTag)
                }
            }

            if (rawLen <= MIN_VALID_MEDIA_BYTES && enclosureLen > MIN_VALID_MEDIA_BYTES) {
                rawLen = enclosureLen
                if (formattedSize.isBlank()) {
                    formattedSize = formatFileSize(rawLen)
                }
            }

            val description = extractTagText(DESCRIPTION_REGEX, itemBlock)
            if (formattedSize.isBlank()) {
                val descSize = extractFormattedSize(description)
                if (descSize.isNotBlank()) {
                    formattedSize = descSize
                    rawLen = parseSizeToBytes(descSize)
                }
            }

            val pubDate = extractTagText(PUB_DATE_REGEX, itemBlock)
            val fansubName = extractFansub(title)
            val resolution = extractResolution(title)
            val episodeSort = extractEpisodeSort(title)

            results.add(
                TorrentMediaResource(
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
    fun extractEpisodeSort(title: String): Float? {
        if (BATCH_KEYWORDS.any { title.contains(it) }) return null
        if (BATCH_EP_RANGE_REGEX.containsMatchIn(title)) return null

        // 形式 1: " - 25 " / " - 09v2"
        val dashMatch = Regex("""(?i)\s+-\s+(\d{1,4}(?:\.\d)?)(?:v\d+)?(?:\s+|\[|\(|$)""").find(title)
        if (dashMatch != null) {
            dashMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // 形式 2: "第01话" / "第 25 集" / "第12.5話"
        val cnMatch = Regex("""第\s*(\d+(?:\.\d+)?)\s*[话集話]""").find(title)
        if (cnMatch != null) {
            cnMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // 形式 3: "S01E03" / "S2E04" / "EP25" / "ep 08" / "E03"
        val sEpMatch = Regex("""(?i)(?:\bs\d{1,2}\s*e|\b(?:ep|e)\s*\.?\s*)(\d{1,4}(?:\.\d)?)(?:v\d+)?\b""").find(title)
        if (sEpMatch != null) {
            sEpMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // 形式 4: "[25]" / "【09】" / "[03v2]" (排除分辨率与编码 720/1080/2160/264/265 等)
        val bracketMatches = Regex("""(?i)[\[\(【]\s*(\d{1,3}(?:\.\d)?)(?:v\d+)?\s*[\]\)】]""").findAll(title)
        for (match in bracketMatches) {
            val num = match.groupValues[1].toFloatOrNull()
            if (num != null && num !in listOf(720f, 1080f, 2160f, 540f, 480f, 264f, 265f)) {
                return num
            }
        }

        return null
    }

    private fun extractFormattedSize(text: String): String =
        SIZE_TEXT_REGEX
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()

    internal fun parseSizeToBytes(sizeStr: String): Long {
        val match = Regex("""^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)$""").find(sizeStr.trim()) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val multiplier =
            when (match.groupValues[2].uppercase()) {
                "GIB", "GB" -> 1024L * 1024L * 1024L
                "MIB", "MB" -> 1024L * 1024L
                "KIB", "KB" -> 1024L
                else -> 1L
            }
        return (value * multiplier).toLong()
    }

    /**
     * 体积展示格式化：与 [DownloadStorageUsage.formatBytes] 共用唯一真源。
     * 保持"未知（<= MIN_VALID_MEDIA_BYTES）返回空串"语义：`formattedSize` 以空串
     * 表示 Feed 未声明有效体积，下游据此回退到估算值，严禁把占位符（如 length="1"）格式化成 "0.0 KB" 或 "0 B"。
     */
    private fun formatFileSize(bytes: Long): String = if (bytes <= MIN_VALID_MEDIA_BYTES) "" else DownloadStorageUsage.formatBytes(bytes)

    private fun unescapeXml(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
}
