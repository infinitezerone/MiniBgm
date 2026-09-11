package com.infinitezerone.minibgm.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * MiniBgm 全局类型安全路由契约（Navigation 3 NavKey）
 */

@Serializable
data object ScheduleRoute : NavKey

@Serializable
data object ExploreRoute : NavKey

@Serializable
data object UserRoute : NavKey

@Serializable
data class SearchRoute(
    val initialQuery: String = "",
) : NavKey

@Serializable
data class SubjectDetailRoute(
    val subjectId: Long,
    val initialName: String = "",
    val initialCoverUrl: String = "",
    val initialScore: Double = 0.0,
    val source: String = "",
) : NavKey

/**
 * 关联/外链条目详情路由（由分集评论外链或关联作品触发）；
 * 在分栏大屏模式下作为 extraPane 展现在右侧，避免打乱左侧已打开的主条目上下文。
 */
@Serializable
data class LinkedSubjectRoute(
    val subjectId: Long,
    val initialName: String = "",
    val initialCoverUrl: String = "",
    val initialScore: Double = 0.0,
    val source: String = "",
) : NavKey

@Serializable
data class UserCollectionsRoute(
    val initialType: Int = 3,
) : NavKey

@Serializable
data class EpisodeDetailRoute(
    val episodeId: Long,
    val subjectId: Long,
    val episodeSort: Float = 0f,
    val episodeType: Int = 0,
    val episodeName: String = "",
    val episodeNameCn: String = "",
) : NavKey
