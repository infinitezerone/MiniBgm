package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.user.components.PlaybackHistoryCard
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 观看记录独立二级界面（Stateful 容器）：
 * - 集中展示用户在内置播放器中的播放历史与各分集断点进度；
 * - 支持点击一键按该分集断点续播，或跳转番剧条目详情；
 * - 单集删除即时触发并支持 Snackbar 撤销；
 * - 整剧删除与全量清空经由 [BgmOverlayHost] 确认弹窗。
 */
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onPlayHistory: (PlayerRoute) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit = {},
    viewModel: HistoryViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val overlayHostState = rememberOverlayHostState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HistoryUiEvent.ShowSnackbar -> {
                val text =
                    event.message?.takeIf { it.isNotBlank() }
                        ?: context.resources.getString(event.messageRes)
                val actionLabel =
                    if (event.actionRes > 0) {
                        context.resources.getString(event.actionRes)
                    } else {
                        null
                    }
                scope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = text,
                            actionLabel = actionLabel,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            }
        }
    }

    HistoryContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onClearAll = {
            scope.launch {
                val confirmed =
                    overlayHostState.await(
                        ConfirmDialogAction(
                            title = context.resources.getString(R.string.feature_user_history_clear_all_title),
                            message = context.resources.getString(R.string.feature_user_history_clear_all_message),
                            confirmText = context.resources.getString(R.string.feature_user_history_clear_all_action),
                            isDestructive = true,
                        ),
                    )
                if (confirmed) {
                    viewModel.clearPlaybackHistory()
                }
            }
        },
        onResume = { entry -> onPlayHistory(entry.toPlayerRoute()) },
        onSubjectClick = onSubjectClick,
        onToggleExpanded = { subjectId -> viewModel.toggleSubjectExpanded(subjectId) },
        onDeleteEpisode = { entry -> viewModel.deleteHistory(entry.episodeId) },
        onDeleteGroup = { group ->
            scope.launch {
                val groupName = group.subjectName.ifBlank { group.latest.episodeName }
                val confirmed =
                    overlayHostState.await(
                        ConfirmDialogAction(
                            title = context.resources.getString(R.string.feature_user_history_delete_title),
                            message =
                                context.resources.getString(
                                    if (group.subjectId > 0L) {
                                        R.string.feature_user_history_delete_subject_message
                                    } else {
                                        R.string.feature_user_history_delete_message
                                    },
                                    groupName,
                                ),
                            confirmText = context.resources.getString(R.string.feature_user_action_delete),
                            isDestructive = true,
                        ),
                    )
                if (confirmed) {
                    if (group.subjectId > 0L) {
                        viewModel.deleteSubjectHistory(group.subjectId)
                    } else {
                        viewModel.deleteHistory(group.latest.episodeId)
                    }
                }
            }
        },
        snackbarHost = { BgmSnackbarHost(hostState = snackbarHostState) },
        overlayHost = {
            BgmOverlayHost(hostState = overlayHostState) {
                confirmDialog()
            }
        },
        modifier = modifier,
    )
}

/** 观看记录主体视图（Stateless）：专职骨架排版与列表呈现 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onBackClick: () -> Unit,
    onClearAll: () -> Unit,
    onResume: (PlaybackHistory) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    onDeleteEpisode: (PlaybackHistory) -> Unit,
    onDeleteGroup: (HistoryGroup) -> Unit,
    snackbarHost: @Composable () -> Unit,
    overlayHost: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = stringResource(R.string.feature_user_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                        )
                    }
                },
                actions = {
                    if (uiState.history.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(
                                imageVector = BgmIcons.CleaningServices,
                                contentDescription = stringResource(R.string.feature_user_history_clear_all_action),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
        modifier = modifier,
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.history.isEmpty() -> {
                HistoryEmptyState(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = uiState.groups,
                        key = { group ->
                            group.subjectId
                                .takeIf { it > 0L }
                                ?.let { "s:$it" }
                                ?: "e:${group.latest.episodeId}"
                        },
                    ) { group ->
                        PlaybackHistoryCard(
                            group = group,
                            expanded = group.subjectId in uiState.expandedSubjectIds,
                            onToggleExpanded = { onToggleExpanded(group.subjectId) },
                            onResume = onResume,
                            onSubjectClick = onSubjectClick,
                            onDeleteEpisode = onDeleteEpisode,
                            onDeleteGroup = { onDeleteGroup(group) },
                        )
                    }
                }
            }
        }

        overlayHost()
    }
}

/** 观看记录空状态视图 */
@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = BgmIcons.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.feature_user_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 历史记录 → 播放器路由：点击即按该分集的断点续播 */
private fun PlaybackHistory.toPlayerRoute(): PlayerRoute {
    val isRuleSource = sourceId.isNotBlank() && sourceId != "direct"
    return PlayerRoute(
        subjectId = subjectId,
        episodeId = episodeId,
        streamUrl = if (isRuleSource) "" else streamUrl,
        episodeName = episodeName,
        subjectName = subjectName,
        episodeSort = episodeSort,
        initialSourceName = sourceName,
        initialSourceId = sourceId,
        initialRuleId = if (isRuleSource) sourceId else "",
        coverUrl = coverUrl,
    )
}
