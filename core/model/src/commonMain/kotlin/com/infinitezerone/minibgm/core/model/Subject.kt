package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    val infobox: List<Infobox> = emptyList(),
) {
    val displayName: String
        get() = nameCn.ifBlank { name }

    /**
     * 取源检索用的片名别名。
     *
     * 台译名、港译名、英文名与罗马音都在 `infobox` 里——它们才是第三方站点标题上
     * 真正写的字，而 [name] / [nameCn] 只是其中两个。此前响应里带回来了却被丢弃，
     * 检索只能靠简繁单字转换硬凑（实测日文原名命中 2/12）。
     */
    val titleAliases: List<String>
        get() =
            infobox
                .filter { it.key in ALIAS_INFOBOX_KEYS }
                .flatMap { it.flatValues() }
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length <= MAX_ALIAS_LENGTH }
                .distinct()
}

/** 与片名相关的 infobox 条目；`日文名` 常与 [Subject.name] 重复，去重时会被合并 */
private val ALIAS_INFOBOX_KEYS = setOf("中文名", "别名", "第二中文名", "英文名", "罗马字", "日文名")

/** 别名里偶有整段简介混入，过长的不是片名 */
private const val MAX_ALIAS_LENGTH = 60

/**
 * Bangumi infobox 条目。
 *
 * `value` 是**多态**的：文字项是字符串，列表项是 `[{"v": "…"}]`。这里原样保留
 * [JsonElement]，由 [flatValues] 归一成字符串列表——比写自定义 Serializer
 * 更不容易踩到形状变化（该字段的两种形状在真实响应里同时存在）。
 */
@Serializable
data class Infobox(
    val key: String = "",
    val value: JsonElement = JsonNull,
)

/** 把多态的 infobox value 归一成字符串列表；`JsonNull` 落在 [JsonPrimitive] 分支并返回空 */
internal fun Infobox.flatValues(): List<String> =
    when (val element = value) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonArray ->
            element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> (item["v"] as? JsonPrimitive)?.contentOrNull
                    else -> null
                }
            }
        else -> emptyList()
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
