package com.infinitezerone.minibgm.feature.subject.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.player.PlayerScreen

/**
 * 视频播放界面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器）
 */
fun EntryProviderScope<NavKey>.playerEntry(
    onBackClick: () -> Unit,
    onRequestOpenSources: (() -> Unit)? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<PlayerRoute>(metadata = metadata) { route ->
        PlayerScreen(
            route = route,
            onBackClick = onBackClick,
            onRequestOpenSources = onRequestOpenSources,
        )
    }
}
