package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.matchesForEpisode
import com.infinitezerone.minibgm.core.navigation.PlayerRoute

/**
 * 「分集 → 播放器路由」的唯一组装出口（条目页与分集详情页共用）：
 * 片单按话数精确匹配直链，未命中则交给播放器内的规则嗅探。
 */
fun buildEpisodePlayerRoute(
    subjectId: Long,
    subjectName: String,
    episode: Episode,
    playlists: List<PlaybackPlaylist>,
): PlayerRoute {
    val matchingEntry =
        playlists
            .matchesForEpisode(subjectId, if (episode.ep > 0f) episode.ep else episode.sort)
            .firstOrNull()
            ?.entry
    return PlayerRoute(
        subjectId = subjectId,
        episodeId = episode.id,
        streamUrl = matchingEntry?.url.orEmpty(),
        requestHeaders = matchingEntry?.headers.orEmpty(),
        episodeName = episode.nameCn.ifBlank { episode.name },
        subjectName = subjectName,
        episodeSort = if (episode.ep > 0f) episode.ep else episode.sort,
        episodeType = episode.type,
    )
}
