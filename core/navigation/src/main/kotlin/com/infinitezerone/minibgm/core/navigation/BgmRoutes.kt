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
 * 二级功能/列表页路由层级（搜索、收藏、AI 追番助手、季度片单等）。
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
 * [prefillPrompt] 非空时进入页面即自动作为首条提问发送（如条目页「AI 找源」交接的检索请求）。
 */
@Serializable
data class AssistantRoute(
    val prefillPrompt: String = "",
) : SubFeatureRoute

/**
 * 季度片单交互界面路由。
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

/**
 * 应用内登录接管页路由：经本地环回代理把 Bangumi 授权页渲染在内置 WebView 中，
 * 从而复用原生 ECH 通道，无需跳转系统浏览器（Custom Tabs 用不了 ECH）。
 *
 * 声明为 [DetailChainRoute] 而非 [SubFeatureRoute]：登录是从任意页面临时压栈的全屏接管
 * （与 [PlayerRoute] 同类），退出后回到触发它的页面；若按二级页语义入栈，会把当前详情层清掉。
 */
@Serializable
data object InAppLoginRoute : DetailChainRoute

/**
 * 应用内网页浏览路由：把 bgm 系域名（bgm.tv / next.bgm.tv 等）的页面同样经环回代理渲染，
 * 复用 ECH 通道。当前用于「访问令牌」页面；内容页（话题/条目/wiki）浏览见后续阶段。
 */
@Serializable
data class InAppWebRoute(
    val url: String,
    val title: String = "",
) : DetailChainRoute

/**
 * 应用内视频播放交互界面路由（基于 Media3 ExoPlayer 渲染）。
 * [requestHeaders] 为用户自备列表条目的必要请求头（如 Referer/Cookie），随媒体请求发送。
 * [queue] 为本次播放的分集队列（找源清单/外部直链按序给出）：非空时播放器支持
 * 播完自动连播与全屏内选集抽屉，[startIndex] 指定起始分集；为空时退化为单集播放。
 */
@Serializable
data class PlayerRoute(
    val subjectId: Long,
    val episodeId: Long,
    val streamUrl: String = "",
    val episodeName: String = "",
    val subjectName: String = "",
    val episodeSort: Float = 1f,
    val episodeType: Int = 0,
    val requestHeaders: Map<String, String> = emptyMap(),
    val queue: List<PlayerQueueEntry> = emptyList(),
    val startIndex: Int = 0,
    val initialRuleId: String = "",
    /**
     * 入口携带的直链来源名（观看记录回放时 = 上次使用的规则名）。
     * 仅用于给「默认直链」贴上来源标签，不参与任何选源决策。
     */
    val initialSourceName: String = "",
) : DetailChainRoute

/** 播放队列里的一条分集（与 [PlayerRoute] 同生命周期，仅用于播放器内换集与连播） */
@Serializable
data class PlayerQueueEntry(
    val streamUrl: String,
    val label: String = "",
    val episodeName: String = "",
    val episodeSort: Float = 1f,
    val episodeType: Int = 0,
    val episodeId: Long = 0L,
    val requestHeaders: Map<String, String> = emptyMap(),
)

/**
 * 自定义播放规则管理交互界面路由。
 * 声明为 [DetailChainRoute]：可从设置页进入，也可从条目详情、分集详情与播放器临时压栈，
 * 返回时回到触发它的页面，避免将当前播放器或详情层清出栈。
 */
@Serializable
data object PlaybackRulesRoute : DetailChainRoute

/**
 * 下载管理总览界面路由（按番剧合集文件夹聚合展示）。
 * 声明为 [DetailChainRoute]：可从设置、用户中心或条目详情压栈，返回时精确回到前驱页面。
 */
@Serializable
data object DownloadRoute : DetailChainRoute

/**
 * 番剧分集下载管理与离线播放列表界面路由。
 * 声明为 [DetailChainRoute]：下钻查看某部番剧的具体分集状态、离线播放与单独控速。
 */
@Serializable
data class SubjectDownloadDetailRoute(
    val subjectId: Long,
    val subjectName: String = "",
) : DetailChainRoute

/**
 * 观看记录管理交互界面路由。
 * 声明为 [DetailChainRoute]：可从用户中心顶栏、设置或播放器进入，返回时精确回到前驱页面。
 */
@Serializable
data object HistoryRoute : DetailChainRoute
