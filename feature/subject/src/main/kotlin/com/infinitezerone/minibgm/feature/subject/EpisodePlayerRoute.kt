package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.navigation.PlayerRoute

/**
 * 「分集 → 播放器路由」的唯一组装出口（条目页与分集详情页共用）：
 * 交由播放器内的规则引擎与流地址竞速解析器处理。
 */
fun buildEpisodePlayerRoute(
    subjectId: Long,
    subjectName: String,
    episode: Episode,
    coverUrl: String = "",
): PlayerRoute =
    PlayerRoute(
        subjectId = subjectId,
        episodeId = episode.id,
        streamUrl = "",
        requestHeaders = emptyMap(),
        episodeName = episode.primaryName,
        subjectName = subjectName,
        episodeSort = episode.episodeNumber,
        episodeType = episode.type,
        coverUrl = coverUrl,
    )
