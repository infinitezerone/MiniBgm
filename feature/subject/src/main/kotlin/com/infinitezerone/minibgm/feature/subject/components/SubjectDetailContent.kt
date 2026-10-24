package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.infinitezerone.minibgm.feature.subject.computeSubjectWatchStatus
import kotlin.math.abs

/** 头部 Hero 在 LazyColumn 中的稳定键；顶栏联动按 key 定位，不依赖列表下标。 */
internal const val SUBJECT_HEADER_ITEM_KEY = "subject_header"

/** 二级 Tab 在 LazyColumn 中的稳定键；吸顶与背景联动都按 key 定位。 */
internal const val SUBJECT_TABS_ITEM_KEY = "subject_tabs_bar"

/** Tab 内容到列表底部的固定呼吸距离；尾部自适应留白会叠加在这段距离之上。 */
private val SubjectTabBottomPadding = 24.dp

/** 列表视口从顶栏底部开始时，Hero 与顶栏之间保留的初始间距。 */
private val SubjectTabContentTopPadding = 8.dp

private data class SubjectTabBarPlacement(
    val windowTopPx: Float,
    val isPinned: Boolean,
)

/**
 * 内容不足一屏时，计算为了让 Tab 能够滚到吸顶位置所需的尾部留白。
 *
 * [viewportHeightPx] 是已经扣除顶栏高度的滚动视口；[tabBarTopPx] 与
 * [contentEndTopPx] 必须来自同一坐标系（窗口坐标）。当 Tab 已经吸顶时，
 * 调用方不应再用本函数回写留白，否则会裁掉刚刚撑开的滚动区间。
 */
