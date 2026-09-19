package com.infinitezerone.minibgm.feature.assistant.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.AssistantRoute
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.assistant.AssistantScreen

/**
 * AI 追番助手页面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器）。
 */
fun EntryProviderScope<NavKey>.assistantEntry(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onPlaySource: (PlayerRoute) -> Unit = {},
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<AssistantRoute>(metadata = metadata) { route ->
        AssistantScreen(
            prefillPrompt = route.prefillPrompt,
            onSubjectClick = onSubjectClick,
            onBackClick = onBackClick,
            onPlaySource = onPlaySource,
        )
    }
}
