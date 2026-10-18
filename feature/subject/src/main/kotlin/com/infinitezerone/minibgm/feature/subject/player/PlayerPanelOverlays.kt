package com.infinitezerone.minibgm.feature.subject.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.infinitezerone.minibgm.core.designsystem.component.OverlayProviderScope
import com.infinitezerone.minibgm.core.designsystem.component.OverlayRequest

/**
 * 播放器面板类浮层请求（设置面板 / 横屏选集抽屉 / 竖屏选集弹层）。
 *
 * 三个面板此前是散落的 `var xxxOpen` 布尔状态机：关闭时机要在 BackHandler 回退链、
 * 全屏切换、横竖屏切换、进 PiP 四处手动同步，且多个标记可同时为 true（叠层）。
 * 收敛进 BgmOverlayHost 后：单槽天然互斥、每个面板自带 BackHandler（同层优先级由
 * 单槽语义自动保证）、打开-关闭-取结果的因果写在同一处挂起调用里。
 */
data object PlayerSettingsRequest : OverlayRequest<Unit>

data object PlayerEpisodeQueueRequest : OverlayRequest<Unit>

data object PlayerEpisodesRequest : OverlayRequest<Unit>

data object PlayerSourcesRequest : OverlayRequest<Unit>

/**
 * 播放器横屏右侧抽屉通用容器：
 * 半透明黑色渐变蒙层背景（点击收起） + 右侧平滑滑入滑出抽屉 + 返回键优雅收起 + 动效结束自动响应。
 */
@Composable
private fun PlayerRightDrawerContainer(
    visibleState: MutableTransitionState<Boolean>,
    onDismissRequest: () -> Unit,
    onRespond: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BackHandler { onDismissRequest() }
    SideEffect {
        if (visibleState.isIdle && !visibleState.currentState) {
            onRespond()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(durationMillis = 150)),
            exit = fadeOut(tween(durationMillis = 120)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest,
                        ),
            )
        }
        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 160),
                ),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 140),
                ),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        ) {
            content()
        }
    }
}

/**
 * 播放设置面板（清晰度/倍速/画面比例/字幕/音轨/弹幕/单集循环）：
 * 竖屏模式走优雅底部弹层 [PlayerTrackSelectionSheet]，横屏模式走右侧抽屉 [PlayerTrackSelectionDrawer]，
 * 彻底消除横屏全屏下底部弹窗扁平挤压、大面积遮挡画面的体验缺陷。
 */
