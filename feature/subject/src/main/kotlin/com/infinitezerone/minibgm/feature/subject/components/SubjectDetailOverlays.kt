package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.infinitezerone.minibgm.core.designsystem.component.AiringReminderPermissionDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.CollectionStatusBottomSheet
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 条目详情页的资料浮层与抽屉（登录提示、权限弹窗、收藏与人物详情）统一收敛组件。
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
}
