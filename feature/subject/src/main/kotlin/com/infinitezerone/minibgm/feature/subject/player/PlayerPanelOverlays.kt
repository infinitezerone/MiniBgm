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

/**
 * 播放设置面板（清晰度/倍速/画面比例/字幕/音轨/弹幕/单集循环）。
 * 竖屏与横屏统一走底部弹层：OverlayHost 渲染的内容独立于舞台层级，
 * 右侧抽屉形态需要依赖舞台 BoxScope 且退出动画要与 respond 时序耦合，收益不抵复杂度。
 */
fun OverlayProviderScope.playerSettingsPanel(
    tracksSnapshot: PlayerTracksSnapshot,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    resizeMode: PlayerResizeMode,
    onSelectResizeMode: (PlayerResizeMode) -> Unit,
    isDanmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onOpenDanmakuPicker: (() -> Unit)?,
    isLooping: Boolean,
    onToggleLoop: () -> Unit,
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
) {
    overlay<PlayerSettingsRequest, Unit> { _, onRespond ->
        PlayerTrackSelectionSheet(
            isOpen = true,
            isLandscape = false,
            tracksSnapshot = tracksSnapshot,
            onSelectQuality = onSelectQuality,
            onAutoQuality = onAutoQuality,
            onSelectSubtitle = onSelectSubtitle,
            onDisableSubtitles = onDisableSubtitles,
            onAutoSubtitles = onAutoSubtitles,
            onSelectAudio = onSelectAudio,
            playbackSpeed = playbackSpeed,
            onSelectSpeed = onSelectSpeed,
            resizeMode = resizeMode,
            onSelectResizeMode = onSelectResizeMode,
            isDanmakuEnabled = isDanmakuEnabled,
            onToggleDanmaku = onToggleDanmaku,
            onOpenDanmakuPicker =
                onOpenDanmakuPicker?.let { open ->
                    {
                        onRespond(Unit)
                        open()
                    }
                },
            isLooping = isLooping,
            onToggleLoop = onToggleLoop,
            onDismiss = { onRespond(Unit) },
        )
    }
}

/**
 * 横屏右侧选集队列抽屉。带滑入/滑出动画：respond 延迟到退出动画完成，
 * 由 [MutableTransitionState] 驱动，BackHandler/点击遮罩都只是收起动画的触发器。
 */
fun OverlayProviderScope.playerEpisodeQueuePanel(
    uiState: PlayerUiState,
    onLoadMoreEpisodes: () -> Unit,
    onToggleAutoNext: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
) {
    overlay<PlayerEpisodeQueueRequest, Unit> { _, onRespond ->
        val visibleState =
            remember {
                MutableTransitionState(initialState = false).apply { targetState = true }
            }
        BackHandler { visibleState.targetState = false }
        // 退出动画播完后再响应（关闭挂起协程并卸载浮层内容）——SideEffect 保证不在组合期产生副作用
        SideEffect {
            if (visibleState.isIdle && !visibleState.currentState) {
                onRespond(Unit)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(durationMillis = 200)),
                exit = fadeOut(tween(durationMillis = 200)),
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
                                onClick = { visibleState.targetState = false },
                            ),
                )
            }
            AnimatedVisibility(
                visibleState = visibleState,
                enter =
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 250),
                    ),
                exit =
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 200),
                    ),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            ) {
                PlayerEpisodeQueueDrawer(
                    queue = uiState.queue,
                    currentIndex = uiState.currentIndex,
                    episodes = uiState.episodes,
                    selectedEpisodeSort = uiState.episodeSort,
                    selectedEpisodeId = uiState.episodeId,
                    selectedEpisodeType = uiState.episodeType,
                    sources = uiState.sources,
                    sourceFailureCounts = uiState.sourceFailureCounts,
                    selectedSourceIndex = uiState.selectedSourceIndex,
                    autoNextEnabled = uiState.autoNextEnabled,
                    totalEpisodes = uiState.totalEpisodes,
                    hasMoreEpisodes = uiState.hasMoreEpisodes,
                    isLoadingMoreEpisodes = uiState.isLoadingMoreEpisodes,
                    onLoadMoreEpisodes = onLoadMoreEpisodes,
                    onToggleAutoNext = onToggleAutoNext,
                    onSelectSource = onSelectSource,
                    onSelectEpisode = {
                        onSelectEpisode(it)
                        onRespond(Unit)
                    },
                    onSelectQueueIndex = {
                        onSelectQueueIndex(it)
                        onRespond(Unit)
                    },
                    onClose = { visibleState.targetState = false },
                )
            }
        }
    }
}

/**
 * 竖屏全量选集底部弹层（Material 3 弹层自带返回键与下滑关闭语义）。
 */
fun OverlayProviderScope.playerEpisodesPanel(
    uiState: PlayerUiState,
    onLoadMoreEpisodes: () -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
) {
    overlay<PlayerEpisodesRequest, Unit> { _, onRespond ->
        PlayerEpisodesBottomSheet(
            episodes = uiState.episodes,
            selectedEpisodeSort = uiState.episodeSort,
            selectedEpisodeId = uiState.episodeId,
            totalEpisodes = uiState.totalEpisodes,
            hasMoreEpisodes = uiState.hasMoreEpisodes,
            isLoadingMoreEpisodes = uiState.isLoadingMoreEpisodes,
            onLoadMoreEpisodes = onLoadMoreEpisodes,
            onSelectEpisode = {
                onSelectEpisode(it)
                onRespond(Unit)
            },
            onDismiss = { onRespond(Unit) },
        )
    }
}
