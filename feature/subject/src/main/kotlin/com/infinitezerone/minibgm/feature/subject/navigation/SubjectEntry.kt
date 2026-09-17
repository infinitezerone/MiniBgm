package com.infinitezerone.minibgm.feature.subject.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.subject.SubjectDetailScreen

/** 条目详情页的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器） */
fun EntryProviderScope<NavKey>.subjectEntry(
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onEpisodeClick: (EpisodeDetailRoute) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SubjectDetailRoute>(metadata = metadata) { route ->
        SubjectDetailScreen(
            subjectId = route.subjectId,
            initialName = route.initialName,
            initialCoverUrl = route.initialCoverUrl,
            initialScore = route.initialScore,
            source = route.source,
            onBackClick = onBackClick,
            onSubjectClick = onSubjectClick,
            onEpisodeClick = onEpisodeClick,
            onTagClick = onTagClick,
        )
    }
}

/** 关联/外链条目详情页的导航条目（在分栏大屏模式下作为 extraPane 展现在右侧，不打乱左侧主条目） */
fun EntryProviderScope<NavKey>.linkedSubjectEntry(
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onEpisodeClick: (EpisodeDetailRoute) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<com.infinitezerone.minibgm.core.navigation.LinkedSubjectRoute>(metadata = metadata) { route ->
        SubjectDetailScreen(
            subjectId = route.subjectId,
            initialName = route.initialName,
            initialCoverUrl = route.initialCoverUrl,
            initialScore = route.initialScore,
            source = route.source,
            onBackClick = onBackClick,
            onSubjectClick = onSubjectClick,
            onEpisodeClick = onEpisodeClick,
            onTagClick = onTagClick,
        )
    }
}
