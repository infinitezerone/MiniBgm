package com.infinitezerone.minibgm.feature.user.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.InAppLoginRoute
import com.infinitezerone.minibgm.core.navigation.InAppWebRoute
import com.infinitezerone.minibgm.core.navigation.PlaybackRulesRoute
import com.infinitezerone.minibgm.core.navigation.SettingsRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.feature.user.InAppLoginScreen
import com.infinitezerone.minibgm.feature.user.InAppWebScreen
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
    onLoginRequest: () -> Unit = {},
    onOpenTokenPage: (String) -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<UserRoute>(metadata = metadata) {
        UserScreen(
            onSubjectClick = onSubjectClick,
            onSettingsClick = onSettingsClick,
            onLoginRequest = onLoginRequest,
            onOpenTokenPage = onOpenTokenPage,
            scrollToTop = scrollToTop,
        )
    }
}

/** 应用「全局设置」二级页面条目 */
fun EntryProviderScope<NavKey>.settingsEntry(
    onBackClick: () -> Unit = {},
    onPlaybackRulesClick: (() -> Unit)? = null,
    enableAiConfig: Boolean = true,
    onClearCache: suspend () -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SettingsRoute>(metadata = metadata) {
        SettingsScreen(
            onBackClick = onBackClick,
            onPlaybackRulesClick = onPlaybackRulesClick,
            enableAiConfig = enableAiConfig,
            onClearCache = onClearCache,
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

/**
 * 应用内登录接管页条目。
 *
 * 全应用唯一的登录入口：授权页经环回代理走原生 ECH 通道，因此不跳系统浏览器——
 * 各 feature 只需 `navigateTo(InAppLoginRoute)`，不再各自 `beginLogin + launchWebUrl`。
 */
fun EntryProviderScope<NavKey>.inAppLoginEntry(
    onBackClick: () -> Unit = {},
    onOpenInBrowser: (String) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<InAppLoginRoute>(metadata = metadata) {
        InAppLoginScreen(
            onBack = onBackClick,
            onOpenInBrowser = onOpenInBrowser,
        )
    }
}

/** 应用内网页浏览条目（仅 bgm 系域名；代理不可用时降级系统浏览器） */
fun EntryProviderScope<NavKey>.inAppWebEntry(
    onBackClick: () -> Unit = {},
    onOpenInBrowser: (String) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<InAppWebRoute>(metadata = metadata) { route ->
        InAppWebScreen(
            url = route.url,
            title = route.title,
            onBack = onBackClick,
            onOpenInBrowser = onOpenInBrowser,
        )
    }
}
