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
    val genres: List<String> = emptyList(),
    val countryOfOrigin: String = "",
    /**
     * Bangumi 官方元标签：除题材外还携带**产地**（日本 / 中国 / 美国 / 欧美 …）与
     * **放送形式**（TV / WEB / 剧场版 / OVA / MV / PV / CM / 短片）。
     *
     * 此前这个字段在反序列化时被丢弃，导视只能拿用户标签（[tags]）加标题关键词去猜
     * 放送形式——`tags` 是 UGC 标签、噪声大，而这里才是站方维护的取值域。
     * 高级搜索的 `filter.meta_tags` 用的也是它，两端口径一致。
     */
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
    /** 放送平台（TV / WEB / OVA / 剧场版 / 其他）；比从 [metaTags] 里找形式标签更可靠 */
    val platform: String = "",
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

    /**
     * 放送电视台（infobox `播放电视台`，如 "TOKYO MX / BS11"）；未收录时为空串。
     *
     * 与 [titleAliases] 同类：都是 infobox 的派生读取，多态的 `value` 形状统一由 [flatValues]
     * 处理。放在模型层是为了让 UI（如导视列表的"集数 · 电视台"行）不必各自解一遍 JSON。
     */
    val broadcastStation: String
        get() =
            infobox
                .firstOrNull { it.key == INFOBOX_BROADCAST_STATION }
                ?.flatValues()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()

    /**
     * 关键主创与制作团队（infobox 首要制作人员，如动画制作、导演/监督、原作、作者、开发等）。
     */
    val keyStaff: List<Pair<String, String>>
        get() {
            val priorityKeys =
                listOf(
                    "动画制作",
                    "制作",
                    "导演",
                    "监督",
                    "原作",
                    "作画",
                    "作者",
                    "出版社",
                    "开发",
                    "发行",
                    "艺术家",
                    "音乐",
                )
            val result = mutableListOf<Pair<String, String>>()
            for (key in priorityKeys) {
                val item = infobox.firstOrNull { it.key == key } ?: continue
                val values = item.flatValues().filter { it.isNotBlank() }
                if (values.isNotEmpty()) {
                    result.add(key to values.joinToString(" / "))
                }
                if (result.size >= 4) break
            }
            return result
        }

    /**
     * 是否为片段型条目（MV / PV / CM / 短片等），而非有完整叙事、按集放送的正片。
     *
     * 一季的搜索结果里混着几十条音乐影像与宣传短片，会把真正的新番冲散，因此导视默认折叠它们。
     * 判据是 `platform == 其他` 或带片段类元标签——两种形态在真实响应里都出现过。
     */
    val isShortForm: Boolean
        get() = platform == PLATFORM_OTHER || metaTags.any { it in SHORT_FORM_META_TAGS }

    /**
     * 是否为季度片单中容易冲淡正片叙事的非主流条目（短片、MV、CM、泡面番、动态漫）。
     *
     * 满足以下任一条件即判定为净化折叠对象：
     * 1. 满足 [isShortForm]（平台为其他或元标签为片段类）；
     * 2. 标签命中微型或衍生类型（"泡面番"、"泡面"、"动态漫"、"动态漫画"）。
     */
    val isPurifiedNoise: Boolean
        get() = isShortForm || tags.any { it.name.trim() in PURIFIED_NOISE_TAGS }
}

/** `platform` 取「其他」的条目基本都是不占档期的片段映像 */
private const val PLATFORM_OTHER = "其他"

/** 片段类元标签：这些不是一集一集放送的正片 */
private val SHORT_FORM_META_TAGS = setOf("MV", "PV", "CM", "短片", "短片集")

/** 易冲散正片排期的微型或衍生类型标签 */
private val PURIFIED_NOISE_TAGS = setOf("泡面番", "泡面", "动态漫", "动态漫画")

/** 与片名相关的 infobox 条目；`日文名` 常与 [Subject.name] 重复，去重时会被合并 */
private val ALIAS_INFOBOX_KEYS = setOf("中文名", "别名", "第二中文名", "英文名", "罗马字", "日文名")

/** 别名里偶有整段简介混入，过长的不是片名 */
private const val MAX_ALIAS_LENGTH = 60

/** 放送电视台所在的 infobox 键名 */
private const val INFOBOX_BROADCAST_STATION = "播放电视台"

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
