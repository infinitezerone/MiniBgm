package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectDetailTab
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState
import com.infinitezerone.minibgm.feature.subject.computeSubjectWatchStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** 顶栏与头部 Hero 之间的初始间距。 */
private val SubjectHeaderTopPadding = 4.dp

/** 头部 Hero 与 Tab 栏之间的呼吸距离；随头部一同参与收合。 */
private val SubjectHeaderTabGap = 14.dp

/** 各 Tab 页列表底部的呼吸距离。 */
private val SubjectTabBottomPadding = 24.dp

/** Tab 页内容与吸顶 Tab 栏的呼吸间距：属于滚动内容，随页面滚动自然滑入 Tab 栏下方 */
private val SubjectTabTopPadding = 12.dp

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
 * （概览、讨论），每页独立滚动状态，左右滑动切页。分集是播放工具，由 Bottom Sheet 承接。
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
    onOpenCollectionSheet: () -> Unit,
    onUpdateCollectionStatus: ((CollectionType) -> Unit)? = null,
    onSubjectClick: (Long) -> Unit,
    onTagClick: (String) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    onLinkClick: (String) -> Unit,
    onTopicClick: (Long, String) -> Unit,
    onLoadMoreComments: () -> Unit,
    isTransitionStabilizing: Boolean,
    onCopyComment: ((String) -> Unit)? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onOpenEpisodePicker: (() -> Unit)? = null,
    onSelectEpisode: ((com.infinitezerone.minibgm.core.model.Episode) -> Unit)? = null,
    onIncrementWatched: (() -> Unit)? = null,
    onDecrementWatched: (() -> Unit)? = null,
    onPromptLogin: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPlayNextLongClick: (() -> Unit)? = null,
    topPadding: Dp = 12.dp,
    headerCollapse: SubjectHeaderCollapseState,
    onTitleBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val cancelFling: () -> Unit = {
        flingJob?.cancel()
        flingJob = null
    }

    // 头部收合 nested scroll：上滑先收头部再滚列表；列表到顶后下拉先展头部再交还 overscroll。
    // 去除人造突变硬吸顶阈值，平滑跟随手势位移；下层手势或滚动触发时立即打断上层惯性。
    val collapseConnection =
        remember(headerCollapse) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    cancelFling()
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
                    cancelFling()
                    val dy = available.y
                    val height = headerCollapse.heightPx
                    if (dy <= 0f || height <= 0f) return Offset.Zero
                    val previous = headerCollapse.offsetPx
                    val next = (previous - dy).coerceIn(0f, height)
                    headerCollapse.offsetPx = next
                    return Offset(0f, previous - next)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    cancelFling()
                    return Velocity.Zero
                }
            }
        }

    // 纯净手势联动：跟手位移 + 平台级自然物理惯性衰减（杜绝强行贴合吸顶，手停即停，轻甩自然滑行）
    val headerDragModifier =
        Modifier.draggable(
            state =
                rememberDraggableState { delta ->
                    cancelFling()
                    val height = headerCollapse.heightPx
                    if (height > 0f) {
                        headerCollapse.offsetPx = (headerCollapse.offsetPx - delta).coerceIn(0f, height)
                    }
                },
            orientation = Orientation.Vertical,
            onDragStarted = {
                cancelFling()
            },
            onDragStopped = { velocity ->
                val height = headerCollapse.heightPx
                if (height > 0f && velocity.absoluteValue > 80f) {
                    flingJob =
                        coroutineScope.launch {
                            val anim = Animatable(headerCollapse.offsetPx)
                            anim.animateDecay(
                                initialVelocity = -velocity,
                                animationSpec = decay,
                            ) {
                                val newOffset = value
                                headerCollapse.offsetPx = newOffset.coerceIn(0f, height)
                                if (newOffset >= height || newOffset <= 0f) {
                                    this@launch.cancel()
                                }
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

    if (fullSubject == null) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .clipToBounds(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                if (uiState.error != null) {
                    SubjectInlineError(message = uiState.error.orEmpty())
                }
                Box(modifier = Modifier.fillMaxWidth().padding(top = SubjectHeaderTopPadding)) {
                    SubjectHeader(
                        displaySubject = displaySubject,
                        subjectType = subjectType,
                        uiState = uiState,
                        source = source,
                        totalEpisodes = totalEpisodes,
                        onOpenCollectionSheet = onOpenCollectionSheet,
                        onUpdateCollectionStatus = onUpdateCollectionStatus,
                        onTagClick = onTagClick,
                        onPlayNextEpisode = onPlayNextEpisode,
                        onOpenEpisodePicker = onOpenEpisodePicker,
                        onSelectEpisode = onSelectEpisode,
                        onIncrementWatched = onIncrementWatched,
                        onDecrementWatched = onDecrementWatched,
                        onPromptLogin = onPromptLogin,
                        onShare = onShare,
                        onTitleBoundsChanged = onTitleBoundsChanged,
                        onPlayNextLongClick = onPlayNextLongClick,
                    )
                }
                if (uiState.isLoading || isTransitionStabilizing) {
                    SubjectDetailBodySkeleton(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    } else {
        val subject = fullSubject
        Layout(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    cancelFling()
                                }
                            }
                        }
                    }.nestedScroll(collapseConnection),
            content = {
                // Measurable 0: 头部收合区（错误提示 + 上间距 + Hero 卡片 + 与 Tab 栏的呼吸距离）
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier),
                ) {
                    Column {
                        if (uiState.error != null) {
                            SubjectInlineError(message = uiState.error.orEmpty())
                        }
                        Spacer(Modifier.height(SubjectHeaderTopPadding))
                        SubjectHeader(
                            displaySubject = displaySubject,
                            subjectType = subjectType,
                            uiState = uiState,
                            source = source,
                            totalEpisodes = totalEpisodes,
                            onOpenCollectionSheet = onOpenCollectionSheet,
                            onUpdateCollectionStatus = onUpdateCollectionStatus,
                            onTagClick = onTagClick,
                            onPlayNextEpisode = onPlayNextEpisode,
                            onOpenEpisodePicker = onOpenEpisodePicker,
                            onSelectEpisode = onSelectEpisode,
                            onIncrementWatched = onIncrementWatched,
                            onDecrementWatched = onDecrementWatched,
                            onPromptLogin = onPromptLogin,
                            onShare = onShare,
                            onTitleBoundsChanged = onTitleBoundsChanged,
                            onPlayNextLongClick = onPlayNextLongClick,
                        )
                        Spacer(Modifier.height(SubjectHeaderTabGap))
                    }
                }

                // Measurable 1: 吸顶二级 Tab 栏
                val collapseRatio =
                    if (headerCollapse.heightPx > 0f) {
                        (headerCollapse.offsetPx / headerCollapse.heightPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                Box(modifier = Modifier.fillMaxWidth().then(headerDragModifier)) {
                    SubjectDetailTabBar(
                        pagerState = pagerState,
                        collapseRatio = collapseRatio,
                        onSelectTab = onSelectTab,
                    )
                }

                // Measurable 2: Pager 分页内容容器（概览 + 讨论）
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    beyondViewportPageCount = SubjectDetailTab.entries.size - 1,
                ) { page ->
                    when (SubjectDetailTab.entries.getOrElse(page) { SubjectDetailTab.OVERVIEW }) {
                        SubjectDetailTab.OVERVIEW ->
                            OverviewTabPage(
                                subject = subject,
                                displaySubject = displaySubject,
                                uiState = uiState,
                                onTagClick = onTagClick,
                                onCharacterClick = onCharacterClick,
                                onActorClick = onPersonClick,
                                onPreviewCharacter = onPreviewCharacter,
                                onPersonClick = onPersonClick,
                                onSubjectClick = onSubjectClick,
                                topPadding = SubjectTabTopPadding,
                                bottomPadding = SubjectTabBottomPadding,
                            )

                        SubjectDetailTab.COMMUNITY ->
                            CommunityTabPage(
                                uiState = uiState,
                                onLoadMoreComments = onLoadMoreComments,
                                onLinkClick = onLinkClick,
                                onTopicClick = onTopicClick,
                                onCopyComment = onCopyComment,
                                topPadding = SubjectTabTopPadding,
                                bottomPadding = SubjectTabBottomPadding,
                            )
                    }
                }
            },
        ) { measurables, constraints ->
            val headerPlaceable =
                measurables[0].measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                )
            val headerHeight = headerPlaceable.height
            headerCollapse.heightPx = headerHeight.toFloat()

            val tabBarPlaceable =
                measurables[1].measure(
                    constraints.copy(minHeight = 0, maxHeight = constraints.maxHeight),
                )
            val tabBarHeight = tabBarPlaceable.height

            val pagerHeight = (constraints.maxHeight - tabBarHeight).coerceAtLeast(0)
            val pagerPlaceable =
                measurables[2].measure(
                    constraints.copy(
                        minWidth = constraints.maxWidth,
                        maxWidth = constraints.maxWidth,
                        minHeight = pagerHeight,
                        maxHeight = pagerHeight,
                    ),
                )

            layout(constraints.maxWidth, constraints.maxHeight) {
                val offset = headerCollapse.offsetPx.coerceIn(0f, headerHeight.toFloat()).roundToInt()
                headerPlaceable.placeRelative(0, -offset)
                tabBarPlaceable.placeRelative(0, headerHeight - offset)
                pagerPlaceable.placeRelative(0, headerHeight + tabBarHeight - offset)
            }
        }
    }
}

