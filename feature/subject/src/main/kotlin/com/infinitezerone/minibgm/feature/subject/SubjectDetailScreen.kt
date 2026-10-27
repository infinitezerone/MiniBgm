package com.infinitezerone.minibgm.feature.subject

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.designsystem.ambient.AmbientBlurBackdrop
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingPageScaffold
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarChrome
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.TagActionBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.isNavEntering
import com.infinitezerone.minibgm.core.navigation.launchStreamingUrl
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailContent
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailFullSkeleton
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailOverlays
import com.infinitezerone.minibgm.feature.subject.components.SubjectEpisodeOverlay
import com.infinitezerone.minibgm.feature.subject.components.SubjectEpisodeOverlayRequest
import com.infinitezerone.minibgm.feature.subject.components.SubjectHeaderCollapseState
import com.infinitezerone.minibgm.feature.subject.components.rememberDownloadNotificationPermissionRequester
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubjectDetailScreen(
    subjectId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialCoverUrl: String = "",
    initialScore: Double = 0.0,
    source: String = "",
    initialEpisodeNumber: Int? = null,
    onSubjectClick: (Long) -> Unit = {},
    onEpisodeClick: (EpisodeDetailRoute) -> Unit = {},
    onPlayClick: ((PlayerRoute) -> Unit)? = null,
    onTagClick: (String) -> Unit = {},
    onTopicClick: (Long, String) -> Unit = { _, _ -> },
    onCharacterClick: ((Long) -> Unit)? = null,
    onPersonClick: ((Long) -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    onSourceSearch: ((String) -> Unit)? = null,
    onLoginRequest: () -> Unit = {},
    onDownloadsClick: (() -> Unit)? = null,
    viewModel: SubjectDetailViewModel = koinViewModel(parameters = { parametersOf(subjectId) }),
) {
    val context = LocalContext.current
    val handleCharacterClick: (Long) -> Unit = { characterId ->
        if (onCharacterClick != null) {
            onCharacterClick(characterId)
        } else {
            viewModel.openCharacterDetail(characterId)
        }
    }
    val handlePersonClick: (Long) -> Unit = { personId ->
        if (onPersonClick != null) {
            onPersonClick(personId)
        } else {
            viewModel.openPersonDetail(personId)
        }
    }

    val onSelectEpisodeForDetail: (Episode) -> Unit = { episode ->
        onEpisodeClick(
            EpisodeDetailRoute(
                episodeId = episode.id,
                subjectId = subjectId,
                episodeSort = episode.episodeNumber,
                episodeType = episode.type,
                episodeName = episode.name,
                episodeNameCn = episode.nameCn,
            ),
        )
    }

    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()
    var hasDismissedAiringReminderPrompt by rememberSaveable { mutableStateOf(false) }
    var showAiringReminderPrompt by remember { mutableStateOf(false) }

    val clipboard = LocalClipboard.current
    val copiedContentText = stringResource(R.string.feature_subject_ep_copied_content)
    val handleCopyComment: (String) -> Unit =
        remember(clipboard, coroutineScope, snackbarHostState, copiedContentText) {
            { text ->
                coroutineScope.launch {
                    val clipEntry = ClipEntry(ClipData.newPlainText("comment", text))
                    clipboard.setClipEntry(clipEntry)
                    snackbarHostState.showSnackbar(copiedContentText)
                }
            }
        }

    val defaultTitle = stringResource(R.string.feature_subject_title)
    val epFormat = stringResource(R.string.feature_subject_check_in_ep_format)
    val epMarkSuccessFormat = stringResource(R.string.feature_subject_mark_ep_success)
    val batchMarkSuccessFormat = stringResource(R.string.feature_subject_mark_batch_success)
    val undoText = stringResource(R.string.feature_subject_undo)
    val airingReminderSuccess = stringResource(R.string.feature_subject_airing_reminder_enabled)
    val notInstalledTitleFormat = stringResource(R.string.feature_subject_app_not_installed_title)
    val notInstalledMessageFormat = stringResource(R.string.feature_subject_app_not_installed_message)
    val openBrowserText = stringResource(DesignSystemR.string.core_designsystem_action_open_browser)
    val cancelText = stringResource(DesignSystemR.string.core_designsystem_action_cancel)
    val batchWatchTitle = stringResource(R.string.feature_subject_batch_watch_dialog_title)
    val batchWatchMessageFormat = stringResource(R.string.feature_subject_batch_watch_dialog_message)
    val confirmText = stringResource(DesignSystemR.string.core_designsystem_action_confirm)
    val shareChooserTitle = stringResource(R.string.feature_subject_cd_share)

    ObserveAsEvents(viewModel.uiEvents) { event ->
        when (event) {
            is SubjectDetailUiEvent.ShowMessage -> {
                snackbarHostState.showSnackbar(
                    event.serverMessage ?: context.resources.getString(event.resId, *event.formatArgs.toTypedArray()),
                )
            }
            is SubjectDetailUiEvent.OpenSourceSearch -> {
                onSourceSearch?.invoke(context.resources.getString(event.promptResId, *event.formatArgs.toTypedArray()))
            }
            is SubjectDetailUiEvent.EpisodeMarked -> {
                val epLabel =
                    if (event.episodeType == 0) {
                        String.format(java.util.Locale.getDefault(), epFormat, event.epNumber)
                    } else {
                        "${EpisodeGroup.fromType(event.episodeType).label} ${event.epNumber}"
                    }
                val snackbarResult =
                    snackbarHostState.showSnackbar(
                        message = String.format(java.util.Locale.getDefault(), epMarkSuccessFormat, epLabel),
                        actionLabel = undoText,
                        duration = SnackbarDuration.Short,
                    )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    viewModel.undoMarkWatchedUpTo(
                        previousEpStatus = event.previousEpStatus,
                        previousType = event.previousType,
                        undoneEpisodeIds = listOf(event.episodeId),
                    )
                }
            }
            is SubjectDetailUiEvent.BatchMarked -> {
                val snackbarResult =
                    snackbarHostState.showSnackbar(
                        message = String.format(java.util.Locale.getDefault(), batchMarkSuccessFormat, event.targetEpNumber),
                        actionLabel = undoText,
                        duration = SnackbarDuration.Short,
                    )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    viewModel.undoMarkWatchedUpTo(
                        previousEpStatus = event.previousEpStatus,
                        previousType = event.previousType,
                        undoneEpisodeIds = event.episodeIds,
                    )
                }
            }
        }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.enableAiringReminder()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(airingReminderSuccess)
                }
            }
        }
    // 离线下载的通知栏是唯一的外部门控面，首次下载前必须确保通知权限已就绪
    val requestDownloadNotification = rememberDownloadNotificationPermissionRequester()
    val isEntering = isNavEntering()
    var hasEnteredTransitionFinished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isEntering) {
        if (!isEntering) {
            hasEnteredTransitionFinished = true
        }
    }
    LaunchedEffect(uiState.selectedTab) {
        when (uiState.selectedTab) {
            SubjectDetailTab.OVERVIEW -> viewModel.loadDetailsTabIfNeeded()
            SubjectDetailTab.COMMUNITY -> viewModel.loadCommunityTabIfNeeded()
        }
    }
    val hasPreview = initialName.isNotBlank() || initialCoverUrl.isNotBlank()
    val isTransitionStabilizing =
        isEntering && !hasEnteredTransitionFinished && hasPreview && uiState.subject == null

    val previewSubject =
        if (hasPreview) {
            remember(subjectId, initialName, initialCoverUrl, initialScore) {
                Subject(
                    id = subjectId,
                    type = uiState.subject?.type ?: SubjectType.ANIME.value,
                    name = initialName,
                    nameCn = initialName,
                    images =
                        if (initialCoverUrl.isNotBlank()) {
                            SubjectImages(
                                large = initialCoverUrl,
                                common = initialCoverUrl,
                                medium = initialCoverUrl,
                                small = initialCoverUrl,
                                grid = initialCoverUrl,
                            )
                        } else {
                            null
                        },
                    rating =
                        if (initialScore > 0.0) {
                            Rating(score = initialScore)
                        } else {
                            null
                        },
                )
            }
        } else {
            null
        }

    val displaySubject =
        if (isTransitionStabilizing) {
            previewSubject
        } else {
            uiState.subject ?: previewSubject
        }
    val subjectType = displaySubject?.type?.let { SubjectType.fromValue(it) } ?: SubjectType.ANIME
    var previewCharacter by remember { mutableStateOf<SubjectCharacter?>(null) }

    val handleShare: () -> Unit = {
        val shareTitle = displaySubject?.displayName ?: defaultTitle
        val shareText = "$shareTitle https://bgm.tv/subject/$subjectId"
        val sendIntent =
            android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
        context.startActivity(android.content.Intent.createChooser(sendIntent, shareChooserTitle))
    }

    val handleStreamingUrl: (String) -> Unit = { url ->
        context.launchStreamingUrl(
            url = url,
            onAppNotInstalled = { appName, webUrl ->
                coroutineScope.launch {
                    val openInBrowser =
                        overlayHostState.await(
                            ConfirmDialogAction(
                                title = String.format(java.util.Locale.getDefault(), notInstalledTitleFormat, appName),
                                message = String.format(java.util.Locale.getDefault(), notInstalledMessageFormat, appName),
                                confirmText = openBrowserText,
                                dismissText = cancelText,
                            ),
                        )
                    if (openInBrowser) {
                        context.launchWebUrl(webUrl)
                    }
                }
            },
        )
    }

    val handleBatchMark: (Episode) -> Unit = { episode ->
        if (!uiState.isLoggedIn) {
            viewModel.promptLogin()
        } else {
            coroutineScope.launch {
                val targetEpNumber = episode.episodeInt
                val confirmed =
                    overlayHostState.await(
                        ConfirmDialogAction(
                            title = batchWatchTitle,
                            message = String.format(java.util.Locale.getDefault(), batchWatchMessageFormat, targetEpNumber),
                            confirmText = confirmText,
                            dismissText = cancelText,
                        ),
                    )
                if (confirmed) {
                    viewModel.markWatchedUpTo(episode)
                }
            }
        }
    }

    val handleLinkClick: (String) -> Unit = { url ->
        when (val link = BgmUrlParser.parse(url)) {
            is BgmLink.Subject -> {
                viewModel.dismissEpisodeDetail()
                onSubjectClick(link.subjectId)
            }
            is BgmLink.Character -> {
                viewModel.dismissEpisodeDetail()
                handleCharacterClick(link.characterId)
            }
            is BgmLink.Person -> {
                viewModel.dismissEpisodeDetail()
                handlePersonClick(link.personId)
            }
            is BgmLink.Episode -> {
                val ep = uiState.episodes.firstOrNull { it.id == link.episodeId }
                if (ep != null) {
                    onSelectEpisodeForDetail(ep)
                } else {
                    onEpisodeClick(
                        EpisodeDetailRoute(
                            episodeId = link.episodeId,
                            subjectId = subjectId,
                        ),
                    )
                }
            }
            is BgmLink.Topic -> onTopicClick(link.topicId, "")
            is BgmLink.User -> context.launchWebUrl(url)
            is BgmLink.External -> handleStreamingUrl(url)
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
            orderedEpisodes.groupBy { EpisodeGroup.fromType(it.type) }
        }
    val availableGroups =
        remember(groupedEpisodes) {
            EpisodeGroup.entries.filter { groupedEpisodes.containsKey(it) }
        }
    var selectedGroup by rememberSaveable {
        mutableStateOf(EpisodeGroup.MAIN)
    }
    val activeGroup = if (selectedGroup in availableGroups) selectedGroup else availableGroups.firstOrNull() ?: EpisodeGroup.MAIN
    val currentEpisodes = groupedEpisodes[activeGroup] ?: emptyList()
    val resolvedTotalEpisodes =
        displaySubject?.let { maxOf(uiState.totalEpisodes, it.eps, it.totalEpisodes) } ?: 0
    val toggleEpisodeWatched: (Episode, Boolean) -> Unit = { episode, isWatched ->
        if (uiState.isLoggedIn) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        viewModel.toggleEpisodeWatched(
            episodeId = episode.id,
            isWatched = isWatched,
            epNumber = episode.episodeInt,
            episodeType = episode.type,
        )
    }
    val openEpisodeOverlay: (Episode?, Boolean) -> Unit = { episode, startInPicker ->
        coroutineScope.launch {
            overlayHostState.await(
                SubjectEpisodeOverlayRequest(
                    initialEpisode = episode,
                    startInPicker = startInPicker,
                ),
            )
        }
    }

    var hasAutoOpenedInitialEpisode by rememberSaveable(subjectId, initialEpisodeNumber) {
        mutableStateOf(false)
    }
    LaunchedEffect(uiState.episodes, displaySubject, initialEpisodeNumber) {
        if (!hasAutoOpenedInitialEpisode && displaySubject != null) {
            val targetEpisode =
                initialEpisodeNumber?.let { number ->
                    uiState.episodes.firstOrNull { it.episodeInt == number }
                }
            if (targetEpisode != null) {
                hasAutoOpenedInitialEpisode = true
                openEpisodeOverlay(targetEpisode, false)
            }
        }
    }

    var activeTagForAction by rememberSaveable { mutableStateOf<String?>(null) }
    val tagActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topChromeHeight = statusBarTop + BgmSpacing.TopBar.height

    val density = LocalDensity.current
    val topBarBottomPx = with(density) { (statusBarTop + BgmSpacing.TopBar.height).toPx() }
    val contentTopPadding = topChromeHeight + 8.dp
    val fadeRangePx = with(density) { 200.dp.toPx() }
    val maxParallaxPx = with(density) { 420.dp.toPx() }
    var titleBounds by remember { mutableStateOf<Rect?>(null) }
    val headerCollapse = remember { SubjectHeaderCollapseState() }
    val ambientAlpha by remember(fadeRangePx) {
        derivedStateOf {
            val height = headerCollapse.heightPx
            if (height <= 0f) {
                1f
            } else {
                // Tab 栏距顶栏底边的剩余距离：头部展开时最远，收合完成后归零
                ((height - headerCollapse.offsetPx) / fadeRangePx).coerceIn(0f, 1f)
            }
        }
    }
    val heroScrollPx by remember(maxParallaxPx) {
        derivedStateOf { headerCollapse.offsetPx.coerceIn(0f, maxParallaxPx) }
    }
    val backdropTranslationY = -heroScrollPx * 0.25f
    val collapseProgress by remember(topBarBottomPx) {
        derivedStateOf {
            val headerScrolledPast =
                headerCollapse.heightPx > 0f && headerCollapse.offsetPx >= headerCollapse.heightPx - 0.5f
            val bounds = titleBounds
            if (bounds == null) {
                if (headerScrolledPast) 1f else 0f
            } else {
                calculateSubjectTitleCollapseProgress(
                    titleBounds = bounds,
                    topBarBottomPx = topBarBottomPx,
                    headerScrolledPast = headerScrolledPast,
                )
            }
        }
    }

    BgmFloatingPageScaffold(
        topBarMode = BgmTopBarMode.Immersive,
        topBar = {
            BgmTopAppBar(
                mode = BgmTopBarMode.Immersive,
                chrome = { BgmTopBarChrome.collapsing(collapseProgress) },
                contentMaxWidth = 840.dp,
                title = {
                    Text(
                        text = displaySubject?.displayName ?: defaultTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BgmCircleActionButton(
                        onClick = onBackClick,
                        icon = BgmIcons.ArrowBack,
                        contentDescription = stringResource(R.string.feature_subject_cd_back),
                    )
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        BgmCircleActionButton(
                            onClick = {
                                context.launchWebUrl("https://bgm.tv/subject/$subjectId")
                            },
                            icon = BgmIcons.OpenInBrowser,
                            contentDescription = stringResource(R.string.feature_subject_open_in_browser),
                        )
                    }
                },
            )
        },
        snackbarHost = { BgmSnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) {
        if (hasEnteredTransitionFinished || !isEntering) {
            AmbientBlurBackdrop(
                imageUrl = displaySubject?.images?.bestImage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .graphicsLayer {
                            alpha = ambientAlpha
                            translationY = backdropTranslationY
                        },
            )
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                viewModel.refresh(isUserPullToRefresh = true)
                when (uiState.selectedTab) {
                    SubjectDetailTab.OVERVIEW -> viewModel.loadDetailsTabIfNeeded(force = true)
                    SubjectDetailTab.COMMUNITY -> viewModel.loadCommunityTabIfNeeded(force = true)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    displaySubject == null && uiState.isLoading -> {
                        SubjectDetailFullSkeleton(topPadding = contentTopPadding)
                    }

                    displaySubject == null && uiState.error != null -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(top = topChromeHeight)
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = stringResource(R.string.feature_subject_load_failed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = uiState.error.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                BgmPillButton(
                                    onClick = viewModel::refresh,
                                    text = stringResource(R.string.feature_subject_reload),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }

                    displaySubject != null -> {
                        val fullSubject = if (isTransitionStabilizing) null else uiState.subject

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            SubjectDetailContent(
                                displaySubject = displaySubject,
                                fullSubject = fullSubject,
                                subjectType = subjectType,
                                uiState = uiState,
                                source = source,
                                totalEpisodes = resolvedTotalEpisodes,
                                selectedTab = uiState.selectedTab,
                                onSelectTab = viewModel::selectTab,
                                onOpenCollectionSheet = { viewModel.setCollectionSheetVisible(true) },
                                onIncrementWatched = viewModel::incrementWatchedEpisode,
                                onUpdateCollectionStatus = { type ->
                                    viewModel.updateCollectionStatus(type)
                                    if ((type == CollectionType.DOING || type == CollectionType.WISH) && uiState.isLoggedIn) {
                                        val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                        if (!systemAllowed && !hasDismissedAiringReminderPrompt) {
                                            showAiringReminderPrompt = true
                                        }
                                    }
                                },
                                onSubjectClick = onSubjectClick,
                                onTagClick = { tag -> activeTagForAction = tag },
                                onCharacterClick = handleCharacterClick,
                                onPersonClick = handlePersonClick,
                                onPreviewCharacter = { previewCharacter = it },
                                onLinkClick = handleLinkClick,
                                onTopicClick = onTopicClick,
                                onLoadMoreComments = { viewModel.loadMoreSubjectComments() },
                                isTransitionStabilizing = isTransitionStabilizing,
                                onCopyComment = handleCopyComment,
                                onPlayNextEpisode =
                                    if (onPlayClick != null) {
                                        {
                                            viewModel.nextEpisodeToWatch()?.let { nextEp ->
                                                onPlayClick(viewModel.buildPlayerRoute(nextEp))
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                onPlayNextLongClick = {
                                    viewModel.nextEpisodeToWatch()?.let { nextEp ->
                                        openEpisodeOverlay(nextEp, false)
                                    }
                                },
                                onOpenEpisodePicker = { openEpisodeOverlay(null, true) },
                                onSelectEpisode = { episode -> openEpisodeOverlay(episode, false) },
                                onDecrementWatched = viewModel::decrementWatchedEpisode,
                                onPromptLogin = viewModel::promptLogin,
                                onShare = handleShare,
                                topPadding = topChromeHeight,
                                headerCollapse = headerCollapse,
                                onTitleBoundsChanged = { bounds ->
                                    // Lazy 项被回收/预取时可能回调空矩形；忽略它，避免顶栏在滚过标题后重新变透明。
                                    if (bounds.height > 0f) titleBounds = bounds
                                },
                                onRetry = viewModel::refresh,
                                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    SubjectDetailOverlays(
        showLoginPromptDialog = uiState.showLoginPromptDialog,
        onDismissLoginPrompt = viewModel::dismissLoginPrompt,
        onLoginClick = {
            // 登录页是独立路由（应用内 WebView + ECH 通道）：本页只负责发起并收起提示
            viewModel.dismissLoginPrompt()
            onLoginRequest()
        },
        showCollectionSheet = uiState.showCollectionSheet,
        currentCollection = uiState.collection,
        subjectType = subjectType,
        totalEpisodes = (displaySubject?.eps?.takeIf { it > 0 } ?: displaySubject?.totalEpisodes) ?: 0,
        popularTags = uiState.subject?.tags ?: emptyList(),
        onDismissCollectionSheet = { viewModel.setCollectionSheetVisible(false) },
        onSaveCollection = { type, rate, comment, private, epStatus, tags ->
            viewModel.updateCollectionStatus(
                type = type,
                rate = rate,
                comment = comment,
                private = private,
                epStatus = epStatus,
                tags = tags,
            )
            if (type == CollectionType.DOING && uiState.isLoggedIn) {
                val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (!systemAllowed && !hasDismissedAiringReminderPrompt) {
                    showAiringReminderPrompt = true
                }
            }
        },
        previewCharacter = previewCharacter,
        onDismissPreviewCharacter = { previewCharacter = null },
        onViewCharacterDetail = handleCharacterClick,
        activeCharacter = uiState.activeCharacter,
        selectedCharacterDetail = uiState.selectedCharacterDetail,
        selectedCharacterWorks = uiState.selectedCharacterWorks,
        isLoadingEntityDetail = uiState.isLoadingEntityDetail,
        onDismissEntityDetail = viewModel::dismissEntityDetail,
        onSubjectClick = { relSubjectId ->
            viewModel.dismissEntityDetail()
            onSubjectClick(relSubjectId)
        },
        onActorClick = { actorId ->
            viewModel.openPersonDetail(actorId)
        },
        activePerson = uiState.activePerson,
        selectedPersonDetail = uiState.selectedPersonDetail,
        selectedPersonWorks = uiState.selectedPersonWorks,
        showAiringReminderPrompt = showAiringReminderPrompt,
        subjectTitle = uiState.subject?.nameCn?.ifBlank { uiState.subject?.name } ?: initialName.ifBlank { null },
        onConfirmAiringReminder = {
            showAiringReminderPrompt = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.enableAiringReminder()
            }
        },
        onDismissAiringReminder = {
            showAiringReminderPrompt = false
            hasDismissedAiringReminderPrompt = true
        },
    )

    if (activeTagForAction != null) {
        TagActionBottomSheet(
            tag = activeTagForAction!!,
            isFavorite = activeTagForAction!! in uiState.customFilterTags,
            sheetState = tagActionSheetState,
            onToggleFavorite = {
                viewModel.toggleCustomFilterTag(activeTagForAction!!)
            },
            onDismiss = { activeTagForAction = null },
            onViewAllWithTag = {
                val t = activeTagForAction!!
                activeTagForAction = null
                onTagClick(t)
            },
        )
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
        overlay<SubjectEpisodeOverlayRequest, Unit> { request, onRespond ->
            val currentSubject = displaySubject
            if (currentSubject == null) {
                LaunchedEffect(request) {
                    onRespond(Unit)
                }
                return@overlay
            }
            SubjectEpisodeOverlay(
                request = request,
                subject = currentSubject,
                subjectType = subjectType,
                uiState = uiState,
                totalEpisodes = resolvedTotalEpisodes,
                activeGroup = activeGroup,
                onSelectGroup = { selectedGroup = it },
                onToggleGridView = { viewModel.setEpisodeGridView(!uiState.isEpisodeGridView) },
                onToggleEpisodeSort = { viewModel.setEpisodeSortDescending(!uiState.episodeSortDescending) },
                onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                onRetryEpisodes = viewModel::retryLoadEpisodes,
                onToggleEpisodeWatched = toggleEpisodeWatched,
                onOpenEpisodeDetails = onSelectEpisodeForDetail,
                onBatchMarkEpisode = handleBatchMark,
                onPlay = onPlayClick,
                onStreamingUrlLaunch = handleStreamingUrl,
                onAiSourceSearch = onSourceSearch?.let { { episode -> viewModel.requestSourceSearch(episode) } },
                onManageRules = onManageRules,
                onRequestNotificationPermission = requestDownloadNotification,
                onRefreshTorrent = { episode -> viewModel.refreshTorrentResources(episode) },
                onShowAllTorrents = { episode -> viewModel.showAllTorrentsWithoutEpisodeFilter(episode) },
                onRestoreEpisodeFilter = { episode -> viewModel.refreshTorrentResources(episode) },
                onDownloadStarted = {
                    coroutineScope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = context.resources.getString(R.string.feature_subject_source_torrent_download_started),
                                actionLabel =
                                    if (onDownloadsClick != null) {
                                        context.resources.getString(R.string.feature_subject_source_torrent_view_downloads)
                                    } else {
                                        null
                                    },
                            )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            onDownloadsClick?.invoke()
                        }
                    }
                },
                onEpisodeChanged = viewModel::loadTorrentResources,
                onDismissRequest = { onRespond(Unit) },
            )
        }
    }
}

/**
 * 标题顶部碰到顶栏底边时开始过渡，标题整体进入顶栏覆盖区时完成过渡。
 * 使用标题自身高度作为过渡距离，天然适配两行标题、字体缩放与宽屏布局。
 * [headerScrolledPast] 表示头部项已整体滚出视口，此时必须锁定完成态，
 * 不能因为 Lazy 项回收时上报空 bounds 而退回透明。
 */
internal fun calculateSubjectTitleCollapseProgress(
    titleBounds: Rect?,
    topBarBottomPx: Float,
    headerScrolledPast: Boolean = false,
): Float {
    if (headerScrolledPast) return 1f
    if (titleBounds == null || titleBounds.height <= 0f) return 0f
    return ((topBarBottomPx - titleBounds.top) / titleBounds.height).coerceIn(0f, 1f)
}
