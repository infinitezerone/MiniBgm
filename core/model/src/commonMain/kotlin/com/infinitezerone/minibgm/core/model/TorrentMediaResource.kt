package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 分布式 P2P 网络（如 BT 种子 / 磁力链接 / RSS Feed）发布的动漫媒体资源条目。
 *
 * @property title 原始标题（含字幕组、集数、分辨率与封装格式）
 * @property fansubName 提取出的字幕组/发布者名称（如 "LoliHouse"、"ANi"）
 * @property episodeSort 提取出的集数序号（如 25f；全集/合集/SP 为 null）
 * @property resolution 提取出的清晰度规格（如 "1080P"、"720P"、"4K"）
 * @property magnetUrl 磁力下载链接（magnet:?xt=urn:btih:...）
 * @property torrentUrl 种子下载地址（.torrent 文件直链）
 * @property contentLength 资源文件体积字节数
 * @property formattedSize 人类可读的文件大小（如 "300.37 MB"、"9.0 GB"）
 * @property pubDate 发布时间字符串
 * @property infoHash 40 位十六进制 BT InfoHash
 */
@Serializable
data class TorrentMediaResource(
    val title: String,
    val fansubName: String = "",
    val episodeSort: Float? = null,
    val resolution: String = "",
    val magnetUrl: String = "",
    val torrentUrl: String = "",
    val contentLength: Long = 0L,
    val formattedSize: String = "",
    val pubDate: String = "",
    val infoHash: String = "",
) {
    /**
     * 资源去重键：多个规则源结果合并后按此键稳定去重。
     *
     * 只按 `infoHash` 判重时，未携带 40 位十六进制哈希的 `.torrent` 直链条目会集体退化为
     * 空串，`distinctBy` 只保留第一条，静默丢掉同一 Feed 的其余条目（分集/清晰度全丢）。
     * 因此按 信息哈希 → 磁力链接 → 种子直链 → 标题 逐级回退，逐级非空即用。
     */
    val dedupeKey: String
        get() =
            infoHash
                .trim()
                .ifBlank { magnetUrl.trim() }
                .ifBlank { torrentUrl.trim() }
                .ifBlank { title.trim() }

    /** 标准可用磁力链接：若 magnetUrl 为空但 infoHash 有效，自动合成标准磁力链接。 */
    val effectiveMagnetUrl: String
        get() =
            magnetUrl.ifBlank {
                if (infoHash.isNotBlank()) "magnet:?xt=urn:btih:${infoHash.lowercase()}" else ""
            }

    /** 是否具备磁力链接 */
    val hasMagnet: Boolean
        get() = effectiveMagnetUrl.isNotBlank()

    /** 是否具备种子直链 */
    val hasTorrent: Boolean
        get() = torrentUrl.isNotBlank()

    /** 是否同时具备种子直链与磁力链接（双源极速） */
    val isDualSource: Boolean
        get() = hasMagnet && hasTorrent

    /** 是否可用于边下边播/离线下载：至少要有磁力链接或种子直链，两者皆空时动作必须禁用。 */
    val hasPlayableSource: Boolean
        get() = hasMagnet || hasTorrent
}

/**
 * 将两个属于同一发布的媒体资源条目合并，双向补齐缺失字段（哈希、磁链、种子直链与体积互补）。
 */
fun TorrentMediaResource.mergeWith(other: TorrentMediaResource): TorrentMediaResource {
    val mergedHash = this.infoHash.ifBlank { other.infoHash }
    val mergedMagnet = this.magnetUrl.ifBlank { other.magnetUrl }
    val mergedTorrent = this.torrentUrl.ifBlank { other.torrentUrl }
    val mergedLength = if (this.contentLength > 0L) this.contentLength else other.contentLength
    val mergedSize = if (this.formattedSize.isNotBlank()) this.formattedSize else other.formattedSize
    val mergedPubDate = this.pubDate.ifBlank { other.pubDate }
    val mergedFansub = this.fansubName.ifBlank { other.fansubName }
    val mergedResolution = this.resolution.ifBlank { other.resolution }
    val mergedEpSort = this.episodeSort ?: other.episodeSort
    val mergedTitle = if (this.title.length >= other.title.length) this.title else other.title

    return this.copy(
        title = mergedTitle,
        fansubName = mergedFansub,
        episodeSort = mergedEpSort,
        resolution = mergedResolution,
        magnetUrl = mergedMagnet,
        torrentUrl = mergedTorrent,
        contentLength = mergedLength,
        formattedSize = mergedSize,
        pubDate = mergedPubDate,
        infoHash = mergedHash,
    )
}

