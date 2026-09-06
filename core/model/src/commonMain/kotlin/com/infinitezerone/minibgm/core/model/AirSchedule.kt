package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AirSchedule(
    val bgmId: Long,
    val title: String,
    val titleCn: String,
    val coverUrl: String = "",
    val ratingScore: Double = 0.0,
    val beginUtc: String = "",
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
