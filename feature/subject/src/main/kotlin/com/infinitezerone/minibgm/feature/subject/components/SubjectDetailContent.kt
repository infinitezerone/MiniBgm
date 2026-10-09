package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectDetailTab
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState
import kotlinx.coroutines.launch

@Composable
internal fun getTabLabel(
    tab: SubjectDetailTab,
    subjectType: SubjectType,
): String =
    when (tab) {
        SubjectDetailTab.OVERVIEW -> stringResource(R.string.feature_subject_tab_overview)
        SubjectDetailTab.EPISODES ->
            when (subjectType) {
                SubjectType.BOOK -> stringResource(R.string.feature_subject_tab_volumes)
                SubjectType.MUSIC -> stringResource(R.string.feature_subject_tab_tracks)
                SubjectType.GAME -> stringResource(R.string.feature_subject_tab_chapters)
                SubjectType.ANIME, SubjectType.REAL -> stringResource(R.string.feature_subject_tab_episodes)
            }
        SubjectDetailTab.COMMUNITY -> stringResource(R.string.feature_subject_tab_community)
    }

/**
 * 条目详情主滚动内容：
 * 头部 Hero 卡片 -> 个人进度卡片 -> 粘性分栏 Tab -> Tab 内容（章节列表/网格、演职员与关系、社区吐槽）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SubjectDetailContent(
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
    episodeSortDescending: Boolean,
    hasMoreEpisodes: Boolean,
    isLoadingMoreEpisodes: Boolean,
    onToggleEpisodeSort: () -> Unit,
    onLoadMoreEpisodes: () -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onToggleWatching: () -> Unit,
    onUpdateCollectionStatus: ((CollectionType) -> Unit)? = null,
    onToggleEpisodeWatched: (Episode, Boolean) -> Unit,
    onSelectEpisodeForDetail: (Episode) -> Unit,
    onSubjectClick: (Long) -> Unit,
    onTagClick: (String) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    onLinkClick: (String) -> Unit,
    onTopicClick: (Long, String) -> Unit,
    onLoadMoreComments: () -> Unit,
    isTransitionStabilizing: Boolean,
    onBatchMarkEpisode: (Episode) -> Unit,
    onPlayEpisode: ((Episode) -> Unit)? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onOpenSources: (() -> Unit)? = null,
    onOpenEpisodeSources: ((Episode) -> Unit)? = null,
    onIncrementWatched: (() -> Unit)? = null,
    onDecrementWatched: (() -> Unit)? = null,
    onPromptLogin: (() -> Unit)? = null,
    onEpisodeClickForQuickAction: ((Episode) -> Unit)? = null,
    onRetryEpisodes: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 滚动到“加载更多”脚标可见时自动续拉下一屏分集
    LaunchedEffect(listState, hasMoreEpisodes) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "episodes_load_more" }
        }.collect { visible ->
            if (visible && hasMoreEpisodes) onLoadMoreEpisodes()
        }
    }
    val tabScrollPositions = remember { mutableMapOf<SubjectDetailTab, Pair<Int, Int>>() }
    val lastTabRef =
        remember {
            object {
                var value: SubjectDetailTab = selectedTab
            }
        }

    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val gridColumns = if (adaptiveInfo.isWide) 7 else 5

    val tabHeaderIndex = if (uiState.error != null) 2 else 1

    val nextUpEpisode =
        remember(currentEpisodes, uiState.collection?.epStatus) {
            currentEpisodes.firstOrNull {
                isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
            }
        }
    val nextUpEpNumber = nextUpEpisode?.episodeNumber

    val mainNextUpEpNumber =
        remember(groupedEpisodes, uiState.episodes, uiState.collection?.epStatus) {
            (groupedEpisodes[EpisodeGroup.MAIN] ?: uiState.episodes.filter { it.isMain })
                .firstOrNull {
                    isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
                }?.episodeNumber
        }

    LaunchedEffect(selectedTab) {
        val lastTab = lastTabRef.value
        if (selectedTab != lastTab) {
            tabScrollPositions[lastTab] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            lastTabRef.value = selectedTab

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
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
                        text = stringResource(R.string.feature_subject_sync_hint, uiState.error.orEmpty()),
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
                collection = uiState.collection,
                totalEpisodes = totalEpisodes,
                onOpenCollectionSheet = onOpenCollectionSheet,
                onTagClick = onTagClick,
            )
        }

        // 2. 个人追番与进度看板卡片
        item(key = "collection_progress_card") {
            SubjectCollectionCard(
                collection = uiState.collection,
                totalEpisodes = totalEpisodes,
                subjectType = subjectType,
                isLoggedIn = uiState.isLoggedIn,
                nextEpSort = mainNextUpEpNumber ?: nextUpEpNumber,
                onOpenCollectionSheet = onOpenCollectionSheet,
                onUpdateCollectionStatus = onUpdateCollectionStatus ?: {},
                onPlayNext = onPlayNextEpisode,
                onIncrementWatched = onIncrementWatched,
                onDecrementWatched = onDecrementWatched,
                onPromptLogin = onPromptLogin,
            )
        }

        if (fullSubject == null && (uiState.isLoading || isTransitionStabilizing)) {
            item(key = "detail_loading_skeleton") {
                SubjectDetailBodySkeleton()
            }
        } else if (fullSubject != null) {
            val subject = fullSubject

            // 2. 二级分栏 Tab 栏（跟随内容自然流滚动，不再生硬吸顶）
            item(key = "subject_tabs_bar") {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        divider = {},
                        indicator = {
                            Box(
                                modifier =
                                    Modifier
                                        .tabIndicatorOffset(selectedTab.ordinal)
                                        .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(28.dp)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SubjectDetailTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val tabLabel = getTabLabel(tab, subjectType)
                            val badgeCount =
                                when (tab) {
                                    SubjectDetailTab.EPISODES -> currentEpisodes.size.takeIf { it > 0 }
                                    SubjectDetailTab.COMMUNITY -> uiState.subjectCommentTotal.takeIf { it > 0 }
                                    else -> null
                                }
                            Tab(
                                selected = isSelected,
                                onClick = { onSelectTab(tab) },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            text = tabLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color =
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                                },
                                        )
                                        if (badgeCount != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color =
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                                                    },
                                            ) {
                                                Text(
                                                    text = if (badgeCount > 999) "999+" else badgeCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color =
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                                        },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 4. Tab 切换内容
            when (selectedTab) {
                SubjectDetailTab.OVERVIEW -> {
                    // 1. 横向轻量选集滑轨（若有分集）：紧凑直达点播、打卡与详情
                    if (currentEpisodes.isNotEmpty()) {
                        item(key = "overview_episodes_rail") {
                            EpisodeQuickRail(
                                episodes = currentEpisodes,
                                watchedCount = uiState.collection?.epStatus ?: 0,
                                onEpisodeClick = { episode ->
                                    if (onEpisodeClickForQuickAction != null) {
                                        onEpisodeClickForQuickAction(episode)
                                    } else {
                                        onSelectEpisodeForDetail(episode)
                                    }
                                },
                                onViewAllClick = { onSelectTab(SubjectDetailTab.EPISODES) },
                                hasProgress = uiState.collection != null,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    // 2. 评分分布与标签（1~10分柱状图、收藏人数、分类标签）
                    item(key = "rating_distribution") {
                        RatingDistributionCard(
                            rating = subject.rating,
                            collection = subject.collection,
                            tags = subject.tags,
                            onTagClick = onTagClick,
                        )
                    }

                    // 3. 角色与声优阵容（若有）
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

                    // 4. 制作人员 / Staff（若有）
                    if (uiState.persons.isNotEmpty()) {
                        item(key = "staff_section") {
                            StaffSection(
                                persons = uiState.persons,
                                onPersonClick = onPersonClick,
                            )
                        }
                    }

                    // 5. 关联作品与系列（若有）
                    if (uiState.relations.isNotEmpty()) {
                        item(key = "relations_section") {
                            RelationsSection(
                                relations = uiState.relations,
                                onSubjectClick = onSubjectClick,
                                currentSubjectId = displaySubject.id,
                                currentSubjectName = displaySubject.nameCn.ifBlank { displaySubject.name },
                                currentSubjectCover = displaySubject.images?.bestImage ?: displaySubject.images?.large,
                                currentSubjectScore = displaySubject.rating?.score ?: 0.0,
                            )
                        }
                    }

                    // 6. 首次加载占位
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
                }

                SubjectDetailTab.EPISODES -> {
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
                            onJumpToNextUp = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(tabHeaderIndex)
                                }
                            },
                            onPlayNext = onPlayNextEpisode.takeIf { currentEpisodes.isNotEmpty() },
                            onOpenSources = onOpenSources,
                            group = activeGroup,
                            hasMoreEpisodes = hasMoreEpisodes,
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

                    when {
                        (uiState.isEpisodesLoading || uiState.isLoading) && currentEpisodes.isEmpty() -> {
                            item(key = "episodes_skeleton") {
                                if (isGridView) {
                                    EpisodeGridSkeleton(columns = gridColumns)
                                } else {
                                    EpisodeListSkeleton()
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
                                            .padding(vertical = 16.dp),
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
                                            .padding(vertical = 32.dp),
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
                                    onToggleWatched = onToggleEpisodeWatched,
                                    onEpisodeClick = { episode ->
                                        if (onEpisodeClickForQuickAction != null) {
                                            onEpisodeClickForQuickAction(episode)
                                        } else {
                                            onSelectEpisodeForDetail(episode)
                                        }
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
                                    onClick = { onSelectEpisodeForDetail(episode) },
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
                                )
                            }
                        }
                    }

                    if (hasMoreEpisodes) {
                        item(key = "episodes_load_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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
                            onTopicClick = onTopicClick,
                            isLoading = uiState.isCommunityLoading,
                        )
                    }
                }
            }
        }
    }
}
