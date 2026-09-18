package com.infinitezerone.minibgm.feature.user.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.navigation.PlaybackRulesRoute
import com.infinitezerone.minibgm.core.navigation.SettingsRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserCollectionsRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.feature.user.PlaybackRulesScreen
import com.infinitezerone.minibgm.feature.user.SettingsScreen
import com.infinitezerone.minibgm.feature.user.UserCollectionsScreen
import com.infinitezerone.minibgm.feature.user.UserScreen
import kotlinx.coroutines.flow.Flow

/** 「我的」个人中心主页条目（全屏 Dashboard，支持自适应双列） */
fun EntryProviderScope<NavKey>.userEntry(
    onCollectionClick: (CollectionType) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<UserRoute>(metadata = metadata) {
        UserScreen(
            onCollectionClick = onCollectionClick,
            onSettingsClick = onSettingsClick,
            scrollToTop = scrollToTop,
        )
    }
}

/** 「我的收藏」分类列表条目（番剧列表，支持自适应左右分栏） */
fun EntryProviderScope<NavKey>.userCollectionsEntry(
    onSubjectClick: (SubjectDetailRoute) -> Unit = {},
    onBackClick: () -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<UserCollectionsRoute>(metadata = metadata) { route ->
        UserCollectionsScreen(
            initialType = CollectionType.fromValue(route.initialType),
            onSubjectClick = onSubjectClick,
            onBackClick = onBackClick,
        )
    }
}

/** 应用「全局设置」二级页面条目 */
fun EntryProviderScope<NavKey>.settingsEntry(
    onBackClick: () -> Unit = {},
    onPlaybackRulesClick: () -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SettingsRoute>(metadata = metadata) {
        SettingsScreen(
            onBackClick = onBackClick,
            onPlaybackRulesClick = onPlaybackRulesClick,
        )
    }
}

/** 自定义「播放规则管理」二级页面条目 */
fun EntryProviderScope<NavKey>.playbackRulesEntry(
    onBackClick: () -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<PlaybackRulesRoute>(metadata = metadata) {
        PlaybackRulesScreen(
            onBackClick = onBackClick,
        )
    }
}
