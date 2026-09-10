package com.infinitezerone.minibgm.feature.user.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserCollectionsRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.feature.user.UserCollectionsScreen
import com.infinitezerone.minibgm.feature.user.UserScreen
import kotlinx.coroutines.flow.Flow

/** 「我的」Tab 的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器） */
fun EntryProviderScope<NavKey>.userEntry(
    onSubjectClick: (SubjectDetailRoute) -> Unit = {},
    onCollectionClick: (CollectionType) -> Unit = {},
    onBackClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
) {
    entry<UserRoute> {
        UserScreen(
            onCollectionClick = onCollectionClick,
            scrollToTop = scrollToTop,
        )
    }

    entry<UserCollectionsRoute> { route ->
        UserCollectionsScreen(
            initialType = CollectionType.fromValue(route.initialType),
            onSubjectClick = onSubjectClick,
            onBackClick = onBackClick,
        )
    }
}