/** 头部 Hero 区（含进卡、CTA 与选集枢纽），收合分支与骨架分支共用。 */
@Composable
private fun SubjectHeader(
    displaySubject: Subject,
    subjectType: SubjectType,
    uiState: SubjectDetailUiState,
    source: String,
    totalEpisodes: Int,
    onOpenCollectionSheet: () -> Unit,
    onUpdateCollectionStatus: ((CollectionType) -> Unit)?,
    onTagClick: (String) -> Unit,
    onPlayNextEpisode: (() -> Unit)?,
    onOpenEpisodePicker: (() -> Unit)?,
    onSelectEpisode: ((com.infinitezerone.minibgm.core.model.Episode) -> Unit)? = null,
    onIncrementWatched: (() -> Unit)?,
    onDecrementWatched: (() -> Unit)?,
    onPromptLogin: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onTitleBoundsChanged: (Rect) -> Unit,
    onPlayNextLongClick: (() -> Unit)? = null,
) {
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
        episodes = uiState.episodes,
        onSelectEpisode = onSelectEpisode,
        onOpenCollectionSheet = onOpenCollectionSheet,
        onUpdateCollectionStatus = onUpdateCollectionStatus,
        onTagClick = onTagClick,
        onPlayNext = onPlayNextEpisode,
        onIncrementWatched = onIncrementWatched,
        onDecrementWatched = onDecrementWatched,
        onPromptLogin = onPromptLogin,
        watchStatus = watchStatus,
        onShare = onShare,
        onOpenEpisodePicker = onOpenEpisodePicker,
        onTitleBoundsChanged = onTitleBoundsChanged,
        onPlayNextLongClick = onPlayNextLongClick,
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