fun OverlayProviderScope.playerSettingsPanel(
    tracksSnapshot: () -> PlayerTracksSnapshot,
    playbackSpeed: () -> Float,
    onSelectSpeed: (Float) -> Unit,
    resizeMode: () -> PlayerResizeMode,
    onSelectResizeMode: (PlayerResizeMode) -> Unit,
    isDanmakuEnabled: () -> Boolean,
    onToggleDanmaku: () -> Unit,
    onOpenDanmakuPicker: (() -> Unit)?,
    danmakuTimeOffsetMs: () -> Long = { 0L },
    onSelectDanmakuTimeOffset: (Long) -> Unit = {},
    isLooping: () -> Boolean,
    onToggleLoop: () -> Unit,
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    isLandscape: () -> Boolean = { false },
) {
    overlay<PlayerSettingsRequest, Unit> { _, onRespond ->
        val landscape = isLandscape()
        if (landscape) {
            val visibleState =
                remember {
                    MutableTransitionState(initialState = false).apply { targetState = true }
                }
            PlayerRightDrawerContainer(
                visibleState = visibleState,
                onDismissRequest = { visibleState.targetState = false },
                onRespond = { onRespond(Unit) },
            ) {
                PlayerTrackSelectionDrawer(
                    tracksSnapshot = tracksSnapshot(),
                    onSelectQuality = onSelectQuality,
                    onAutoQuality = onAutoQuality,
                    onSelectSubtitle = onSelectSubtitle,
                    onDisableSubtitles = onDisableSubtitles,
                    onAutoSubtitles = onAutoSubtitles,
                    onSelectAudio = onSelectAudio,
                    playbackSpeed = playbackSpeed(),
                    onSelectSpeed = onSelectSpeed,
                    resizeMode = resizeMode(),
                    onSelectResizeMode = onSelectResizeMode,
                    isDanmakuEnabled = isDanmakuEnabled(),
                    onToggleDanmaku = onToggleDanmaku,
                    onOpenDanmakuPicker =
                        onOpenDanmakuPicker?.let { open ->
                            {
                                visibleState.targetState = false
                                open()
                            }
                        },
                    danmakuTimeOffsetMs = danmakuTimeOffsetMs(),
                    onSelectDanmakuTimeOffset = onSelectDanmakuTimeOffset,
                    isLooping = isLooping(),
                    onToggleLoop = onToggleLoop,
                    onClose = { visibleState.targetState = false },
                )
            }
        } else {
            PlayerTrackSelectionSheet(
                isOpen = true,
                tracksSnapshot = tracksSnapshot(),
                onSelectQuality = onSelectQuality,
                onAutoQuality = onAutoQuality,
                onSelectSubtitle = onSelectSubtitle,
                onDisableSubtitles = onDisableSubtitles,
                onAutoSubtitles = onAutoSubtitles,
                onSelectAudio = onSelectAudio,
                playbackSpeed = playbackSpeed(),
                onSelectSpeed = onSelectSpeed,
                resizeMode = resizeMode(),
                onSelectResizeMode = onSelectResizeMode,
                isDanmakuEnabled = isDanmakuEnabled(),
                onToggleDanmaku = onToggleDanmaku,
                onOpenDanmakuPicker =
                    onOpenDanmakuPicker?.let { open ->
                        {
                            onRespond(Unit)
                            open()
                        }
                    },
                danmakuTimeOffsetMs = danmakuTimeOffsetMs(),
                onSelectDanmakuTimeOffset = onSelectDanmakuTimeOffset,
                isLooping = isLooping(),
                onToggleLoop = onToggleLoop,
                onDismiss = { onRespond(Unit) },
            )
        }
    }
}

/**
 * 横屏右侧选集队列抽屉。带滑入/滑出动画：respond 延迟到退出动画完成，
 * 由 [MutableTransitionState] 驱动，BackHandler/点击遮罩都只是收起动画的触发器。
 */
fun OverlayProviderScope.playerEpisodeQueuePanel(
    uiState: () -> PlayerUiState,
    onLoadMoreEpisodes: () -> Unit,
    onToggleAutoNext: () -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
) {
    overlay<PlayerEpisodeQueueRequest, Unit> { _, onRespond ->
        val state = uiState()
        val visibleState =
            remember {
                MutableTransitionState(initialState = false).apply { targetState = true }
            }
        PlayerRightDrawerContainer(
            visibleState = visibleState,
            onDismissRequest = { visibleState.targetState = false },
            onRespond = { onRespond(Unit) },
        ) {
            PlayerEpisodeQueueDrawer(
                queue = state.queue,
                currentIndex = state.currentIndex,
                episodes = state.episodes,
                selectedEpisodeSort = state.episodeSort,
                selectedEpisodeId = state.episodeId,
                selectedEpisodeType = state.episodeType,
                autoNextEnabled = state.autoNextEnabled,
                totalEpisodes = state.totalEpisodes,
                hasMoreEpisodes = state.hasMoreEpisodes,
                isLoadingMoreEpisodes = state.isLoadingMoreEpisodes,
                onLoadMoreEpisodes = onLoadMoreEpisodes,
                onToggleAutoNext = onToggleAutoNext,
                onSelectEpisode = {
                    onSelectEpisode(it)
                    visibleState.targetState = false
                },
                onSelectQueueIndex = {
                    onSelectQueueIndex(it)
                    visibleState.targetState = false
                },
                onClose = { visibleState.targetState = false },
            )
        }
    }
}

