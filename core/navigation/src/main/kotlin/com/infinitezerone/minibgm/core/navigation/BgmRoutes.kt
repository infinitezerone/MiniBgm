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
) : NavKey

@Serializable
data class UserCollectionsRoute(
    val initialType: Int = 3,
) : NavKey
