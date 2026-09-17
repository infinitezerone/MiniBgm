package com.infinitezerone.minibgm.feature.subject.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.feature.subject.EpisodeDetailScreen
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel

/** 分集详情与讨论全屏三级页面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式） */
fun EntryProviderScope<NavKey>.episodeDetailEntry(
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onEpisodeClick: (EpisodeDetailRoute) -> Unit = {},
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<EpisodeDetailRoute>(metadata = metadata) { route ->
        val epNumberText =
            if (route.episodeType == 0) {
                "第 ${route.episodeSort.toEpisodeLabel()} 话"
            } else {
                "SP ${route.episodeSort.toInt()}"
            }
        val displayTitle = route.episodeNameCn.ifBlank { route.episodeName.ifBlank { epNumberText } }

        EpisodeDetailScreen(
            subjectId = route.subjectId,
            episodeId = route.episodeId,
            initialEpNumberText = epNumberText,
            initialEpisodeTitle = displayTitle,
            onBackClick = onBackClick,
            onSubjectClick = { targetSubjectId ->
                if (targetSubjectId == route.subjectId) {
                    onBackClick()
                } else {
                    onSubjectClick(targetSubjectId)
                }
            },
            onEpisodeClick = { targetEpId ->
                onEpisodeClick(
                    EpisodeDetailRoute(
                        episodeId = targetEpId,
                        subjectId = route.subjectId,
                    ),
                )
            },
            onCharacterClick = onCharacterClick,
            onPersonClick = onPersonClick,
        )
    }
}
