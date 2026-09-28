package com.infinitezerone.minibgm.feature.user.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.PlaybackRulesRoute
import com.infinitezerone.minibgm.core.navigation.SettingsRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.feature.user.PlaybackRulesScreen
import com.infinitezerone.minibgm.feature.user.SettingsScreen
import com.infinitezerone.minibgm.feature.user.UserScreen
import kotlinx.coroutines.flow.Flow

/**
 * 「我的」个人中心主页条目。
 *
 * 收藏列表已内联进本页（吸顶分区 Tab + 内容流），因此不再需要 `UserCollectionsRoute` 二级页；
 * 列表内的条目点击直接复用详情路由。
 */
fun EntryProviderScope<NavKey>.userEntry(
    onSubjectClick: (SubjectDetailRoute) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<UserRoute>(metadata = metadata) {
        UserScreen(
            onSubjectClick = onSubjectClick,
            onSettingsClick = onSettingsClick,
            scrollToTop = scrollToTop,
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
    onAiSourceSearch: (String) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<PlaybackRulesRoute>(metadata = metadata) {
        PlaybackRulesScreen(
            onBackClick = onBackClick,
            onAiSourceSearch = onAiSourceSearch,
        )
    }
}
