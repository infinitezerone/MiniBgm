package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmBottomSheetHandle
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesBottomSheet
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

/**
 * 条目页的统一分集选择浮层。
 *
 * 沿用分集工作区的分组、排序、视图切换与按需分页；点击分集后由调用方在同一个
 * Overlay request 内切换到播放源，而不是再弹第二层抽屉。
 */
@Composable
internal fun SubjectEpisodesBottomSheet(
    subject: Subject,
    subjectType: SubjectType,
    uiState: SubjectDetailUiState,
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
    onSelectEpisode: (Episode) -> Unit,
    onOpenEpisodeDetails: (Episode) -> Unit,
    onBatchMarkEpisode: (Episode) -> Unit,
    onDismissRequest: () -> Unit,
    sheetHandle: BgmBottomSheetHandle? = null,
) {
    BgmSourcesBottomSheet(
        title = subject.displayName,
        coverUrl = subject.images?.bestImage,
        sheetHandle = sheetHandle,
        subtitle =
            stringResource(
                when (subjectType) {
                    SubjectType.BOOK -> R.string.feature_subject_episode_picker_subtitle_volumes
                    SubjectType.MUSIC -> R.string.feature_subject_episode_picker_subtitle_tracks
                    SubjectType.GAME -> R.string.feature_subject_episode_picker_subtitle_chapters
                    SubjectType.ANIME, SubjectType.REAL -> R.string.feature_subject_episode_picker_subtitle
                },
            ),
        onDismissRequest = onDismissRequest,
        contentScrollable = false,
    ) {
        EpisodesTabPage(
            uiState = uiState,
            subjectType = subjectType,
            totalEpisodes = totalEpisodes,
            currentEpisodes = currentEpisodes,
            availableGroups = availableGroups,
            groupedEpisodes = groupedEpisodes,
            activeGroup = activeGroup,
            onSelectGroup = onSelectGroup,
            isGridView = isGridView,
            onToggleGridView = onToggleGridView,
            episodeSortDescending = episodeSortDescending,
            hasMoreEpisodes = hasMoreEpisodes,
            isLoadingMoreEpisodes = isLoadingMoreEpisodes,
            onToggleEpisodeSort = onToggleEpisodeSort,
            onLoadMoreEpisodes = onLoadMoreEpisodes,
            onRetryEpisodes = onRetryEpisodes,
            onToggleEpisodeWatched = onToggleEpisodeWatched,
            onSelectEpisodeForDetail = onOpenEpisodeDetails,
            onEpisodeClick = onSelectEpisode,
            onBatchMarkEpisode = onBatchMarkEpisode,
            onPlayEpisode = null,
            onOpenSources = null,
            onOpenEpisodeSources = null,
            onPlayNextEpisode = null,
            bottomPadding = 8.dp,
        )
    }
}
