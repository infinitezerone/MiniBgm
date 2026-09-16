package com.infinitezerone.minibgm.core.model

/**
 * 首页时空动态行动卡片的动作紧急程度。
 */
enum class NextUpUrgency {
    /** 距离开播不到 45 分钟 ("还有 X 分钟开播") */
    IMMINENT,

    /** 今日已开播，且尚未观看 ("今日已更新 · 第 X 话") */
    TODAY_AIRED,

    /** 今日即将开播，但距离开播还有 45 分钟以上 ("今日 HH:mm 准时放送") */
    TODAY_UPCOMING,
}

/**
 * 首页时空动态行动卡片的领域模型。
 *
 * 核心设计：基于时间轴的 Contextual Action (不可见 AI)
 * 只在正确的时间 (即将开播 / 刚开播未看) 出现，零噪音。
 */
data class NextUpAction(
    val subjectId: Long,
    val title: String,
    val titleCn: String,
    val coverUrl: String,
    val episodeNumber: Int,
    val airTimeLabel: String,
    val urgency: NextUpUrgency,
    val primaryPlayLink: SiteLink? = null,
    val allPlayLinks: List<SiteLink> = emptyList(),
    val canMarkWatched: Boolean = false,
)
