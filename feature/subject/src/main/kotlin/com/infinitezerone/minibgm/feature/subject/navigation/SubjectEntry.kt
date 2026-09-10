package com.infinitezerone.minibgm.feature.subject.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.subject.SubjectDetailScreen

/** 条目详情页的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器） */
fun EntryProviderScope<NavKey>.subjectEntry(
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onTagClick: (String) -> Unit = {},
) {
    entry<SubjectDetailRoute> { route ->
        SubjectDetailScreen(
            subjectId = route.subjectId,
            initialName = route.initialName,
            initialCoverUrl = route.initialCoverUrl,
            initialScore = route.initialScore,
            source = route.source,
            onBackClick = onBackClick,
            onSubjectClick = onSubjectClick,
            onTagClick = onTagClick,
        )
    }
}
