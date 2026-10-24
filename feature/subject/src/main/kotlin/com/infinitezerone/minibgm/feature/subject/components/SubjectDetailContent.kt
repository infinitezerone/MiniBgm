package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** 顶栏与头部 Hero 之间的初始间距。 */
private val SubjectHeaderTopPadding = 8.dp

/** 头部 Hero 与 Tab 栏之间的呼吸距离；随头部一同参与收合。 */
private val SubjectHeaderTabGap = 14.dp

/** 二级 Tab 栏高度。 */
private val SubjectTabBarHeight = 48.dp

/** 各 Tab 页列表底部的呼吸距离。 */
private val SubjectTabBottomPadding = 24.dp

/**
 * 头部收合进度唯一持有者：由内容区 nested scroll 写入、屏幕在绘制期（graphicsLayer /
 * derivedStateOf 离散布尔）读取，滚动逐帧更新不触发重组。
 *
 * - [offsetPx]：当前收合位移，0 = 完全展开，[heightPx] = 完全收合（Tab 栏钉在顶栏下方）；
 * - [heightPx]：可收合总高度（头部 Hero + 上下呼吸距离），由头部布局实测回写。
 */
class SubjectHeaderCollapseState {
    var offsetPx by mutableFloatStateOf(0f)
    var heightPx by mutableFloatStateOf(0f)
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
 * 头部 Hero 卡片（nested scroll 收合）-> 吸顶二级 Tab 栏 -> HorizontalPager 分页内容
 * （章节列表/网格、演职员与关系、社区吐槽），每页独立滚动状态，左右滑动切页。
 *
 * 严禁回退为「单个共享 LazyColumn 换 items + stickyHeader 假吸顶 + 尾部自适应留白」：
 * 瞬时矮内容（如讨论页 loading 占位）会把共享滚动钳制弹回，页面剧烈跳动且顶栏标题错折
 * （红线 subject_detail_tabs_must_be_pager_based_with_independent_page_scroll）。
 */
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
    headerCollapse: SubjectHeaderCollapseState,
    onTitleBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // 头部收合 nested scroll：上滑先收头部再滚列表；列表到顶后下拉先展头部再交还 overscroll。
    val collapseConnection =
        remember(headerCollapse) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val dy = available.y
                    val height = headerCollapse.heightPx
                    if (dy >= 0f || height <= 0f) return Offset.Zero
                    val previous = headerCollapse.offsetPx
                    val next = (previous - dy).coerceIn(0f, height)
                    headerCollapse.offsetPx = next
                    return Offset(0f, previous - next)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val dy = available.y
                    val height = headerCollapse.heightPx
                    if (dy <= 0f || height <= 0f) return Offset.Zero
                    val previous = headerCollapse.offsetPx
                    val next = (previous - dy).coerceIn(0f, height)
                    headerCollapse.offsetPx = next
                    return Offset(0f, previous - next)
                }
            }
        }

    val coroutineScope = rememberCoroutineScope()
    val headerDragModifier =
        Modifier.draggable(
            state =
                rememberDraggableState { delta ->
                    val height = headerCollapse.heightPx
                    if (height > 0f) {
                        headerCollapse.offsetPx = (headerCollapse.offsetPx - delta).coerceIn(0f, height)
                    }
                },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                val height = headerCollapse.heightPx
                if (height > 0f) {
                    val target =
                        when {
                            velocity < -800f -> height
                            velocity > 800f -> 0f
                            headerCollapse.offsetPx > height * 0.5f -> height
                            else -> 0f
                        }
                    val anim = Animatable(headerCollapse.offsetPx)
                    coroutineScope.launch {
                        anim.animateTo(
                            targetValue = target,
                            initialVelocity = -velocity,
                        ) {
                            headerCollapse.offsetPx = value
                        }
                    }
                }
            },
        )

    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val pagerState =
        rememberPagerState(initialPage = selectedTab.ordinal) { SubjectDetailTab.entries.size }
    // 双向同步：点 Tab 动画翻页；滑动/动画落定后回写选中态（VM 为真源，同页回写为 no-op）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = SubjectDetailTab.entries.getOrNull(page) ?: return@collect
            if (tab != currentSelectedTab) onSelectTab(tab)
        }
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.targetPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = topPadding)
                .clipToBounds()
                .nestedScroll(collapseConnection),
    ) {
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val pagerHeight =
            with(density) { (viewportHeightPx - SubjectTabBarHeight.toPx()).coerceAtLeast(0f).toDp() }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(0, -headerCollapse.offsetPx.coerceIn(0f, headerCollapse.heightPx).roundToInt())
                    },
        ) {
            if (uiState.error != null) {
                SubjectInlineError(message = uiState.error.orEmpty())
            }

            if (fullSubject == null && (uiState.isLoading || isTransitionStabilizing)) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = SubjectHeaderTopPadding)) {
                        SubjectHeader(
                            displaySubject = displaySubject,
                            subjectType = subjectType,
                            uiState = uiState,
                            source = source,
                            totalEpisodes = totalEpisodes,
                            currentEpisodes = currentEpisodes,
                            groupedEpisodes = groupedEpisodes,
                            onOpenCollectionSheet = onOpenCollectionSheet,
                            onUpdateCollectionStatus = onUpdateCollectionStatus,
                            onTagClick = onTagClick,
                            onPlayNextEpisode = onPlayNextEpisode,
                            onOpenSources = onOpenSources,
                            onIncrementWatched = onIncrementWatched,
                            onDecrementWatched = onDecrementWatched,
                            onPromptLogin = onPromptLogin,
                            onShare = onShare,
                            onTitleBoundsChanged = onTitleBoundsChanged,
                        )
                    }
                    SubjectDetailBodySkeleton(modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else if (fullSubject != null) {
                val subject = fullSubject
                val collapseRatio =
                    if (headerCollapse.heightPx > 0f) {
                        (headerCollapse.offsetPx / headerCollapse.heightPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                // 头部收合区：上间距 + Hero 卡片 + 与 Tab 栏的呼吸距离，整体实测回写 heightPx，附着垂直滑动手势联动
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { headerCollapse.heightPx = it.height.toFloat() }
                            .then(headerDragModifier),
                ) {
                    Column {
                        Spacer(Modifier.height(SubjectHeaderTopPadding))
                        SubjectHeader(
                            displaySubject = displaySubject,
                            subjectType = subjectType,
                            uiState = uiState,
                            source = source,
                            totalEpisodes = totalEpisodes,
                            currentEpisodes = currentEpisodes,
                            groupedEpisodes = groupedEpisodes,
                            onOpenCollectionSheet = onOpenCollectionSheet,
                            onUpdateCollectionStatus = onUpdateCollectionStatus,
                            onTagClick = onTagClick,
                            onPlayNextEpisode = onPlayNextEpisode,
                            onOpenSources = onOpenSources,
                            onIncrementWatched = onIncrementWatched,
                            onDecrementWatched = onDecrementWatched,
                            onPromptLogin = onPromptLogin,
                            onShare = onShare,
                            onTitleBoundsChanged = onTitleBoundsChanged,
                        )
                        Spacer(Modifier.height(SubjectHeaderTabGap))
                    }
                }

                SubjectDetailTabBar(
                    selectedTab = selectedTab,
                    pagerState = pagerState,
                    collapseRatio = collapseRatio,
                    subjectType = subjectType,
                    totalEpisodes = totalEpisodes,
                    currentEpisodeCount = currentEpisodes.size,
                    commentTotal = uiState.subjectCommentTotal,
                    onSelectTab = onSelectTab,
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(pagerHeight),
                    beyondViewportPageCount = SubjectDetailTab.entries.size - 1,
                ) { page ->
                    when (SubjectDetailTab.entries.getOrElse(page) { SubjectDetailTab.OVERVIEW }) {
                        SubjectDetailTab.OVERVIEW ->
                            OverviewTabPage(
                                subject = subject,
                                uiState = uiState,
                                displaySubject = displaySubject,
                                onTagClick = onTagClick,
                                onCharacterClick = onCharacterClick,
                                onActorClick = onPersonClick,
                                onPreviewCharacter = onPreviewCharacter,
                                onPersonClick = onPersonClick,
                                onSubjectClick = onSubjectClick,
                            )

                        SubjectDetailTab.EPISODES ->
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
                                onSelectEpisodeForDetail = onSelectEpisodeForDetail,
                                onEpisodeClickForQuickAction = onEpisodeClickForQuickAction,
                                onBatchMarkEpisode = onBatchMarkEpisode,
                                onPlayEpisode = onPlayEpisode,
                                onOpenSources = onOpenSources,
                                onOpenEpisodeSources = onOpenEpisodeSources,
                                onPlayNextEpisode = onPlayNextEpisode,
                            )

                        SubjectDetailTab.COMMUNITY ->
                            CommunityTabPage(
                                uiState = uiState,
                                onLoadMoreComments = onLoadMoreComments,
                                onLinkClick = onLinkClick,
                                onTopicClick = onTopicClick,
                                onCopyComment = onCopyComment,
                            )
                    }
                }
            }
        }
    }
}

