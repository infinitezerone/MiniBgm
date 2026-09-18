package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

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
)

/** 一个条目的可播放清单：助手与播放器之间传递的唯一形态，集数列表按 [episodes] 顺序展示 */
@Serializable
data class PlayableEpisodeList(
    val subjectId: Long = 0L,
    val title: String = "",
    /** 这批来源从哪来：自备片单 / 来源站解析，供 UI 标注可信度 */
    val source: String = "",
    val episodes: List<PlayableSource> = emptyList(),
)
