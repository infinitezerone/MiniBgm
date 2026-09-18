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

/**
 * 顶层 Tab 路由层级（Schedule, Explore, User）。
 * 用于标识应用主导航栏的核心目的地，配合 Navigation 3 entry 元数据实现瞬时无缝转场。
 */
sealed interface TopLevelRoute : BgmRoute

/**
 * 二级功能/列表页路由层级（搜索、收藏、AI 追番助手、新番导视等）。
 * 进入时清理先前残留的详情层级，同类型二级页按层级替换而非堆叠。
 */
sealed interface SubFeatureRoute : BgmRoute

/**
 * 条目详情及钻取链路由层级（条目详情、关联条目、分集讨论、标签专题、讨论帖等）。
 * 详情页在列表中点击时替换旧详情，钻取链层级逐层压栈。
 */
sealed interface DetailChainRoute : BgmRoute

@Serializable
data object ScheduleRoute : TopLevelRoute

@Serializable
data object ExploreRoute : TopLevelRoute

@Serializable
data object UserRoute : TopLevelRoute

@Serializable
data class SearchRoute(
    val initialQuery: String = "",
) : SubFeatureRoute

@Serializable
data class SubjectDetailRoute(
    val subjectId: Long,
    val initialName: String = "",
    val initialCoverUrl: String = "",
    val initialScore: Double = 0.0,
    val source: String = "",
) : DetailChainRoute

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
) : DetailChainRoute

@Serializable
data class UserCollectionsRoute(
    val initialType: Int = 3,
) : SubFeatureRoute

@Serializable
data class EpisodeDetailRoute(
    val episodeId: Long,
    val subjectId: Long,
    val episodeSort: Float = 0f,
    val episodeType: Int = 0,
    val episodeName: String = "",
    val episodeNameCn: String = "",
) : DetailChainRoute

/**
 * 标签专题条目路由（从条目详情页点击热门标签触发）；
 * 在分栏大屏模式下作为 extraPane 展现在右侧，避免打乱左侧主列表与当前条目上下文。
 */
@Serializable
data class TagSubjectsRoute(
    val tag: String,
    val initialType: Int = 0,
) : DetailChainRoute

/**
 * AI 追番助手交互界面路由。
 */
@Serializable
data object AssistantRoute : SubFeatureRoute

/**
 * 季度新番导视大盘交互界面路由。
 * [initialYear] 与 [initialSeasonMonth] 为 0 时默认定位到当期年份与季度。
 */
@Serializable
data class SeasonalGuideRoute(
    val initialYear: Int = 0,
    val initialSeasonMonth: Int = 0,
) : SubFeatureRoute

/**
 * 讨论帖详情交互界面路由（包含主楼正文、楼层回帖与楼中楼树形回复）。
 * [initialTitle] 为初始预填标题，[type] 为 "subject" 或 "group"。
 */
@Serializable
data class TopicDetailRoute(
    val topicId: Long,
    val initialTitle: String = "",
    val type: String = "subject",
) : DetailChainRoute

/**
 * 应用全局设置交互界面路由（包含数据源同步、AI 追番助手、存储管理、关于与账号安全）。
 */
@Serializable
data object SettingsRoute : SubFeatureRoute
