package com.infinitezerone.minibgm.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * MiniBgm 全局类型安全路由契约（Navigation 3 NavKey）
 *
 * 所有路由必须实现 [BgmRoute]（sealed 层级）：这使 [BgmNavState] 的入栈语义
 * `when` 成为编译期穷尽匹配——新增路由时必须显式声明其层级语义
 * （详情层级 replace / 二级列表页同类替换 / 钻取链压栈），否则编译不过。
 * 严禁在模块其他文件直接实现 NavKey（见 ArchitectureRulesTest 路由红线）。
 */
sealed interface BgmRoute : NavKey

@Serializable
data object ScheduleRoute : BgmRoute

@Serializable
data object ExploreRoute : BgmRoute

@Serializable
data object UserRoute : BgmRoute

@Serializable
data class SearchRoute(
    val initialQuery: String = "",
) : BgmRoute

@Serializable
data class SubjectDetailRoute(
    val subjectId: Long,
    val initialName: String = "",
    val initialCoverUrl: String = "",
    val initialScore: Double = 0.0,
    val source: String = "",
) : BgmRoute

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
) : BgmRoute

@Serializable
data class UserCollectionsRoute(
    val initialType: Int = 3,
) : BgmRoute

@Serializable
data class EpisodeDetailRoute(
    val episodeId: Long,
    val subjectId: Long,
    val episodeSort: Float = 0f,
    val episodeType: Int = 0,
    val episodeName: String = "",
    val episodeNameCn: String = "",
) : BgmRoute

/**
 * Agent 聊天路由（自然语言助手入口）；与 SearchRoute 同层级：二级列表页语义。
 */
@Serializable
data object AgentChatRoute : BgmRoute

/**
 * 标签专题条目路由（从条目详情页点击热门标签触发）；
 * 在分栏大屏模式下作为 extraPane 展现在右侧，避免打乱左侧主列表与当前条目上下文。
 */
@Serializable
data class TagSubjectsRoute(
    val tag: String,
    val initialType: Int = 0,
) : BgmRoute