internal fun calculateSubjectTabTailSpacerPx(
    viewportHeightPx: Float,
    bottomPaddingPx: Float,
    tabBarTopPx: Float,
    contentEndTopPx: Float,
): Float {
    if (!viewportHeightPx.isFinite() || viewportHeightPx <= 0f) return 0f
    if (!tabBarTopPx.isFinite() || !contentEndTopPx.isFinite()) return 0f
    val contentBelowTabPx = contentEndTopPx - tabBarTopPx
    if (contentBelowTabPx <= 0f) return 0f
    val requiredBelowTabPx = viewportHeightPx - bottomPaddingPx
    return (requiredBelowTabPx - contentBelowTabPx).coerceAtLeast(0f)
}

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
    onCopyComment: ((String) -> Unit)? = null,
    onPlayEpisode: ((Episode) -> Unit)? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onOpenSources: (() -> Unit)? = null,
    onOpenEpisodeSources: ((Episode) -> Unit)? = null,
    onIncrementWatched: (() -> Unit)? = null,
    onDecrementWatched: (() -> Unit)? = null,
    onPromptLogin: (() -> Unit)? = null,
    onEpisodeClickForQuickAction: ((Episode) -> Unit)? = null,
    onRetryEpisodes: () -> Unit = {},
    onShare: (() -> Unit)? = null,
    topPadding: Dp = 12.dp,
    listState: LazyListState = rememberLazyListState(),
    onTitleBoundsChanged: (Rect) -> Unit = {},
    onTabBarTopChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bottomPaddingPx = with(density) { SubjectTabBottomPadding.toPx() }
    val contentTopPaddingPx = with(density) { SubjectTabContentTopPadding.toPx() }
    val topPaddingPx = with(density) { topPadding.toPx() }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    var tabContentEndTopPx by remember { mutableStateOf(Float.NaN) }
    var tabTailSpacerPx by remember { mutableStateOf(0f) }
    val tabBarPlacement by
        remember(listState, topPaddingPx, contentTopPaddingPx) {
            derivedStateOf {
                val tabs = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == SUBJECT_TABS_ITEM_KEY }
                if (tabs == null) {
                    SubjectTabBarPlacement(windowTopPx = Float.NaN, isPinned = false)
                } else {
                    SubjectTabBarPlacement(
                        windowTopPx = topPaddingPx + tabs.offset + contentTopPaddingPx,
                        isPinned = tabs.offset <= 0f,
                    )
                }
            }
        }

    LaunchedEffect(tabBarPlacement.windowTopPx) {
        onTabBarTopChanged(tabBarPlacement.windowTopPx)
    }

    LaunchedEffect(tabBarPlacement, tabContentEndTopPx, viewportHeightPx, bottomPaddingPx) {
        if (tabBarPlacement.isPinned || !tabBarPlacement.windowTopPx.isFinite()) return@LaunchedEffect
        val target =
            calculateSubjectTabTailSpacerPx(
                viewportHeightPx = viewportHeightPx,
                bottomPaddingPx = bottomPaddingPx,
                tabBarTopPx = tabBarPlacement.windowTopPx,
                contentEndTopPx = tabContentEndTopPx,
            )
        if (abs(target - tabTailSpacerPx) >= 0.5f) tabTailSpacerPx = target
    }

    // 滚动到“加载更多”脚标可见时自动续拉下一屏分集
    LaunchedEffect(listState, hasMoreEpisodes) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "episodes_load_more" }
        }.collect { visible ->
            if (visible && hasMoreEpisodes) onLoadMoreEpisodes()
        }
    }
    val lastTabRef =
        remember {
            object {
                var value: SubjectDetailTab = selectedTab
            }
        }

    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val gridColumns = if (adaptiveInfo.isWide) 7 else 5

    val tabHeaderIndex = if (uiState.error != null) 2 else 1
    val isTabBarPinned = tabBarPlacement.isPinned

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
            lastTabRef.value = selectedTab
            if (isTabBarPinned) {
                val tabsIndex =
                    listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == SUBJECT_TABS_ITEM_KEY }
                        ?.index
                        ?: tabHeaderIndex
                listState.requestScrollToItem(tabsIndex, 0)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = topPadding)
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                .pointerInput(selectedTab) {
                    var dragTotalPx = 0f
                    val thresholdPx = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotalPx = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragTotalPx += dragAmount
                        },
                        onDragEnd = {
                            val target =
                                when {
                                    dragTotalPx <= -thresholdPx ->
                                        SubjectDetailTab.entries.getOrNull(selectedTab.ordinal + 1)
                                    dragTotalPx >= thresholdPx ->
                                        SubjectDetailTab.entries.getOrNull(selectedTab.ordinal - 1)
                                    else -> null
                                }
                            if (target != null) onSelectTab(target)
                            dragTotalPx = 0f
                        },
                        onDragCancel = { dragTotalPx = 0f },
                    )
                },
        contentPadding = PaddingValues(top = SubjectTabContentTopPadding, bottom = SubjectTabBottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (uiState.error != null) {
            item(key = "inline_error") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        // 1. 条目头部 Hero 区 (首帧在场，支撑即使是首次进入也能顺滑飞渡)
        item(key = SUBJECT_HEADER_ITEM_KEY) {
            val watchStatus =
                remember(uiState.episodes, uiState.collection) {
                    computeSubjectWatchStatus(uiState.episodes, uiState.collection)
                }
            SubjectHeaderCard(
                subject = displaySubject,
                subjectType = subjectType,
                sharedElementSource = source,
                collection = uiState.collection,
                totalEpisodes = totalEpisodes,
                isLoggedIn = uiState.isLoggedIn,
                onOpenCollectionSheet = onOpenCollectionSheet,
                onUpdateCollectionStatus = onUpdateCollectionStatus,
                onTagClick = onTagClick,
                onPlayNext = onPlayNextEpisode,
                onOpenSources = onOpenSources,
                onIncrementWatched = onIncrementWatched,
                onDecrementWatched = onDecrementWatched,
                onPromptLogin = onPromptLogin,
                nextEpSort = mainNextUpEpNumber ?: nextUpEpNumber,
                watchStatus = watchStatus,
                onShare = onShare,
                onTitleBoundsChanged = onTitleBoundsChanged,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (fullSubject == null && (uiState.isLoading || isTransitionStabilizing)) {
            item(key = "detail_loading_skeleton") {
                SubjectDetailBodySkeleton(modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else if (fullSubject != null) {
            val subject = fullSubject

            // 2. 二级分栏 Tab 栏（吸顶实体指示器）
            stickyHeader(key = SUBJECT_TABS_ITEM_KEY) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (tabBarPlacement.isPinned) {
                                    MaterialTheme.colorScheme.background
                                } else {
                                    Color.Transparent
                                },
                            ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectDetailTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val tabLabel = getTabLabel(tab, subjectType)
                            val badgeCount =
                                when (tab) {
                                    SubjectDetailTab.EPISODES ->
                                        (
                                            if (totalEpisodes >
                                                0
                                            ) {
                                                totalEpisodes
                                            } else {
                                                currentEpisodes.size
                                            }
                                        ).takeIf { it > 0 }
                                    SubjectDetailTab.COMMUNITY -> uiState.subjectCommentTotal.takeIf { it > 0 }
                                    else -> null
                                }

                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { onSelectTab(tab) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = tabLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            },
                                    )
                                    if (badgeCount != null) {
                                        Text(
                                            text = if (badgeCount > 999) "999+" else badgeCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                },
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 7.dp)
                                                .width(24.dp)
                                                .height(3.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Tab 切换内容
            when (selectedTab) {
                SubjectDetailTab.OVERVIEW -> {
                    // 1. 评分分布与标签（1~10分柱状图、收藏人数、分类标签）
                    item(key = "rating_distribution") {
                        RatingDistributionCard(
                            rating = subject.rating,
                            collection = subject.collection,
                            tags = subject.tags,
                            onTagClick = onTagClick,
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    // 4. 制作人员 / Staff（若有）
                    if (uiState.persons.isNotEmpty()) {
                        item(key = "staff_section") {
                            StaffSection(
                                persons = uiState.persons,
                                onPersonClick = onPersonClick,
                                modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
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
                            onPlayNext = onPlayNextEpisode.takeIf { currentEpisodes.isNotEmpty() },
                            onOpenSources = onOpenSources,
                            group = activeGroup,
                            hasMoreEpisodes = hasMoreEpisodes,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    if (availableGroups.size > 1) {
                        item(key = "episode_group_chips") {
                            EpisodeGroupFilterChips(
                                availableGroups = availableGroups,
                                groupedEpisodes = groupedEpisodes,
                                selectedGroup = activeGroup,
                                onGroupSelected = onSelectGroup,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    when {
                        (uiState.isEpisodesLoading || uiState.isLoading) && currentEpisodes.isEmpty() -> {
                            item(key = "episodes_skeleton") {
                                if (isGridView) {
                                    EpisodeGridSkeleton(columns = gridColumns, modifier = Modifier.padding(horizontal = 16.dp))
                                } else {
                                    EpisodeListSkeleton(modifier = Modifier.padding(horizontal = 16.dp))
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
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
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
                                            .padding(horizontal = 16.dp, vertical = 32.dp),
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }

                    if (hasMoreEpisodes) {
                        item(key = "episodes_load_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
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
                            onCopyComment = onCopyComment,
                            isLoading = uiState.isCommunityLoading,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            item(key = "tab_content_end_marker") {
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .onGloballyPositioned { tabContentEndTopPx = it.boundsInWindow().top },
                )
            }

            if (tabTailSpacerPx > 0f) {
                item(key = "tab_content_tail_spacer") {
                    Spacer(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(with(density) { tabTailSpacerPx.toDp() }),
                    )
                }
            }
        }
    }
}
