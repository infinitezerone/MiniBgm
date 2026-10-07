package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 时刻表条目领域模型，一个类型承载双重语义：
 * 1. **时刻表条目**：封面 / 中日双标题 / 评分 / 播出时间段（[weekday]、[timeCst]、[timeJst]）等展示信息；
 * 2. **下一话仲裁结果**：[nextEpisodeNumber]、[nextEpisodeAtUtc]、[nextEpisodeKind] 三元组——
 *    由多来源播出事件仲裁回写（可信度 actual / scheduled / predicted），是"下一话"的唯一真值。
 */
@Serializable
data class AirSchedule(
    val bgmId: Long,
    val title: String,
    val titleCn: String,
    val coverUrl: String = "",
    val ratingScore: Double = 0.0,
    val airDate: String = "",
    val beginAtUtc: String? = null,
    val beginUtc: String = beginAtUtc ?: airDate,
    val weekday: Int = 1,
    val timeCst: String = "",
    val timeJst: String = "",
    val siteLinks: List<SiteLink> = emptyList(),
    val nextEpisodeNumber: Int = 0,
    /** 下一话播出时刻（UTC ISO-8601）；空串表示未知 */
    val nextEpisodeAtUtc: String = "",
    /** 时刻可信度：AirEventKind.ACTUAL / SCHEDULED / PREDICTED；空串表示未知 */
    val nextEpisodeKind: String = "",
    val isAiring: Boolean = true,
    val isAdult: Boolean = false,
    val format: String = "",
) {
    /** 展示标题：中文标题优先，空缺回退原日文标题 */
    val displayName: String
        get() = titleCn.ifBlank { title }
}

/** 单集播出事件领域模型（按自然日与时刻精准对应集数） */
@Serializable
data class AirScheduleEvent(
    val subjectId: Long,
    val episode: Int,
    val airAtUtc: String,
    val kind: String = "",
)

@Serializable
data class SiteLink(
    val siteName: String,
    val displayName: String,
    val playUrl: String,
)

/** 默认播放渠道与资源站点推荐显示优先级 */
val DEFAULT_SITE_PRIORITY =
    listOf(
        "bilibili",
        "gamer",
        "gamer_hk",
        "bahamut",
        "iqiyi",
        "qq",
        "youku",
        "mikan",
        "muse_tw",
        "muse_hk",
        "ani_one",
        "ani_one_asia",
        "netflix",
        "disneyplus",
        "crunchyroll",
        "abema",
        "danime",
        "unext",
        "prime",
        "nicovideo",
    )

private val SITE_PRIORITY_MAP: Map<String, Int> =
    DEFAULT_SITE_PRIORITY
        .withIndex()
        .associate { it.value to it.index }

/**
 * 依据全局站点优先级对播放源列表去重并排序（O(1) 站点查找）
 */
fun List<SiteLink>.sortedBySitePriority(): List<SiteLink> =
    distinctBy { it.displayName }.sortedBy { link ->
        SITE_PRIORITY_MAP[link.siteName.lowercase()] ?: 100
    }
