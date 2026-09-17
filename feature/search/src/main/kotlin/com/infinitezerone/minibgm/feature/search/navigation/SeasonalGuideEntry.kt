package com.infinitezerone.minibgm.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.SeasonalGuideRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.search.SeasonalGuideScreen

/**
 * 季度新番导视大盘界面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式）
 */
fun EntryProviderScope<NavKey>.seasonalGuideEntry(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onBackClick: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SeasonalGuideRoute>(metadata = metadata) { route ->
        SeasonalGuideScreen(
            initialYear = route.initialYear,
            initialSeasonMonth = route.initialSeasonMonth,
            onSubjectClick = onSubjectClick,
            onBackClick = onBackClick,
        )
    }
}
