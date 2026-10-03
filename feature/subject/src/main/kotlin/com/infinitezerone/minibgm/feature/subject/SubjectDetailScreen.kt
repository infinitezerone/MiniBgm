package com.infinitezerone.minibgm.feature.subject

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.designsystem.ambient.AmbientBlurBackdrop
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
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
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailContent
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailFullSkeleton
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailOverlays
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
                episodeSort = if (episode.ep > 0f) episode.ep else episode.sort,
                episodeType = episode.type,
                episodeName = episode.name,
                episodeNameCn = episode.nameCn,
            ),
        )
    }

    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var batchMarkTargetEpisode by remember { mutableStateOf<Episode?>(null) }
    var selectedEpisodeForQuickAction by remember { mutableStateOf<Episode?>(null) }
    var hasDismissedAiringReminderPrompt by rememberSaveable { mutableStateOf(false) }
    var showAiringReminderPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is SubjectDetailUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SubjectDetailUiEvent.OpenSourceSearch -> {
                    onSourceSearch?.invoke(event.prefillPrompt)
                }
                is SubjectDetailUiEvent.EpisodeMarked -> {
                    val group = EpisodeGroup.fromType(event.episodeType)
                    val epLabel = if (event.episodeType == 0) "第 ${event.epNumber} 话" else "${group.label} ${event.epNumber}"
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message = "已标记 $epLabel",
                            actionLabel = "撤销",
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
                            message = "已标记至第 ${event.targetEpNumber} 集",
                            actionLabel = "撤销",
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
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.enableAiringReminder()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("已开启追番开播提醒")
                }
            }
        }
    val isEntering = isNavEntering()
    var hasEnteredTransitionFinished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isEntering) {
        if (!isEntering) {
            hasEnteredTransitionFinished = true
        }
    }
    val hasPreview = initialName.isNotBlank() || initialCoverUrl.isNotBlank()
    val isTransitionStabilizing = isEntering && !hasEnteredTransitionFinished && hasPreview

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
    var appNotInstalledPrompt by remember { mutableStateOf<Pair<String, String>?>(null) }

    val handleStreamingUrl: (String) -> Unit = { url ->
        context.launchStreamingUrl(
            url = url,
            onAppNotInstalled = { appName, webUrl ->
                appNotInstalledPrompt = appName to webUrl
            },
        )
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

    LaunchedEffect(subjectType, uiState.episodes) {
        if (subjectType == SubjectType.GAME && uiState.episodes.isEmpty() && uiState.selectedTab == SubjectDetailTab.EPISODES) {
            viewModel.selectTab(SubjectDetailTab.DETAILS)
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
    var showSourcesBottomSheet by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodeForSources by remember { mutableStateOf<Episode?>(null) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        if (hasEnteredTransitionFinished || !isEntering) {
            AmbientBlurBackdrop(
                imageUrl = displaySubject?.images?.bestImage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
            )
        }

        Scaffold(
            topBar = {
                BgmTopAppBar(
                    title = {
                        Text(
                            text = displaySubject?.displayName ?: "条目详情",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = BgmIcons.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                context.launchWebUrl("https://bgm.tv/subject/$subjectId")
                            },
                        ) {
                            Icon(
                                imageVector = BgmIcons.OpenInBrowser,
                                contentDescription = "在浏览器中打开",
                            )
                        }
                        IconButton(
                            onClick = {
                                val shareTitle = displaySubject?.displayName ?: "条目详情"
                                val shareText = "$shareTitle https://bgm.tv/subject/$subjectId"
                                val sendIntent =
                                    android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "分享条目"))
                            },
                        ) {
                            Icon(
                                imageVector = BgmIcons.Share,
                                contentDescription = "分享",
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            },
            snackbarHost = { BgmSnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    viewModel.refresh(isUserPullToRefresh = true)
                    when (uiState.selectedTab) {
                        SubjectDetailTab.EPISODES -> Unit
                        SubjectDetailTab.DETAILS -> viewModel.loadDetailsTabIfNeeded(force = true)
                        SubjectDetailTab.COMMUNITY -> viewModel.loadCommunityTabIfNeeded(force = true)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        displaySubject == null && uiState.isLoading -> {
                            SubjectDetailFullSkeleton()
                        }

                        displaySubject == null && uiState.error != null -> {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Card(
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(48.dp),
                                        )
                                        Text(
                                            text = "条目加载失败",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(
                                            text = uiState.error.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Button(
                                            onClick = viewModel::refresh,
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            Text(text = "重新加载")
                                        }
                                    }
                                }
                            }
                        }

                        displaySubject != null -> {
                            val totalEpisodes =
                                if (displaySubject.eps > 0) displaySubject.eps else displaySubject.totalEpisodes
                            val fullSubject = if (isTransitionStabilizing) null else uiState.subject

                            val onToggleEpisodeWatched: (Episode, Boolean) -> Unit = { episode, isWatched ->
                                if (uiState.isLoggedIn) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                }
                                val epNumber =
                                    if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
                                viewModel.toggleEpisodeWatched(
                                    episodeId = episode.id,
                                    isWatched = isWatched,
                                    epNumber = epNumber,
                                    episodeType = episode.type,
                                )
                            }

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
                                    totalEpisodes = totalEpisodes,
                                    selectedTab = uiState.selectedTab,
                                    onSelectTab = viewModel::selectTab,
                                    currentEpisodes = currentEpisodes,
                                    availableGroups = availableGroups,
                                    groupedEpisodes = groupedEpisodes,
                                    activeGroup = activeGroup,
                                    onSelectGroup = { selectedGroup = it },
                                    isGridView = uiState.isEpisodeGridView,
                                    onToggleGridView = { viewModel.setEpisodeGridView(!uiState.isEpisodeGridView) },
                                    episodeSortDescending = uiState.episodeSortDescending,
                                    hasMoreEpisodes = uiState.hasMoreEpisodes,
                                    isLoadingMoreEpisodes = uiState.isLoadingMoreEpisodes,
                                    onToggleEpisodeSort = {
                                        viewModel.setEpisodeSortDescending(!uiState.episodeSortDescending)
                                    },
                                    onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                                    onOpenCollectionSheet = { viewModel.setCollectionSheetVisible(true) },
                                    onIncrementWatched = viewModel::incrementWatchedEpisode,
                                    onEpisodeClickForQuickAction = { episode ->
                                        selectedEpisodeForQuickAction = episode
                                    },
                                    onToggleWatching = {
                                        val wasWatching = uiState.collection?.type == CollectionType.DOING.value
                                        viewModel.toggleWatching()
                                        if (!wasWatching && uiState.isLoggedIn) {
                                            val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                            if (!systemAllowed && !hasDismissedAiringReminderPrompt) {
                                                showAiringReminderPrompt = true
                                            }
                                        }
                                    },
                                    onUpdateCollectionStatus = { type ->
                                        viewModel.updateCollectionStatus(type)
                                        if (type == CollectionType.DOING && uiState.isLoggedIn) {
                                            val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                            if (!systemAllowed && !hasDismissedAiringReminderPrompt) {
                                                showAiringReminderPrompt = true
                                            }
                                        }
                                    },
                                    onToggleEpisodeWatched = onToggleEpisodeWatched,
                                    onSelectEpisodeForDetail = onSelectEpisodeForDetail,
                                    onSubjectClick = onSubjectClick,
                                    onTagClick = onTagClick,
                                    onCharacterClick = handleCharacterClick,
                                    onPersonClick = handlePersonClick,
                                    onPreviewCharacter = { previewCharacter = it },
                                    onLinkClick = handleLinkClick,
                                    onTopicClick = onTopicClick,
                                    onLoadMoreComments = { viewModel.loadMoreSubjectComments() },
                                    isTransitionStabilizing = isTransitionStabilizing,
                                    onBatchMarkEpisode = { episode ->
                                        if (!uiState.isLoggedIn) {
                                            viewModel.promptLogin()
                                        } else {
                                            batchMarkTargetEpisode = episode
                                        }
                                    },
                                    onPlayEpisode =
                                        if (onPlayClick != null) {
                                            { episode -> onPlayClick(viewModel.buildPlayerRoute(episode)) }
                                        } else {
                                            null
                                        },
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
                                    onOpenSources = {
                                        selectedEpisodeForSources = viewModel.nextEpisodeToWatch()
                                        showSourcesBottomSheet = true
                                    },
                                    onOpenEpisodeSources = { episode ->
                                        selectedEpisodeForSources = episode
                                        showSourcesBottomSheet = true
                                    },
                                    onRetryEpisodes = viewModel::retryLoadEpisodes,
                                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SubjectDetailOverlays(
        batchMarkTargetEpisode = batchMarkTargetEpisode,
        onDismissBatchMark = { batchMarkTargetEpisode = null },
        onConfirmBatchMark = { episode ->
            batchMarkTargetEpisode = null
            viewModel.markWatchedUpTo(episode)
        },
        appNotInstalledPrompt = appNotInstalledPrompt,
        onDismissAppNotInstalled = { appNotInstalledPrompt = null },
        onOpenAppNotInstalledWebUrl = { webUrl ->
            appNotInstalledPrompt = null
            context.launchWebUrl(webUrl)
        },
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
        selectedEpisodeForQuickAction = selectedEpisodeForQuickAction,
        onDismissQuickAction = { selectedEpisodeForQuickAction = null },
        onToggleEpisodeWatched = { episode, isWatched, epNumber, episodeType ->
            viewModel.toggleEpisodeWatched(
                episodeId = episode.id,
                isWatched = isWatched,
                epNumber = epNumber,
                episodeType = episodeType,
            )
        },
        onSelectEpisodeForDetail = onSelectEpisodeForDetail,
        onBuildPlayerRoute = viewModel::buildPlayerRoute,
        onPlayClick = onPlayClick,
        onOpenSourcesForEpisode = { episode ->
            selectedEpisodeForSources = episode
            showSourcesBottomSheet = true
        },
        onBatchMarkRequest = { episode -> batchMarkTargetEpisode = episode },
        isLoggedIn = uiState.isLoggedIn,
        haptic = haptic,
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
        showSourcesBottomSheet = showSourcesBottomSheet,
        displaySubject = displaySubject,
        selectedEpisodeForSources = selectedEpisodeForSources,
        onDismissSourcesSheet = {
            showSourcesBottomSheet = false
            selectedEpisodeForSources = null
        },
        onStreamingUrlLaunch = handleStreamingUrl,
        onRequestSourceSearch = { ep -> viewModel.requestSourceSearch(ep) },
        onManageRules = onManageRules,
        playbackRules = uiState.playbackRules,
        playlists = uiState.playlists,
        failedSourceReasons = uiState.failedSourceReasons,
    )
}
