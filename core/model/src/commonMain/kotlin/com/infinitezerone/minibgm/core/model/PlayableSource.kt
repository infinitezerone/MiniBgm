package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/** 播放来源条目类型：DIRECT = 可直连播放的媒体地址；PAGE = 需外部打开的页面链接 */
@Serializable
enum class PlaylistEntryKind {
    DIRECT,
    PAGE,
}

/**
 * 一条可播放来源。
 *
 * [kind] 为 DIRECT 时 [url] 可直接交给播放器，配合 [headers]（Referer 等）即可起播；
 * 为 PAGE 时 [url] 只是需要继续解析或外部打开的页面。
 * [pageUrl] 记录该来源是从哪个页面解析出来的，便于回溯与去重。
 */
@Serializable
data class PlayableSource(
    val url: String,
    val kind: PlaylistEntryKind = PlaylistEntryKind.DIRECT,
    val label: String = "",
    val episodeSort: Float = 0f,
    val siteName: String = "",
    val pageUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val matchedVodName: String = "",
    val durationSeconds: Long = 0L,
)

/** 一个条目的可播放清单：助手与播放器之间传递的唯一形态，集数列表按 [episodes] 顺序展示 */
@Serializable
data class PlayableEpisodeList(
    val subjectId: Long = 0L,
    val title: String = "",
    /** 这批来源从哪来：规则解析 / 外部页面，供 UI 标注可信度 */
    val source: String = "",
    val episodes: List<PlayableSource> = emptyList(),
)