/**
 * 归一化标题指纹：用于跨站点同源发布的模糊对齐（消除首尾空格、下划线、多余空格及尾缀扩展名差异）。
 */
fun normalizeReleaseTitle(title: String): String =
    title
        .trim()
        .lowercase()
        .replace('_', ' ')
        .replace(Regex("""\.(?:mp4|mkv|avi|torrent)$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

/**
 * 智能聚合与去重：
 * 1. 优先对有效 infoHash 相同的条目合并；
 * 2. 对无 hash 但归一化标题完全一致的跨站条目合并；
 * 3. 产生字段互补（磁力+种子直链+真实大小）的唯一条目列表。
 */
fun List<TorrentMediaResource>.mergeAndDeduplicate(): List<TorrentMediaResource> {
    if (size <= 1) return this

    val mergedList = mutableListOf<TorrentMediaResource>()

    for (item in this) {
        val existingIndex =
            mergedList.indexOfFirst { existing ->
                // 判据 1: infoHash 均非空且相同
                val hashMatch =
                    existing.infoHash.isNotBlank() &&
                        item.infoHash.isNotBlank() &&
                        existing.infoHash.equals(item.infoHash, ignoreCase = true)
                if (hashMatch) return@indexOfFirst true

                // 判据 2: 磁力链接均非空且相同
                val magnetMatch =
                    existing.magnetUrl.isNotBlank() &&
                        item.magnetUrl.isNotBlank() &&
                        existing.magnetUrl == item.magnetUrl
                if (magnetMatch) return@indexOfFirst true

                // 判据 3: 种子直链均非空且相同
                val torrentMatch =
                    existing.torrentUrl.isNotBlank() &&
                        item.torrentUrl.isNotBlank() &&
                        existing.torrentUrl == item.torrentUrl
                if (torrentMatch) return@indexOfFirst true

                // 判据 4: 标题指纹完全相同（跨站同源发布）
                val existingNorm = normalizeReleaseTitle(existing.title)
                val itemNorm = normalizeReleaseTitle(item.title)
                existingNorm.isNotBlank() && existingNorm == itemNorm
            }

        if (existingIndex >= 0) {
            val existing = mergedList[existingIndex]
            mergedList[existingIndex] = existing.mergeWith(item)
        } else {
            mergedList.add(item)
        }
    }

    return mergedList
}

/**
 * 从动漫发布标题提取通用的语言与特性标签（用于卡片徽章展示，避免单行截断导致的假重复）。
 */
fun extractReleaseTags(title: String): List<String> {
    if (title.isBlank()) return emptyList()
    val tags = mutableListOf<String>()

    // 语言与字幕标签
    when {
        Regex("""(?i)(?:简繁|chs[ _&/]?cht|sc[ _&/]?tc)""").containsMatchIn(title) -> tags.add("简繁")
        Regex("""(?i)(?:简日|chs[ _&/]?jpn?|双语)""").containsMatchIn(title) -> tags.add("双语")
        Regex("""(?i)(?:简[中体]|chs|gb|sc\b)""").containsMatchIn(title) -> tags.add("简中")
        Regex("""(?i)(?:繁[中体]|cht|big5|tc\b)""").containsMatchIn(title) -> tags.add("繁中")
    }

    if (Regex("""(?i)(?:内封|mkv)""").containsMatchIn(title)) {
        tags.add("内封")
    } else if (Regex("""(?i)(?:内嵌|mp4)""").containsMatchIn(title)) {
        tags.add("内嵌")
    }

    // 编码与画质
    if (Regex("""(?i)(?:hevc|x265)""").containsMatchIn(title)) {
        tags.add("HEVC")
    }
    if (Regex("""(?i)\b10bit\b""").containsMatchIn(title)) {
        tags.add("10bit")
    }
    if (Regex("""(?i)\bv2\b""").containsMatchIn(title)) {
        tags.add("v2修正")
    } else if (Regex("""(?i)\bv3\b""").containsMatchIn(title)) {
        tags.add("v3修正")
    }

    return tags.distinct()
}

/**
 * 格式化 RSS 发布时间为简洁友好的短时间字符串（如 "10-16 07:02"）。
 */
fun formatPubDateShort(rawDate: String): String {
    val trimmed = rawDate.trim()
    if (trimmed.isBlank()) return ""

    // ISO 8601: "2026-10-16T07:02:15Z" or "2026-10-16 07:02:15"
    val isoMatch = Regex("""\b\d{4}-(\d{2})-(\d{2})[T ](\d{2}:\d{2})""").find(trimmed)
    if (isoMatch != null) {
        val (month, day, time) = isoMatch.destructured
        return "$month-$day $time"
    }

    // RFC 822: "Thu, 16 Oct 2026 07:02:15 GMT" or "16 Oct 2026 07:02:15 +0800"
    val rfcMatch = Regex("""\b(\d{1,2})\s+([A-Za-z]{3})\s+\d{4}\s+(\d{2}:\d{2})""").find(trimmed)
    if (rfcMatch != null) {
        val (day, monthStr, time) = rfcMatch.destructured
        val monthNum =
            when (monthStr.lowercase()) {
                "jan" -> "01"
                "feb" -> "02"
                "mar" -> "03"
                "apr" -> "04"
                "may" -> "05"
                "jun" -> "06"
                "jul" -> "07"
                "aug" -> "08"
                "sep" -> "09"
                "oct" -> "10"
                "nov" -> "11"
                "dec" -> "12"
                else -> monthStr
            }
        val paddedDay = day.padStart(2, '0')
        return "$monthNum-$paddedDay $time"
    }

    return if (trimmed.length > 16) trimmed.take(16) else trimmed
}

/**
 * 从磁力链接提取 `infoHash`（`xt=urn:btih:` 段，遇 `&` 截止并去除首尾空白）。
 * 若为 32 位 Base32 编码（RFC 4648 / BitTorrent BEP 0009），自动解码为 40 位小写十六进制。
 *
 * **唯一真源**：引擎会话复用判据、仓库任务去重、种子缓存键与选源面板的既有任务比对都必须调用本函数。
 * 历史上这几处各写了一遍 `substringAfter("xt=urn:btih:")`，其中引擎侧还把"解析不出来"降级成
 * `"unknown_hash"` 哨兵字符串、其余处返回空串——同一语义两套降级，是"未知磁力互相误判为同一源"的温床。
 *
 * 解析不出（非磁力链接、缺 `xt` 段、`xt` 段为空）时统一返回**空串**，
 * 调用方一律以 `isBlank()` 判定"未知"，严禁再引入哨兵值。
 */
fun magnetInfoHash(magnetUrl: String): String {
    val raw =
        magnetUrl
            .substringAfter("xt=urn:btih:", "")
            .substringBefore("&")
            .trim()
    if (raw.isBlank()) return ""
    return normalizeInfoHash(raw)
}

/**
 * 将 32 位 Base32 或 40 位十六进制的 BTIH 归一化为 40 位小写十六进制字符串。
 */
fun normalizeInfoHash(rawHash: String): String {
    val trimmed = rawHash.trim()
    if (trimmed.length == 32) {
        base32ToHex(trimmed)?.let { return it }
    }
    return trimmed.lowercase()
}

internal fun base32ToHex(base32: String): String? {
    if (base32.length != 32) return null
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var buffer = 0L
    var bitsLeft = 0
    val bytes = ByteArray(20)
    var byteIndex = 0

    for (ch in base32.uppercase()) {
        val val5 = alphabet.indexOf(ch)
        if (val5 < 0) return null
        buffer = (buffer shl 5) or val5.toLong()
        bitsLeft += 5
        if (bitsLeft >= 8) {
            bitsLeft -= 8
            if (byteIndex < 20) {
                bytes[byteIndex++] = ((buffer shr bitsLeft) and 0xFFL).toByte()
            }
        }
    }
    if (byteIndex != 20) return null
    return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** 向后兼容别名，便于过渡期依赖平滑替换 */
typealias MikanTorrentResource = TorrentMediaResource
