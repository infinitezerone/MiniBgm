package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Subject(
    val id: Long,
    val type: Int = 2,
    val name: String,
    @SerialName("name_cn") val nameCn: String = "",
    val summary: String = "",
    val date: String = "",
    @SerialName("air_date") val airDate: String = "",
    val eps: Int = 0,
    @SerialName("total_episodes") val totalEpisodes: Int = 0,
    val images: SubjectImages? = null,
    val rating: Rating? = null,
    val collection: CollectionCount? = null,
    val tags: List<Tag> = emptyList(),
) {
    val displayName: String
        get() = nameCn.ifBlank { name }
}

@Serializable
data class SubjectImages(
    val large: String = "",
    val common: String = "",
    val medium: String = "",
    val small: String = "",
    val grid: String = "",
) {
    /**
     * 适用于绝大多数卡片、列表、网格、双列瀑布流的高效封面图：
     * 优先采用 Bangumi CDN 裁切优化的 400px (common) / 800px (medium) 压缩图（~25KB），
     * 彻底避免在移动端列表无脑下载数兆原始扫图 (large) 导致的巨额带宽浪费、解码性能瓶颈与卡顿。
     * 同时强制转换为 https，避免 301 Moved Permanently 重定向与额外 TLS 握手开销。
     */
    val bestImage: String
        get() {
            val raw = (common.ifBlank { medium.ifBlank { large } }).replace("http://", "https://")
            if (raw.contains("lain.bgm.tv")) {
                if (raw.contains("/pic/cover/c/")) {
                    return raw.replace("/pic/cover/c/", "/r/400/pic/cover/l/")
                }
                if (raw.contains("/pic/cover/m/")) {
                    return raw.replace("/pic/cover/m/", "/r/400/pic/cover/l/")
                }
                if (raw.contains("/pic/cover/l/") && !raw.contains("/r/")) {
                    return raw.replace("/pic/cover/l/", "/r/400/pic/cover/l/")
                }
            }
            return raw
        }

    /** 适用于大图画廊、全屏海报的高清大图 */
    val largeImage: String
        get() = (large.ifBlank { medium.ifBlank { common } }).replace("http://", "https://")

    /** 适用于极小头像、紧凑网格（100px）的微缩图 */
    val thumbnailImage: String
        get() = (grid.ifBlank { small.ifBlank { common } }).replace("http://", "https://")
}

@Serializable
data class Rating(
    val score: Double = 0.0,
    val total: Int = 0,
    val rank: Int = 0,
    val count: Map<String, Int> = emptyMap(),
)

@Serializable
data class CollectionCount(
    val wish: Int = 0,
    val collect: Int = 0,
    val doing: Int = 0,
    val onHold: Int = 0,
    val dropped: Int = 0,
)

@Serializable
data class Tag(
    val name: String,
    val count: Int = 0,
)