/** 头部 Hero 区（含进卡与 CTA），收合分支与骨架分支共用。 */
@Composable
private fun SubjectHeader(
    displaySubject: Subject,
    subjectType: SubjectType,
    uiState: SubjectDetailUiState,
    source: String,
    totalEpisodes: Int,
    currentEpisodes: List<Episode>,
    groupedEpisodes: Map<EpisodeGroup, List<Episode>>,
    onOpenCollectionSheet: () -> Unit,
    onUpdateCollectionStatus: ((CollectionType) -> Unit)?,
    onTagClick: (String) -> Unit,
    onPlayNextEpisode: (() -> Unit)?,
    onOpenSources: (() -> Unit)?,
    onIncrementWatched: (() -> Unit)?,
    onDecrementWatched: (() -> Unit)?,
    onPromptLogin: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onTitleBoundsChanged: (Rect) -> Unit,
) {
    val nextUpEpisode =
        remember(currentEpisodes, uiState.collection?.epStatus) {
            currentEpisodes.firstOrNull {
                isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
            }
        }
    val mainNextUpEpNumber =
        remember(groupedEpisodes, uiState.episodes, uiState.collection?.epStatus) {
            (groupedEpisodes[EpisodeGroup.MAIN] ?: uiState.episodes.filter { it.isMain })
                .firstOrNull {
                    isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
                }?.episodeNumber
        }
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
        nextEpSort = mainNextUpEpNumber ?: nextUpEpisode?.episodeNumber,
        watchStatus = watchStatus,
        onShare = onShare,
        onTitleBoundsChanged = onTitleBoundsChanged,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** 同步失败的内联提示条。 */
@Composable
private fun SubjectInlineError(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_subject_sync_hint, message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** 吸顶二级 Tab 栏（概览/分集/讨论）：支持滑动渐变背景与平滑位移指示胶囊。 */
@Composable
private fun SubjectDetailTabBar(
    selectedTab: SubjectDetailTab,
    pagerState: PagerState,
    collapseRatio: Float,
    subjectType: SubjectType,
    totalEpisodes: Int,
    currentEpisodeCount: Int,
    commentTotal: Int,
    onSelectTab: (SubjectDetailTab) -> Unit,
) {
    val tabBgAlpha = (collapseRatio * 1.25f).coerceIn(0f, 1f)
    val tabContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = tabBgAlpha)

    Surface(
        color = tabContainerColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SubjectTabBarHeight),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val tabCount = SubjectDetailTab.entries.size
            val tabWidth = maxWidth / tabCount
            val pageOffset =
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, (tabCount - 1).toFloat())
            val indicatorWidth = 28.dp
            val indicatorCenterX = (pageOffset + 0.5f) * (maxWidth.value / tabCount)
            val indicatorLeft = (indicatorCenterX - (indicatorWidth.value / 2f)).dp

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectDetailTab.entries.forEachIndexed { index, tab ->
                    val tabLabel = getTabLabel(tab, subjectType)
                    val badgeCount =
                        when (tab) {
                            SubjectDetailTab.EPISODES ->
                                (if (totalEpisodes > 0) totalEpisodes else currentEpisodeCount).takeIf { it > 0 }
                            SubjectDetailTab.COMMUNITY -> commentTotal.takeIf { it > 0 }
                            else -> null
                        }
                    val tabDistance = (pageOffset - index).absoluteValue.coerceIn(0f, 1f)
                    val isSelected = tabDistance < 0.5f

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
                    }
                }
            }

            // 平滑滑动的指示胶囊
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 6.dp)
                        .offset(x = indicatorLeft)
                        .width(indicatorWidth)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )

            // 吸顶时微细分割线，阻隔下方穿行内容
            if (collapseRatio > 0.05f) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * collapseRatio),
                )
            }
        }
    }
}

