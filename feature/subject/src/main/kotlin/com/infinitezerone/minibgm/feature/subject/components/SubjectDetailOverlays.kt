package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.AiringReminderPermissionDialog
import com.infinitezerone.minibgm.core.designsystem.component.CollectionStatusBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
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

/**
 * 条目详情页的所有浮层与弹窗（快捷打卡、全集标记、登录提示、权限弹窗、详情抽屉与播放源抽屉）统一收敛组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubjectDetailOverlays(
    batchMarkTargetEpisode: Episode?,
    onDismissBatchMark: () -> Unit,
    onConfirmBatchMark: (Episode) -> Unit,
    appNotInstalledPrompt: Pair<String, String>?,
    onDismissAppNotInstalled: () -> Unit,
    onOpenAppNotInstalledWebUrl: (String) -> Unit,
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
    batchMarkTargetEpisode?.let { episode ->
        val targetEpNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
        AlertDialog(
            onDismissRequest = onDismissBatchMark,
            title = { Text("看到此集？") },
            text = { Text("是否将第 1 集至第 $targetEpNumber 集全部标记为已看过？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmBatchMark(episode)
                    },
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBatchMark) {
                    Text("取消")
                }
            },
        )
    }

    appNotInstalledPrompt?.let { (appName, webUrl) ->
        AlertDialog(
            onDismissRequest = onDismissAppNotInstalled,
            title = { Text("未安装 $appName 客户端") },
            text = { Text("未检测到 $appName 客户端，是否在应用内使用浏览器打开该播放源？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOpenAppNotInstalledWebUrl(webUrl)
                    },
                ) {
                    Text("浏览器打开")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAppNotInstalled) {
                    Text("取消")
                }
            },
        )
    }

    if (showLoginPromptDialog) {
        AlertDialog(
            onDismissRequest = onDismissLoginPrompt,
            icon = {
                Icon(
                    imageVector = BgmIcons.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            },
            title = {
                Text(
                    text = "请先登录 Bangumi 账号",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "追番、收藏与章节打卡需要同步至您的 Bangumi 账号，登录后即可随手收藏、打卡并同步进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(onClick = onLoginClick) {
                    Text("立即登录")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLoginPrompt) {
                    Text("稍后再说")
                }
            },
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
                val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
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
