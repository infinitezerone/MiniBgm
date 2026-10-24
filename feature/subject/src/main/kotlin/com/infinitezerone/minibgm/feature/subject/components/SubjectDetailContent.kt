package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import kotlin.math.roundToInt

/** 顶栏与头部 Hero 之间的初始间距。 */
private val SubjectHeaderTopPadding = 4.dp

/** 头部 Hero 与 Tab 栏之间的呼吸距离；随头部一同参与收合。 */
private val SubjectHeaderTabGap = 14.dp

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
    // 去除人造突变硬吸顶阈值，平滑跟随手势位移。
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

    // 纯净手势联动：跟手位移，禁止抬手强制贴合全吸顶
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
        // 动态底部安全内边距：随头部展开动态补偿尾部留白，展开时绝不截断内容，收合时恢复默认 24dp 呼吸距离
        val dynamicBottomPadding =
            with(density) {
                (headerCollapse.heightPx - headerCollapse.offsetPx).coerceAtLeast(0f).toDp() + SubjectTabBottomPadding
            }

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
                                bottomPadding = dynamicBottomPadding,
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
                                bottomPadding = dynamicBottomPadding,
                            )

                        SubjectDetailTab.COMMUNITY ->
                            CommunityTabPage(
                                uiState = uiState,
                                onLoadMoreComments = onLoadMoreComments,
                                onLinkClick = onLinkClick,
                                onTopicClick = onTopicClick,
                                onCopyComment = onCopyComment,
                                bottomPadding = dynamicBottomPadding,
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
