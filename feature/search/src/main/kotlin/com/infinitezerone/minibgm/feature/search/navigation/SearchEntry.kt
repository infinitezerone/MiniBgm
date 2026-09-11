package com.infinitezerone.minibgm.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.search.SearchScreen

/** 搜索页面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器） */
fun EntryProviderScope<NavKey>.searchEntry(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onBackClick: (() -> Unit)? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SearchRoute>(metadata = metadata) { route ->
        SearchScreen(
            initialQuery = route.initialQuery,
            onSubjectClick = onSubjectClick,
            onBackClick = onBackClick,
        )
    }
}
