package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import com.infinitezerone.minibgm.core.designsystem.component.AiringReminderPermissionDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.CollectionStatusBottomSheet
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 条目详情页的所有浮层与抽屉（快捷打卡、登录提示、权限弹窗、详情抽屉与播放源抽屉）统一收敛组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubjectDetailOverlays(
    showLoginPromptDialog: Boolean,
    onDismissLoginPrompt: () -> Unit,
    onLoginClick: () -> Unit,
    showCollectionSheet: Boolean,
    currentCollection: UserCollection?,
    subjectType: SubjectType,
    totalEpisodes: Int,
    popularTags: List<Tag>,
    onDismissCollectionSheet: () -> Unit,
    onSaveCollection: (CollectionType, Int?, String?, Boolean, Int?, List<String>?) -> Unit,
    selectedEpisodeForQuickAction: Episode?,
    onDismissQuickAction: () -> Unit,
    onToggleEpisodeWatched: (episode: Episode, isWatched: Boolean, epNumber: Int, episodeType: Int) -> Unit,
    onSelectEpisodeForDetail: (Episode) -> Unit,
    onBuildPlayerRoute: (Episode) -> PlayerRoute,
    onPlayClick: ((PlayerRoute) -> Unit)?,
    onOpenSourcesForEpisode: ((Episode) -> Unit)? = null,
    onBatchMarkRequest: (Episode) -> Unit,
    isLoggedIn: Boolean,
    haptic: HapticFeedback,
    previewCharacter: SubjectCharacter?,
    onDismissPreviewCharacter: () -> Unit,
    onViewCharacterDetail: (Long) -> Unit,
    activeCharacter: SubjectCharacter?,
    selectedCharacterDetail: CharacterDetail?,
    selectedCharacterWorks: List<RelatedWork>,
    isLoadingEntityDetail: Boolean,
    onDismissEntityDetail: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    activePerson: SubjectPerson?,
    selectedPersonDetail: PersonDetail?,
    selectedPersonWorks: List<RelatedWork>,
    showAiringReminderPrompt: Boolean,
    subjectTitle: String?,
    onConfirmAiringReminder: () -> Unit,
    onDismissAiringReminder: () -> Unit,
    showSourcesBottomSheet: Boolean,
    displaySubject: Subject?,
    selectedEpisodeForSources: Episode?,
    onDismissSourcesSheet: () -> Unit,
    onStreamingUrlLaunch: (String) -> Unit,
    onRequestSourceSearch: (Episode?) -> Unit,
    onManageRules: (() -> Unit)?,
    playbackRules: List<PlaybackSourceRule>,
    playlists: List<PlaybackPlaylist>,
    failedSourceReasons: Map<String, String>,
) {
    if (showLoginPromptDialog) {
        BgmLoginPromptDialog(
            description = stringResource(R.string.feature_subject_login_prompt_desc),
            onLogin = onLoginClick,
            onDismiss = onDismissLoginPrompt,
        )
    }

    if (showCollectionSheet) {
        CollectionStatusBottomSheet(
            currentCollection = currentCollection,
            subjectType = subjectType,
            totalEpisodes = totalEpisodes,
            popularTags = popularTags,
            onDismiss = onDismissCollectionSheet,
            onSave = onSaveCollection,
        )
    }

    selectedEpisodeForQuickAction?.let { episode ->
        val watchedCount = currentCollection?.epStatus ?: 0
        val isWatched = isEpisodeWatched(episode, watchedCount)
        val isNextToWatch = isEpisodeNextToWatch(episode, watchedCount, hasProgress = currentCollection != null)
        EpisodeQuickActionBottomSheet(
            episode = episode,
            isWatched = isWatched,
            isNextToWatch = isNextToWatch,
            onDismiss = onDismissQuickAction,
            onToggleWatched = {
                val epNumber = episode.episodeInt
                if (isLoggedIn) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                onToggleEpisodeWatched(episode, !isWatched, epNumber, episode.type)
            },
            onSelectEpisodeForDetail = {
                onSelectEpisodeForDetail(episode)
            },
            onPlayClick =
                if ((subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL) && onPlayClick != null) {
                    { onPlayClick(onBuildPlayerRoute(episode)) }
                } else {
                    null
                },
            onOpenSources =
                if ((subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL) && onOpenSourcesForEpisode != null) {
                    { onOpenSourcesForEpisode(episode) }
                } else {
                    null
                },
            onBatchMark =
                if (!isWatched && episode.type == 0) {
                    { onBatchMarkRequest(episode) }
                } else {
                    null
                },
        )
    }

    previewCharacter?.let { character ->
        CharacterImagePreviewDialog(
            character = character,
            onDismiss = onDismissPreviewCharacter,
            onViewDetail = { characterId ->
                onViewCharacterDetail(characterId)
            },
        )
    }

    activeCharacter?.let { character ->
        CharacterDetailBottomSheet(
            character = character,
            detail = selectedCharacterDetail,
            relatedWorks = selectedCharacterWorks,
            isLoading = isLoadingEntityDetail,
            onDismiss = onDismissEntityDetail,
            onSubjectClick = onSubjectClick,
            onActorClick = onActorClick,
        )
    }

    activePerson?.let { person ->
        PersonDetailBottomSheet(
            person = person,
            detail = selectedPersonDetail,
            relatedWorks = selectedPersonWorks,
            isLoading = isLoadingEntityDetail,
            onDismiss = onDismissEntityDetail,
            onSubjectClick = onSubjectClick,
        )
    }

    if (showAiringReminderPrompt) {
        AiringReminderPermissionDialog(
            subjectTitle = subjectTitle,
            onConfirm = onConfirmAiringReminder,
            onDismiss = onDismissAiringReminder,
        )
    }

    if (showSourcesBottomSheet && displaySubject != null) {
        SubjectSourcesBottomSheet(
            subject = displaySubject,
            episode = selectedEpisodeForSources,
            onDismissRequest = onDismissSourcesSheet,
            onOpenUrl = onStreamingUrlLaunch,
            onInternalPlayClick =
                onPlayClick?.let { play ->
                    { route ->
                        onDismissSourcesSheet()
                        play(route)
                    }
                },
            onAiSourceSearch = { onRequestSourceSearch(selectedEpisodeForSources) },
            onManageRules =
                onManageRules?.let { manage ->
                    {
                        onDismissSourcesSheet()
                        manage()
                    }
                },
            playbackRules = playbackRules,
            playlists = playlists,
            failedSourceReasons = failedSourceReasons,
        )
    }
}
