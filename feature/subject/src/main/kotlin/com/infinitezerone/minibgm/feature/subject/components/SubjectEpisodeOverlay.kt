package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.OverlayRequest
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetHandle
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

/** 分集选择与播放源共享一个 Overlay request，避免抽屉套抽屉。 */
internal data class SubjectEpisodeOverlayRequest(
    val initialEpisode: Episode? = null,
    val startInPicker: Boolean = true,
) : OverlayRequest<Unit>

@Composable
internal fun SubjectEpisodeOverlay(
    request: SubjectEpisodeOverlayRequest,
    subject: Subject,
    subjectType: SubjectType,
    uiState: SubjectDetailUiState,
    totalEpisodes: Int,
    activeGroup: EpisodeGroup,
    onSelectGroup: (EpisodeGroup) -> Unit,
    onToggleGridView: () -> Unit,
    onToggleEpisodeSort: () -> Unit,
    onLoadMoreEpisodes: () -> Unit,
    onRetryEpisodes: () -> Unit,
    onToggleEpisodeWatched: (Episode, Boolean) -> Unit,
    onOpenEpisodeDetails: (Episode) -> Unit,
    onBatchMarkEpisode: (Episode) -> Unit,
    onPlay: ((PlayerRoute) -> Unit)?,
    onStreamingUrlLaunch: (String) -> Unit,
    onAiSourceSearch: ((Episode?) -> Unit)?,
    onManageRules: (() -> Unit)?,
    onRequestNotificationPermission: (() -> Unit)?,
    onRefreshTorrent: (Episode?) -> Unit,
    onDownloadStarted: () -> Unit,
    onEpisodeChanged: (Episode?) -> Unit,
    onShowAllTorrents: ((Episode?) -> Unit)? = null,
    onRestoreEpisodeFilter: ((Episode?) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    var showPicker by remember(request) { mutableStateOf(request.startInPicker) }
    var selectedEpisode by remember(request) { mutableStateOf(request.initialEpisode) }
    val sheetHandle = rememberBgmBottomSheetHandle(skipPartiallyExpanded = true)
    val supportsPlaybackSources =
        subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL
    val effectiveEpisode =
        remember(selectedEpisode, uiState.episodes, uiState.collection?.epStatus) {
            selectedEpisode
                ?: uiState.episodes.firstOrNull {
                    isEpisodeNextToWatch(
                        it,
                        uiState.collection?.epStatus ?: 0,
                        hasProgress = uiState.collection != null,
                    )
                }
                ?: uiState.episodes.firstOrNull()
        }

    val showSources = supportsPlaybackSources && !showPicker
    LaunchedEffect(showSources, effectiveEpisode?.id) {
        if (showSources) {
            onEpisodeChanged(effectiveEpisode)
        }
    }

    val orderedEpisodes =
        remember(uiState.episodes, uiState.episodeSortDescending) {
            if (uiState.episodeSortDescending) {
                uiState.episodes.sortedByDescending { it.sort }
            } else {
                uiState.episodes.sortedBy { it.sort }
            }
        }
    val groupedEpisodes =
        remember(orderedEpisodes) {
            orderedEpisodes.groupBy {
                EpisodeGroup.fromType(it.type)
            }
        }
    val availableGroups =
        remember(groupedEpisodes) {
            EpisodeGroup.entries.filter {
                groupedEpisodes.containsKey(it)
            }
        }

    if (!supportsPlaybackSources) {
        SubjectEpisodesBottomSheet(
            subject = subject,
            subjectType = subjectType,
            uiState = uiState,
            totalEpisodes = totalEpisodes,
            currentEpisodes = groupedEpisodes[activeGroup].orEmpty(),
            availableGroups = availableGroups,
            groupedEpisodes = groupedEpisodes,
            activeGroup = activeGroup,
            onSelectGroup = onSelectGroup,
            isGridView = uiState.isEpisodeGridView,
            onToggleGridView = onToggleGridView,
            episodeSortDescending = uiState.episodeSortDescending,
            hasMoreEpisodes = uiState.hasMoreEpisodes,
            isLoadingMoreEpisodes = uiState.isLoadingMoreEpisodes,
            onToggleEpisodeSort = onToggleEpisodeSort,
            onLoadMoreEpisodes = onLoadMoreEpisodes,
            onRetryEpisodes = onRetryEpisodes,
            onToggleEpisodeWatched = onToggleEpisodeWatched,
            onSelectEpisode = { episode ->
                onDismissRequest()
                onOpenEpisodeDetails(episode)
            },
            onOpenEpisodeDetails = { episode ->
                onDismissRequest()
                onOpenEpisodeDetails(episode)
            },
            onBatchMarkEpisode = onBatchMarkEpisode,
            onDismissRequest = onDismissRequest,
            sheetHandle = sheetHandle,
        )
        return
    }

    val sameGroupEpisodes =
        remember(uiState.episodes, effectiveEpisode?.type) {
            val targetType = effectiveEpisode?.type ?: 0
            uiState.episodes.filter { it.type == targetType }
        }
    val currentIndex =
        effectiveEpisode?.let { episode ->
            sameGroupEpisodes.indexOfFirst { it.id == episode.id }
        } ?: -1

    SubjectSourcesBottomSheet(
        subject = subject,
        episode = effectiveEpisode,
        isWatched = effectiveEpisode?.let { isEpisodeWatched(it, uiState.collection?.epStatus ?: 0) } ?: false,
        isNextToWatch =
            effectiveEpisode?.let {
                isEpisodeNextToWatch(
                    it,
                    uiState.collection?.epStatus ?: 0,
                    hasProgress = uiState.collection != null,
                )
            } ?: false,
        showEpisodePicker = showPicker || !supportsPlaybackSources,
        onCloseEpisodePicker =
            if (supportsPlaybackSources && effectiveEpisode != null) {
                { showPicker = false }
            } else {
                null
            },
        episodePickerContent = {
            EpisodesTabPage(
                uiState = uiState,
                subjectType = subjectType,
                totalEpisodes = totalEpisodes,
                currentEpisodes = groupedEpisodes[activeGroup].orEmpty(),
                availableGroups = availableGroups,
                groupedEpisodes = groupedEpisodes,
                activeGroup = activeGroup,
                onSelectGroup = onSelectGroup,
                isGridView = uiState.isEpisodeGridView,
                onToggleGridView = onToggleGridView,
                episodeSortDescending = uiState.episodeSortDescending,
                hasMoreEpisodes = uiState.hasMoreEpisodes,
                isLoadingMoreEpisodes = uiState.isLoadingMoreEpisodes,
                onToggleEpisodeSort = onToggleEpisodeSort,
                onLoadMoreEpisodes = onLoadMoreEpisodes,
                onRetryEpisodes = onRetryEpisodes,
                onToggleEpisodeWatched = onToggleEpisodeWatched,
                onSelectEpisodeForDetail = onOpenEpisodeDetails,
                onEpisodeClick = { episode ->
                    if (supportsPlaybackSources) {
                        selectedEpisode = episode
                        showPicker = false
                    } else {
                        onDismissRequest()
                        onOpenEpisodeDetails(episode)
                    }
                },
                onBatchMarkEpisode = onBatchMarkEpisode,
                onPlayEpisode = null,
                onOpenSources = null,
                onOpenEpisodeSources = null,
                onPlayNextEpisode = null,
                bottomPadding = 8.dp,
            )
        },
        onDismissRequest = onDismissRequest,
        sheetHandle = sheetHandle,
        onOpenUrl = onStreamingUrlLaunch,
        onToggleWatched =
            effectiveEpisode?.let { episode ->
                {
                    onToggleEpisodeWatched(
                        episode,
                        !isEpisodeWatched(episode, uiState.collection?.epStatus ?: 0),
                    )
                }
            },
        onBatchMark =
            effectiveEpisode
                ?.takeIf {
                    !isEpisodeWatched(it, uiState.collection?.epStatus ?: 0) && it.type == 0
                }?.let {
                    { onBatchMarkEpisode(it) }
                },
        onSelectEpisodeForDetail =
            effectiveEpisode?.let { episode ->
                {
                    onOpenEpisodeDetails(episode)
                }
            },
        onOpenEpisodePicker = {
            showPicker = true
        },
        onNavigateToPrevious =
            sameGroupEpisodes.getOrNull(currentIndex - 1)?.let { episode ->
                { selectedEpisode = episode }
            },
        onNavigateToNext =
            sameGroupEpisodes.getOrNull(currentIndex + 1)?.let { episode ->
                { selectedEpisode = episode }
            },
        currentIndex = currentIndex,
        totalCount = sameGroupEpisodes.size,
        onInternalPlayClick =
            onPlay?.let { play ->
                { route ->
                    onDismissRequest()
                    play(route)
                }
            },
        onAiSourceSearch =
            onAiSourceSearch?.let { search ->
                { search(effectiveEpisode) }
            },
        onManageRules =
            onManageRules?.let { manage ->
                {
                    onDismissRequest()
                    manage()
                }
            },
        playbackRules = uiState.playbackRules,
        failedSourceReasons = uiState.failedSourceReasons,
        torrentResources = uiState.torrentResources,
        isTorrentLoading = uiState.isTorrentLoading,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onRefreshTorrent = { onRefreshTorrent(effectiveEpisode) },
        onDownloadStarted = onDownloadStarted,
        isTorrentEpisodeFilterIgnored = uiState.isTorrentEpisodeFilterIgnored,
        onShowAllTorrents =
            onShowAllTorrents?.let { showAll ->
                { showAll(effectiveEpisode) }
            },
        onRestoreEpisodeFilter =
            onRestoreEpisodeFilter?.let { restore ->
                { restore(effectiveEpisode) }
            },
    )
}
