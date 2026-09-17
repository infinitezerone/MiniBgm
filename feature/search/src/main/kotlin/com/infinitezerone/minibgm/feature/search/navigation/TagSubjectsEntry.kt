package com.infinitezerone.minibgm.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.TagSubjectsRoute
import com.infinitezerone.minibgm.feature.search.TagSubjectsScreen

/** 标签专题页面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器） */
fun EntryProviderScope<NavKey>.tagSubjectsEntry(
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<TagSubjectsRoute>(metadata = metadata) { route ->
        TagSubjectsScreen(
            tag = route.tag,
            initialType = route.initialType,
            onBackClick = onBackClick,
            onSubjectClick = onSubjectClick,
        )
    }
}
