package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 蜜柑计划（Mikan Project）发布的动漫 BT 种子/磁力资源条目。
 *
 * @property title 原始标题（含字幕组、集数、分辨率与封装格式）
 * @property fansubName 提取出的字幕组名称（如 "LoliHouse"、"ANi"）
 * @property episodeSort 提取出的集数序号（如 25f；全集/合集/SP 为 null）
 * @property resolution 提取出的清晰度规格（如 "1080P"、"720P"、"4K"）
 * @property magnetUrl 磁力下载链接（magnet:?xt=urn:btih:...）
 * @property torrentUrl 种子下载地址（.torrent 文件直链）
 * @property contentLength 资源文件体积字节数
 * @property formattedSize 人类可读的文件大小（如 "300.37 MB"、"9.0 GB"）
 * @property pubDate 发布时间 ISO 字符串
 * @property infoHash 40 位十六进制 BT InfoHash
 */
@Serializable
data class MikanTorrentResource(
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
)
