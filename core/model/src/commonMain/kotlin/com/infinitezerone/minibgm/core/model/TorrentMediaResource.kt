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

    /** 是否可用于边下边播/离线下载：至少要有磁力链接或种子直链，两者皆空时动作必须禁用。 */
    val hasPlayableSource: Boolean
        get() = magnetUrl.isNotBlank() || torrentUrl.isNotBlank()
}

/**
 * 从磁力链接提取 `infoHash`（`xt=urn:btih:` 段，遇 `&` 截止并去除首尾空白）。
 *
 * **唯一真源**：引擎会话复用判据、仓库任务去重、种子缓存键与选源面板的既有任务比对都必须调用本函数。
 * 历史上这几处各写了一遍 `substringAfter("xt=urn:btih:")`，其中引擎侧还把"解析不出来"降级成
 * `"unknown_hash"` 哨兵字符串、其余处返回空串——同一语义两套降级，是"未知磁力互相误判为同一源"的温床。
 *
 * 解析不出（非磁力链接、缺 `xt` 段、`xt` 段为空）时统一返回**空串**，
 * 调用方一律以 `isBlank()` 判定"未知"，严禁再引入哨兵值。
 */
fun magnetInfoHash(magnetUrl: String): String =
    magnetUrl
        .substringAfter("xt=urn:btih:", "")
        .substringBefore("&")
        .trim()

/** 向后兼容别名，便于过渡期依赖平滑替换 */
typealias MikanTorrentResource = TorrentMediaResource
