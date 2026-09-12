package com.infinitezerone.minibgm.feature.agent.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.AgentChatRoute
import com.infinitezerone.minibgm.feature.agent.AgentChatScreen
import com.infinitezerone.minibgm.feature.agent.AgentChatViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Agent 聊天页导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式，feature 不感知导航容器）。
 * ViewModel 与工具集经 Koin 注入（agentModule）。
 */
fun EntryProviderScope<NavKey>.agentChatEntry(onBackClick: () -> Unit) {
    entry<AgentChatRoute> { _ ->
        val viewModel: AgentChatViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AgentChatScreen(
            uiState = uiState,
            onBackClick = onBackClick,
            onSendMessage = viewModel::send,
            onInputChange = viewModel::updateInput,
            onConfigChange = viewModel::updateConfig,
            onClearSession = viewModel::clearSession,
        )
    }
}