/**
 * 竖屏全量选集底部弹层（Material 3 弹层自带返回键与下滑关闭语义）。
 */
fun OverlayProviderScope.playerEpisodesPanel(
    uiState: () -> PlayerUiState,
    onLoadMoreEpisodes: () -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
) {
    overlay<PlayerEpisodesRequest, Unit> { _, onRespond ->
        val state = uiState()
        PlayerEpisodesBottomSheet(
            episodes = state.episodes,
            selectedEpisodeSort = state.episodeSort,
            selectedEpisodeId = state.episodeId,
            totalEpisodes = state.totalEpisodes,
            hasMoreEpisodes = state.hasMoreEpisodes,
            isLoadingMoreEpisodes = state.isLoadingMoreEpisodes,
            onLoadMoreEpisodes = onLoadMoreEpisodes,
            onSelectEpisode = {
                onSelectEpisode(it)
                onRespond(Unit)
            },
            onDismiss = { onRespond(Unit) },
        )
    }
}

/**
 * 统一播放线路选择弹层：竖屏底部弹窗，横屏右侧抽屉（与选集抽屉对齐风格，避免宽屏遮挡画面）。
 */
fun OverlayProviderScope.playerSourcesPanel(
    uiState: () -> PlayerUiState,
    onSelectSource: (Int) -> Unit,
    onManageRules: (() -> Unit)?,
    onRequestOpenSources: (() -> Unit)? = null,
    isLandscape: () -> Boolean = { false },
) {
    overlay<PlayerSourcesRequest, Unit> { _, onRespond ->
        val state = uiState()
        val landscape = isLandscape()
        if (landscape) {
            val visibleState =
                remember {
                    MutableTransitionState(initialState = false).apply { targetState = true }
                }
            PlayerRightDrawerContainer(
                visibleState = visibleState,
                onDismissRequest = { visibleState.targetState = false },
                onRespond = { onRespond(Unit) },
            ) {
                PlayerSourcesDrawer(
                    sources = state.sources,
                    selectedIndex = state.selectedSourceIndex,
                    playingSourceId = state.playingSourceId,
                    sourceFailureCounts = state.sourceFailureCounts,
                    isResolvingSource = state.isResolvingSource,
                    resolveAttempt = state.resolveAttempt,
                    resolveAttemptTotal = state.resolveAttemptTotal,
                    matchedVodName = state.matchedVodName,
                    onSelectSource = { index ->
                        onSelectSource(index)
                        visibleState.targetState = false
                    },
                    onManageRules =
                        onManageRules?.let { manage ->
                            {
                                visibleState.targetState = false
                                manage()
                            }
                        },
                    onRequestOpenSources =
                        onRequestOpenSources?.let { openAi ->
                            {
                                visibleState.targetState = false
                                openAi()
                            }
                        },
                    onClose = { visibleState.targetState = false },
                )
            }
        } else {
            PlayerSourcesBottomSheet(
                sources = state.sources,
                selectedIndex = state.selectedSourceIndex,
                playingSourceId = state.playingSourceId,
                sourceFailureCounts = state.sourceFailureCounts,
                isResolvingSource = state.isResolvingSource,
                resolveAttempt = state.resolveAttempt,
                resolveAttemptTotal = state.resolveAttemptTotal,
                matchedVodName = state.matchedVodName,
                // 底栏自己负责「先播完收起动效再执行动作」，调用方只交真实动作，
                // 否则同一处会调度两次收起时序（且第一次是瞬间消失、没有动效）
                onSelectSource = onSelectSource,
                onManageRules = onManageRules,
                onRequestOpenSources = onRequestOpenSources,
                onDismiss = { onRespond(Unit) },
            )
        }
    }
}
