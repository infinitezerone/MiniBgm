package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

/** 分集面板：头部工具条、分组筛选、列表/网格、加载更多，由详情页 Bottom Sheet 承接。 */
@Composable
internal fun EpisodesTabPage(
    uiState: SubjectDetailUiState,
    subjectType: SubjectType,
    totalEpisodes: Int,
    currentEpisodes: List<Episode>,
    availableGroups: List<EpisodeGroup>,
    groupedEpisodes: Map<EpisodeGroup, List<Episode>>,
    activeGroup: EpisodeGroup,
    onSelectGroup: (EpisodeGroup) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    episodeSortDescending: Boolean,
    hasMoreEpisodes: Boolean,
    isLoadingMoreEpisodes: Boolean,
    onToggleEpisodeSort: () -> Unit,
    onLoadMoreEpisodes: () -> Unit,
    onRetryEpisodes: () -> Unit,
    onToggleEpisodeWatched: (Episode, Boolean) -> Unit,
    onSelectEpisodeForDetail: (Episode) -> Unit,
    onEpisodeClick: ((Episode) -> Unit)?,
    onBatchMarkEpisode: (Episode) -> Unit,
    onPlayEpisode: ((Episode) -> Unit)?,
    onOpenSources: (() -> Unit)?,
    onOpenEpisodeSources: ((Episode) -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
    bottomPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val gridColumns = if (adaptiveInfo.isWide) 7 else 5
    val pageListState = rememberLazyListState()
    val nextUpEpNumber =
        remember(currentEpisodes, uiState.collection?.epStatus) {
            currentEpisodes
                .firstOrNull {
                    isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
                }?.episodeNumber
        }

    var selectedEpisodeId by rememberSaveable(currentEpisodes.firstOrNull()?.id) {
        val initial =
            currentEpisodes.firstOrNull {
                isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
            } ?: currentEpisodes.firstOrNull()
        mutableStateOf(initial?.id)
    }
    val selectedEpisode =
        remember(selectedEpisodeId, currentEpisodes) {
            currentEpisodes.firstOrNull { it.id == selectedEpisodeId }
                ?: currentEpisodes.firstOrNull()
        }

    // 滚动到「加载更多」脚标可见时自动续拉下一屏分集
    LaunchedEffect(pageListState, hasMoreEpisodes) {
        snapshotFlow {
            pageListState.layoutInfo.visibleItemsInfo.any { it.key == "episodes_load_more" }
        }.collect { visible ->
            if (visible && hasMoreEpisodes) onLoadMoreEpisodes()
        }
    }

    LazyColumn(
        state = pageListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val watchedInGroup =
            currentEpisodes.count {
                isEpisodeWatched(it, uiState.collection?.epStatus ?: 0)
            }

        val effectiveTotalEpisodes =
            if (activeGroup == EpisodeGroup.MAIN) {
                if (totalEpisodes > 0) maxOf(totalEpisodes, currentEpisodes.size) else 0
            } else {
                currentEpisodes.size
            }

        item(key = "episodes_header") {
            EpisodesSectionHeader(
                totalEpisodes = effectiveTotalEpisodes,
                watchedEpisodes = watchedInGroup,
                subjectType = subjectType,
                isGridView = isGridView,
                onToggleView = onToggleGridView,
                episodeSortDescending = episodeSortDescending,
                onToggleSort = onToggleEpisodeSort,
                nextUpEpisodeSort = nextUpEpNumber,
                onPlayNext = onPlayNextEpisode.takeIf { currentEpisodes.isNotEmpty() },
                onOpenSources = onOpenSources,
                group = activeGroup,
                hasMoreEpisodes = hasMoreEpisodes,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (availableGroups.size > 1) {
            item(key = "episode_group_chips") {
                EpisodeGroupFilterChips(
                    availableGroups = availableGroups,
                    groupedEpisodes = groupedEpisodes,
                    selectedGroup = activeGroup,
                    onGroupSelected = onSelectGroup,
                    showCounts = !hasMoreEpisodes,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        when {
            (uiState.isEpisodesLoading || uiState.isLoading) && currentEpisodes.isEmpty() -> {
                item(key = "episodes_skeleton") {
                    if (isGridView) {
                        EpisodeGridSkeleton(columns = gridColumns, modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        EpisodeListSkeleton(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            uiState.episodesError != null && currentEpisodes.isEmpty() -> {
                item(key = "episodes_error") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = stringResource(R.string.feature_subject_episodes_load_failed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = uiState.episodesError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            FilledTonalButton(
                                onClick = onRetryEpisodes,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.feature_subject_reload))
                            }
                        }
                    }
                }
            }

            currentEpisodes.isEmpty() -> {
                item(key = "episodes_empty") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_episodes_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            isGridView -> {
                item(key = "episodes_grid") {
                    EpisodeGrid(
                        episodes = currentEpisodes,
                        watchedCount = uiState.collection?.epStatus ?: 0,
                        hasProgress = uiState.collection != null,
                        selectedEpisodeId = selectedEpisode?.id,
                        onToggleWatched = onToggleEpisodeWatched,
                        onEpisodeClick = { episode ->
                            selectedEpisodeId = episode.id
                            onEpisodeClick?.invoke(episode)
                        },
                        onEpisodeLongClick = { episode ->
                            val isWatched = isEpisodeWatched(episode, uiState.collection?.epStatus ?: 0)
                            if (!isWatched && episode.type == 0) {
                                onBatchMarkEpisode(episode)
                            } else {
                                onSelectEpisodeForDetail(episode)
                            }
                        },
                        columns = gridColumns,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            else -> {
                items(items = currentEpisodes, key = { it.id }) { episode ->
                    val watchedCount = uiState.collection?.epStatus ?: 0
                    val isWatched = isEpisodeWatched(episode, watchedCount)
                    val isNextToWatch =
                        isEpisodeNextToWatch(episode, watchedCount, hasProgress = uiState.collection != null)
                    EpisodeListItem(
                        episode = episode,
                        isWatched = isWatched,
                        isNextToWatch = isNextToWatch,
                        onClick = {
                            if (onEpisodeClick != null) {
                                onEpisodeClick(episode)
                            } else {
                                onSelectEpisodeForDetail(episode)
                            }
                        },
                        onToggleWatched = {
                            onToggleEpisodeWatched(episode, !isWatched)
                        },
                        onPlayClick =
                            if ((subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL) &&
                                onPlayEpisode != null
                            ) {
                                { onPlayEpisode(episode) }
                            } else {
                                null
                            },
                        onOpenSources =
                            if ((subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL) &&
                                onOpenEpisodeSources != null
                            ) {
                                { onOpenEpisodeSources(episode) }
                            } else {
                                null
                            },
                        onLongClick = {
                            if (!isWatched && episode.type == 0) {
                                onBatchMarkEpisode(episode)
                            } else {
                                onSelectEpisodeForDetail(episode)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        if (hasMoreEpisodes) {
            item(key = "episodes_load_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMoreEpisodes) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.feature_subject_load_more_episodes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