/** 概览页：评分分布与标签、角色与声优、Staff、关联作品。 */
@Composable
private fun OverviewTabPage(
    subject: Subject,
    uiState: SubjectDetailUiState,
    displaySubject: Subject,
    onTagClick: (String) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    onPersonClick: (Long) -> Unit,
    onSubjectClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = SubjectTabBottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "rating_distribution") {
            RatingDistributionCard(
                rating = subject.rating,
                collection = subject.collection,
                tags = subject.tags,
                onTagClick = onTagClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (uiState.characters.isNotEmpty()) {
            item(key = "characters_section") {
                CharactersSection(
                    characters = uiState.characters,
                    onCharacterClick = onCharacterClick,
                    onActorClick = onActorClick,
                    onPreviewCharacter = onPreviewCharacter,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (uiState.persons.isNotEmpty()) {
            item(key = "staff_section") {
                StaffSection(
                    persons = uiState.persons,
                    onPersonClick = onPersonClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

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
}

/** 分集页：头部工具条、分组筛选、列表/网格、加载更多。 */
@Composable
private fun EpisodesTabPage(
    uiState: SubjectDetailUiState,
    subjectType: SubjectType,
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
    onSelectEpisodeForDetail: (Episode) -> Unit,
    onEpisodeClickForQuickAction: ((Episode) -> Unit)?,
    onBatchMarkEpisode: (Episode) -> Unit,
    onPlayEpisode: ((Episode) -> Unit)?,
    onOpenSources: (() -> Unit)?,
    onOpenEpisodeSources: ((Episode) -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
) {
    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val gridColumns = if (adaptiveInfo.isWide) 7 else 5
    val pageListState = rememberLazyListState()
    val nextUpEpNumber =
        remember(currentEpisodes, uiState.collection?.epStatus) {
            currentEpisodes
                .firstOrNull {
                    isEpisodeNextToWatch(it, uiState.collection?.epStatus ?: 0, hasProgress = uiState.collection != null)
                }?.episodeNumber
        }

    // 滚动到「加载更多」脚标可见时自动续拉下一屏分集
    LaunchedEffect(pageListState, hasMoreEpisodes) {
        snapshotFlow {
            pageListState.layoutInfo.visibleItemsInfo.any { it.key == "episodes_load_more" }
        }.collect { visible ->
            if (visible && hasMoreEpisodes) onLoadMoreEpisodes()
        }
    }

    LazyColumn(
        state = pageListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = SubjectTabBottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                        onClick = {
                            if (onEpisodeClickForQuickAction != null) {
                                onEpisodeClickForQuickAction(episode)
                            } else {
                                onSelectEpisodeForDetail(episode)
                            }
                        },
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
}

/** 讨论页：评论区与话题。 */
@Composable
private fun CommunityTabPage(
    uiState: SubjectDetailUiState,
    onLoadMoreComments: () -> Unit,
    onLinkClick: (String) -> Unit,
    onTopicClick: (Long, String) -> Unit,
    onCopyComment: ((String) -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = SubjectTabBottomPadding),
    ) {
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
