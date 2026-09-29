package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.SubjectDetailTab
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState
import kotlinx.coroutines.launch

internal fun getTabLabel(
    tab: SubjectDetailTab,
    subjectType: SubjectType,
): String =
    when (tab) {
        SubjectDetailTab.EPISODES ->
            when (subjectType) {
                SubjectType.BOOK -> "卷册与章节"
                SubjectType.MUSIC -> "曲目列表"
                SubjectType.GAME -> "关卡与章节"
                SubjectType.ANIME, SubjectType.REAL -> "章节打卡"
            }
        SubjectDetailTab.DETAILS ->
            when (subjectType) {
                SubjectType.BOOK -> "原作与出版信息"
                SubjectType.MUSIC -> "专辑制作与人员"
                SubjectType.GAME -> "游戏资料与主创"
                SubjectType.ANIME, SubjectType.REAL -> "资料与演职员"
            }
        SubjectDetailTab.COMMUNITY -> "社区吐槽"
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
    onIncrementWatched: (() -> Unit)? = null,
    onEpisodeClickForQuickAction: ((Episode) -> Unit)? = null,
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
    var lastTab by remember { mutableStateOf(selectedTab) }

    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val gridColumns = if (adaptiveInfo.isWide) 7 else 6

    val tabHeaderIndex = if (uiState.error != null) 3 else 2

    val nextUpEpisode =
        remember(currentEpisodes, uiState.collection?.epStatus) {
            currentEpisodes.firstOrNull {
                isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
            }
        }

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
                    onIncrementWatched = onIncrementWatched,
                    onPlayNext = onPlayNextEpisode.takeIf { currentEpisodes.isNotEmpty() },
                    nextEpSort = nextUpEpisode?.sort,
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
                            episodeSortDescending = episodeSortDescending,
                            onToggleSort = onToggleEpisodeSort,
                            nextUpEpisodeSort = nextUpEpisode?.sort,
                            onJumpToNextUp = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(tabHeaderIndex)
                                }
                            },
                            onPlayNext = onPlayNextEpisode.takeIf { currentEpisodes.isNotEmpty() },
                            onOpenSources = onOpenSources,
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
                                        text = "暂无分集信息",
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
                                    onEpisodeClick = onEpisodeClickForQuickAction,
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
                                        text = "上滑加载更多分集…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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
                            onTopicClick = onTopicClick,
                            isLoading = uiState.isCommunityLoading,
                        )
                    }
                }
            }
        }
    }
}
