package com.infinitezerone.minibgm.feature.subject

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.core.navigation.isNavEntering
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.subject.components.CharacterDetailBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.CharacterImagePreviewDialog
import com.infinitezerone.minibgm.feature.subject.components.CharactersSection
import com.infinitezerone.minibgm.feature.subject.components.CollectionStatusBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGrid
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroupFilterChips
import com.infinitezerone.minibgm.feature.subject.components.EpisodeListItem
import com.infinitezerone.minibgm.feature.subject.components.EpisodesSectionHeader
import com.infinitezerone.minibgm.feature.subject.components.PersonDetailBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.RatingDistributionCard
import com.infinitezerone.minibgm.feature.subject.components.RelationsSection
import com.infinitezerone.minibgm.feature.subject.components.StaffSection
import com.infinitezerone.minibgm.feature.subject.components.SubjectCommunitySection
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailBodySkeleton
import com.infinitezerone.minibgm.feature.subject.components.SubjectDetailFullSkeleton
import com.infinitezerone.minibgm.feature.subject.components.SubjectHeaderCard
import com.infinitezerone.minibgm.feature.subject.components.SubjectPersonalProgressCard
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeWatched
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private fun getTabLabel(
    tab: SubjectDetailTab,
    subjectType: SubjectType,
): String =
    when (tab) {
        SubjectDetailTab.EPISODES ->
            when (subjectType) {
                SubjectType.BOOK -> "📚 卷册与章节"
                SubjectType.MUSIC -> "🎵 曲目列表"
                SubjectType.GAME -> "🎮 关卡与章节"
                SubjectType.ANIME, SubjectType.REAL -> "📺 章节打卡"
            }
        SubjectDetailTab.DETAILS ->
            when (subjectType) {
                SubjectType.BOOK -> "📖 原作与出版信息"
                SubjectType.MUSIC -> "💿 专辑制作与人员"
                SubjectType.GAME -> "🎮 游戏资料与主创"
                SubjectType.ANIME, SubjectType.REAL -> "📖 资料与演职员"
            }
        SubjectDetailTab.COMMUNITY -> "💬 社区吐槽"
    }

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
    onTagClick: (String) -> Unit = {},
    onCharacterClick: ((Long) -> Unit)? = null,
    onPersonClick: ((Long) -> Unit)? = null,
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
                episodeSort = episode.sort,
                episodeType = episode.type,
                episodeName = episode.name,
                episodeNameCn = episode.nameCn,
            ),
        )
    }

    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEntering = isNavEntering()
    var hasEnteredTransitionFinished by rememberSaveable { mutableStateOf(false) }
    if (!isEntering) {
        hasEnteredTransitionFinished = true
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
            is BgmLink.Topic -> context.launchWebUrl(url)
            is BgmLink.User -> context.launchWebUrl(url)
            is BgmLink.External -> context.launchWebUrl(url)
        }
    }

    LaunchedEffect(subjectType, uiState.episodes) {
        if (subjectType == SubjectType.GAME && uiState.episodes.isEmpty() && uiState.selectedTab == SubjectDetailTab.EPISODES) {
            viewModel.selectTab(SubjectDetailTab.DETAILS)
        }
    }

    val groupedEpisodes =
        remember(uiState.episodes) {
            uiState.episodes.groupBy { EpisodeGroup.fromType(it.type) }
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        modifier = modifier,
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
                                        imageVector = Icons.Filled.ErrorOutline,
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
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val epNumber =
                                if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
                            viewModel.toggleEpisodeWatched(episode.id, isWatched, epNumber)
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
                                onOpenCollectionSheet = { viewModel.setCollectionSheetVisible(true) },
                                onToggleWatching = viewModel::toggleWatching,
                                onToggleEpisodeWatched = onToggleEpisodeWatched,
                                onSelectEpisodeForDetail = onSelectEpisodeForDetail,
                                onSubjectClick = onSubjectClick,
                                onTagClick = onTagClick,
                                onCharacterClick = handleCharacterClick,
                                onPersonClick = handlePersonClick,
                                onPreviewCharacter = { previewCharacter = it },
                                onLinkClick = handleLinkClick,
                                onLoadMoreComments = { viewModel.loadMoreSubjectComments() },
                                isTransitionStabilizing = isTransitionStabilizing,
                                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showCollectionSheet) {
        CollectionStatusBottomSheet(
            currentCollection = uiState.collection,
            subjectType = subjectType,
            onDismiss = { viewModel.setCollectionSheetVisible(false) },
            onSave = { type, rate, comment, private ->
                viewModel.updateCollectionStatus(
                    type = type,
                    rate = rate,
                    comment = comment,
                    private = private,
                )
            },
        )
    }

    previewCharacter?.let { character ->
        CharacterImagePreviewDialog(
            character = character,
            onDismiss = { previewCharacter = null },
            onViewDetail = { characterId ->
                handleCharacterClick(characterId)
            },
        )
    }

    uiState.activeCharacter?.let { character ->
        CharacterDetailBottomSheet(
            character = character,
            detail = uiState.selectedCharacterDetail,
            relatedWorks = uiState.selectedCharacterWorks,
            isLoading = uiState.isLoadingEntityDetail,
            onDismiss = viewModel::dismissEntityDetail,
            onSubjectClick = { relSubjectId ->
                viewModel.dismissEntityDetail()
                onSubjectClick(relSubjectId)
            },
            onActorClick = { actorId ->
                viewModel.openPersonDetail(actorId)
            },
        )
    }

    uiState.activePerson?.let { person ->
        PersonDetailBottomSheet(
            person = person,
            detail = uiState.selectedPersonDetail,
            relatedWorks = uiState.selectedPersonWorks,
            isLoading = uiState.isLoadingEntityDetail,
            onDismiss = viewModel::dismissEntityDetail,
            onSubjectClick = { relSubjectId ->
                viewModel.dismissEntityDetail()
                onSubjectClick(relSubjectId)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDetailContent(
    displaySubject: Subject,
    fullSubject: Subject?,
    subjectType: SubjectType,
    uiState: SubjectDetailUiState,
    source: String,
    totalEpisodes: Int,
    selectedTab: SubjectDetailTab,
    onSelectTab: (SubjectDetailTab) -> Unit,
    currentEpisodes: List<Episode>,
    availableGroups: List<EpisodeGroup>,
    groupedEpisodes: Map<EpisodeGroup, List<Episode>>,
    activeGroup: EpisodeGroup,
    onSelectGroup: (EpisodeGroup) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onToggleWatching: () -> Unit,
    onToggleEpisodeWatched: (Episode, Boolean) -> Unit,
    onSelectEpisodeForDetail: (Episode) -> Unit,
    onSubjectClick: (Long) -> Unit,
    onTagClick: (String) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    onLinkClick: (String) -> Unit,
    onLoadMoreComments: () -> Unit,
    isTransitionStabilizing: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val tabScrollPositions = remember { mutableMapOf<SubjectDetailTab, Pair<Int, Int>>() }
    var lastTab by remember { mutableStateOf(selectedTab) }

    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val gridColumns = if (adaptiveInfo.isWide) 7 else 6

    val tabHeaderIndex = if (uiState.error != null) 3 else 2

    LaunchedEffect(selectedTab) {
        if (selectedTab != lastTab) {
            tabScrollPositions[lastTab] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            lastTab = selectedTab

            val target =
                if (listState.firstVisibleItemIndex < tabHeaderIndex) {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                } else {
                    tabScrollPositions[selectedTab] ?: (tabHeaderIndex to 0)
                }
            val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            val safeIndex = target.first.coerceIn(0, maxIndex)
            listState.scrollToItem(safeIndex, target.second)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (uiState.error != null) {
            item(key = "inline_error") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "同步提示：${uiState.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // 1. 条目头部 Hero 卡片 (首帧在场，支撑即使是首次进入也能顺滑飞渡)
        item(key = "header") {
            SubjectHeaderCard(
                subject = displaySubject,
                subjectType = subjectType,
                sharedElementSource = source,
            )
        }

        if (fullSubject == null && (uiState.isLoading || isTransitionStabilizing)) {
            item(key = "detail_loading_skeleton") {
                SubjectDetailBodySkeleton()
            }
        } else if (fullSubject != null) {
            val subject = fullSubject
            // 2. 我的追番/阅读/收听/游玩与进度条面板
            item(key = "collection_progress_bar") {
                SubjectPersonalProgressCard(
                    collection = uiState.collection,
                    totalEpisodes = totalEpisodes,
                    subjectType = subjectType,
                    onOpenSheet = onOpenCollectionSheet,
                    onToggleWatching = onToggleWatching,
                )
            }

            // 3. 粘性二级分栏 Tab 栏
            stickyHeader(key = "subject_tabs_bar") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SubjectDetailTab.entries.forEach { tab ->
                            val tabLabel = getTabLabel(tab, subjectType)
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                text = {
                                    Text(
                                        text =
                                            when (tab) {
                                                SubjectDetailTab.EPISODES ->
                                                    if (currentEpisodes.isNotEmpty()) {
                                                        "$tabLabel (${currentEpisodes.size})"
                                                    } else {
                                                        tabLabel
                                                    }
                                                SubjectDetailTab.COMMUNITY ->
                                                    if (uiState.subjectCommentTotal > 0) {
                                                        "$tabLabel (${uiState.subjectCommentTotal})"
                                                    } else {
                                                        tabLabel
                                                    }
                                                else -> tabLabel
                                            },
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // 4. Tab 切换内容
            when (selectedTab) {
                SubjectDetailTab.EPISODES -> {
                    val watchedInGroup =
                        currentEpisodes.count {
                            isEpisodeWatched(it, uiState.collection?.epStatus ?: 0)
                        }

                    item(key = "episodes_header") {
                        EpisodesSectionHeader(
                            totalEpisodes = currentEpisodes.size,
                            watchedEpisodes = watchedInGroup,
                            subjectType = subjectType,
                            isGridView = isGridView,
                            onToggleView = onToggleGridView,
                        )
                    }

                    if (availableGroups.size > 1) {
                        item(key = "episode_group_chips") {
                            EpisodeGroupFilterChips(
                                availableGroups = availableGroups,
                                groupedEpisodes = groupedEpisodes,
                                selectedGroup = activeGroup,
                                onGroupSelected = onSelectGroup,
                            )
                        }
                    }

                    if (currentEpisodes.isEmpty()) {
                        item(key = "episodes_empty") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (uiState.isLoading) "正在加载章节列表..." else "暂无分集信息",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (isGridView) {
                        item(key = "episodes_grid") {
                            EpisodeGrid(
                                episodes = currentEpisodes,
                                watchedCount = uiState.collection?.epStatus ?: 0,
                                onToggleWatched = onToggleEpisodeWatched,
                                onEpisodeLongClick = onSelectEpisodeForDetail,
                                columns = gridColumns,
                            )
                        }
                    } else {
                        items(items = currentEpisodes, key = { it.id }) { episode ->
                            val isWatched = isEpisodeWatched(episode, uiState.collection?.epStatus ?: 0)
                            EpisodeListItem(
                                episode = episode,
                                isWatched = isWatched,
                                onClick = { onSelectEpisodeForDetail(episode) },
                                onToggleWatched = {
                                    onToggleEpisodeWatched(episode, !isWatched)
                                },
                            )
                        }
                    }
                }

                SubjectDetailTab.DETAILS -> {
                    item(key = "rating_distribution") {
                        RatingDistributionCard(
                            rating = subject.rating,
                            collection = subject.collection,
                            tags = subject.tags,
                            onTagClick = onTagClick,
                        )
                    }

                    if (uiState.isDetailsLoading &&
                        uiState.relations.isEmpty() &&
                        uiState.characters.isEmpty() &&
                        uiState.persons.isEmpty()
                    ) {
                        item(key = "details_loading_indicator") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }

                    if (uiState.relations.isNotEmpty()) {
                        item(key = "relations_section") {
                            RelationsSection(
                                relations = uiState.relations,
                                onSubjectClick = onSubjectClick,
                            )
                        }
                    }

                    if (uiState.characters.isNotEmpty()) {
                        item(key = "characters_section") {
                            CharactersSection(
                                characters = uiState.characters,
                                onCharacterClick = onCharacterClick,
                                onActorClick = onPersonClick,
                                onPreviewCharacter = onPreviewCharacter,
                            )
                        }
                    }

                    if (uiState.persons.isNotEmpty()) {
                        item(key = "staff_section") {
                            StaffSection(
                                persons = uiState.persons,
                                onPersonClick = onPersonClick,
                            )
                        }
                    }
                }

                SubjectDetailTab.COMMUNITY -> {
                    item(key = "community_tab_section") {
                        SubjectCommunitySection(
                            comments = uiState.subjectComments,
                            commentTotal = uiState.subjectCommentTotal,
                            isLoadingMoreComments = uiState.isLoadingMoreComments,
                            hasMoreComments = uiState.hasMoreComments,
                            onLoadMoreComments = onLoadMoreComments,
                            topics = uiState.subjectTopics,
                            onUrlClick = onLinkClick,
                            isLoading = uiState.isCommunityLoading,
                        )
                    }
                }
            }
        }
    }
}
