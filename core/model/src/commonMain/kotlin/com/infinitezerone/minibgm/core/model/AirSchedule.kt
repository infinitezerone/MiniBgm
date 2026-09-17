package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

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
