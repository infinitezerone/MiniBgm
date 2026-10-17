package com.infinitezerone.minibgm.feature.subject.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 双击侧边（左退右进）及累积快进快退秒数（对齐现代 YouTube 视觉微动效）
 */
internal enum class DoubleTapSide {
    LEFT,
    RIGHT,
}

internal data class DoubleTapIndicatorState(
    val side: DoubleTapSide,
    val seconds: Int,
    val token: Long = System.currentTimeMillis(),
)

/**
 * 进度拖拽自适应计算算法：
 * 针对短视频、普通 24 分钟番剧、长电影设定更跟手的滑屏距离跨度。
 */
internal fun calculateSeekDeltaMs(
    dragDeltaX: Float,
    width: Float,
    totalDurationMs: Long,
): Long {
    if (width <= 0f || totalDurationMs <= 0L) return 0L
    val fraction = dragDeltaX / width
    val maxSpanMs =
        when {
            totalDurationMs <= 5 * 60 * 1000L -> 60 * 1000L
            totalDurationMs <= 30 * 60 * 1000L -> 180 * 1000L
            else -> (totalDurationMs * 0.10f).toLong().coerceIn(300 * 1000L, 900 * 1000L)
        }
    return (fraction * maxSpanMs).toLong()
}

/**
 * 手势 HUD 类型
 */
internal sealed interface GestureHudState {
    data object Idle : GestureHudState

    data class Brightness(
        val percent: Int,
    ) : GestureHudState

    data class Volume(
        val percent: Int,
    ) : GestureHudState

    data class Seek(
        val targetMs: Long,
        val totalMs: Long,
        val deltaMs: Long,
    ) : GestureHudState

    data class FastForward(
        val speed: Float,
    ) : GestureHudState
}

private enum class DragMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME,
}

internal fun Modifier.playerGestures(
    context: Context,
    isPlaying: Boolean,
    currentPositionMs: () -> Long,
    totalDurationMs: () -> Long,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onSeekConfirm: (Long) -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    onHudStateChange: (GestureHudState) -> Unit,
    onTriggerHudDismiss: () -> Unit,
    isLocked: Boolean = false,
    onDoubleTapAction: (DoubleTapSide, Int) -> Unit = { _, _ -> },
    onHaptic: (HapticFeedbackType) -> Unit = {},
    onScrubbing: (isScrubbing: Boolean, progress: Float) -> Unit = { _, _ -> },
): Modifier =
    this then
        PlayerGesturesElement(
            context = context,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSingleTap = onSingleTap,
            onDoubleTapSeek = onDoubleTapSeek,
            onDoubleTapPlayPause = onDoubleTapPlayPause,
            onSeekConfirm = onSeekConfirm,
            onFastForwardStart = onFastForwardStart,
            onFastForwardEnd = onFastForwardEnd,
            onHudStateChange = onHudStateChange,
            onTriggerHudDismiss = onTriggerHudDismiss,
            isLocked = isLocked,
            onDoubleTapAction = onDoubleTapAction,
            onHaptic = onHaptic,
            onScrubbing = onScrubbing,
        )

private data class PlayerGesturesElement(
    val context: Context,
    val isPlaying: Boolean,
    val currentPositionMs: () -> Long,
    val totalDurationMs: () -> Long,
    val onSingleTap: () -> Unit,
    val onDoubleTapSeek: (Long) -> Unit,
    val onDoubleTapPlayPause: () -> Unit,
    val onSeekConfirm: (Long) -> Unit,
    val onFastForwardStart: () -> Unit,
    val onFastForwardEnd: () -> Unit,
    val onHudStateChange: (GestureHudState) -> Unit,
    val onTriggerHudDismiss: () -> Unit,
    val isLocked: Boolean = false,
    val onDoubleTapAction: (DoubleTapSide, Int) -> Unit = { _, _ -> },
    val onHaptic: (HapticFeedbackType) -> Unit = {},
    val onScrubbing: (isScrubbing: Boolean, progress: Float) -> Unit = { _, _ -> },
) : ModifierNodeElement<PlayerGesturesNode>() {
    override fun create(): PlayerGesturesNode =
        PlayerGesturesNode(
            context = context,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSingleTap = onSingleTap,
            onDoubleTapSeek = onDoubleTapSeek,
            onDoubleTapPlayPause = onDoubleTapPlayPause,
            onSeekConfirm = onSeekConfirm,
            onFastForwardStart = onFastForwardStart,
            onFastForwardEnd = onFastForwardEnd,
            onHudStateChange = onHudStateChange,
            onTriggerHudDismiss = onTriggerHudDismiss,
            isLocked = isLocked,
            onDoubleTapAction = onDoubleTapAction,
            onHaptic = onHaptic,
            onScrubbing = onScrubbing,
        )

    override fun update(node: PlayerGesturesNode) {
        node.update(
            context = context,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSingleTap = onSingleTap,
            onDoubleTapSeek = onDoubleTapSeek,
            onDoubleTapPlayPause = onDoubleTapPlayPause,
            onSeekConfirm = onSeekConfirm,
            onFastForwardStart = onFastForwardStart,
            onFastForwardEnd = onFastForwardEnd,
            onHudStateChange = onHudStateChange,
            onTriggerHudDismiss = onTriggerHudDismiss,
            isLocked = isLocked,
            onDoubleTapAction = onDoubleTapAction,
            onHaptic = onHaptic,
            onScrubbing = onScrubbing,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "playerGestures"
        properties["isPlaying"] = isPlaying
        properties["currentPositionMs"] = currentPositionMs
        properties["totalDurationMs"] = totalDurationMs
        properties["isLocked"] = isLocked
    }
}

private class PlayerGesturesNode(
    var context: Context,
    var isPlaying: Boolean,
    var currentPositionMs: () -> Long,
    var totalDurationMs: () -> Long,
    var onSingleTap: () -> Unit,
    var onDoubleTapSeek: (Long) -> Unit,
    var onDoubleTapPlayPause: () -> Unit,
    var onSeekConfirm: (Long) -> Unit,
    var onFastForwardStart: () -> Unit,
    var onFastForwardEnd: () -> Unit,
    var onHudStateChange: (GestureHudState) -> Unit,
    var onTriggerHudDismiss: () -> Unit,
    var isLocked: Boolean = false,
    var onDoubleTapAction: (DoubleTapSide, Int) -> Unit = { _, _ -> },
    var onHaptic: (HapticFeedbackType) -> Unit = {},
    var onScrubbing: (isScrubbing: Boolean, progress: Float) -> Unit = { _, _ -> },
) : DelegatingNode() {
    private var isFastForwarding = false
    private var dragMode = DragMode.NONE
    private var dragAccumulatedX = 0f
    private var dragAccumulatedY = 0f
    private var seekTargetMs = 0L
    private var initialBrightness = 0.5f
    private var initialVolume = 0f

    // 连续双击累加状态（对齐 YouTube 连续双击 +10s, +20s, +30s ... 累加与防抖提交）
    private var lastDoubleTapSide: DoubleTapSide? = null
    private var lastDoubleTapTime: Long = 0L
    private var accumulatedSeconds: Int = 0
    private var lastSeekBasePositionMs: Long = 0L
    private var pendingSeekJob: Job? = null
    private var pendingSeekTargetMs: Long = 0L

    private fun handleQuickSeek(side: DoubleTapSide) {
        val now = System.currentTimeMillis()
        val total = totalDurationMs()
        val isSameSideInWindow =
            lastDoubleTapSide == side && (now - lastDoubleTapTime) <= DOUBLE_TAP_WINDOW_MS
        val basePos =
            if (isSameSideInWindow) lastSeekBasePositionMs else currentPositionMs()
        accumulatedSeconds =
            if (isSameSideInWindow) accumulatedSeconds + 10 else 10
        lastDoubleTapSide = side
        lastDoubleTapTime = now
        lastSeekBasePositionMs = basePos

        val deltaMs =
            if (side == DoubleTapSide.RIGHT) accumulatedSeconds * 1000L else -accumulatedSeconds * 1000L
        val maxPos = if (total > 0L) total else Long.MAX_VALUE
        val target = (basePos + deltaMs).coerceIn(0L, maxPos)
        pendingSeekTargetMs = target

        // 视觉与触觉反馈即时响应（数字累加、气泡波纹、HUD提示）
        onDoubleTapAction(side, accumulatedSeconds)
        onHaptic(HapticFeedbackType.LongPress)
        onHudStateChange(GestureHudState.Seek(target, total, deltaMs))
        onTriggerHudDismiss()

        // 防抖延迟提交：连续点击期间保持平稳不跳针，停手冷却后一次性提交跳转
        pendingSeekJob?.cancel()
        pendingSeekJob =
            coroutineScope.launch {
                delay(DOUBLE_TAP_COMMIT_DELAY_MS)
                onDoubleTapSeek(target)
                lastDoubleTapSide = null
                accumulatedSeconds = 0
                pendingSeekJob = null
            }
    }

    private fun commitPendingQuickSeek() {
        if (pendingSeekJob?.isActive == true) {
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            onDoubleTapSeek(pendingSeekTargetMs)
            lastDoubleTapSide = null
            accumulatedSeconds = 0
        }
    }

    override fun onDetach() {
        pendingSeekJob?.cancel()
        pendingSeekJob = null
        super.onDetach()
    }

    fun update(
        context: Context,
        isPlaying: Boolean,
        currentPositionMs: () -> Long,
        totalDurationMs: () -> Long,
        onSingleTap: () -> Unit,
        onDoubleTapSeek: (Long) -> Unit,
        onDoubleTapPlayPause: () -> Unit,
        onSeekConfirm: (Long) -> Unit,
        onFastForwardStart: () -> Unit,
        onFastForwardEnd: () -> Unit,
        onHudStateChange: (GestureHudState) -> Unit,
        onTriggerHudDismiss: () -> Unit,
        isLocked: Boolean = false,
        onDoubleTapAction: (DoubleTapSide, Int) -> Unit = { _, _ -> },
        onHaptic: (HapticFeedbackType) -> Unit = {},
        onScrubbing: (isScrubbing: Boolean, progress: Float) -> Unit = { _, _ -> },
    ) {
        this.context = context
        this.isPlaying = isPlaying
        this.currentPositionMs = currentPositionMs
        this.totalDurationMs = totalDurationMs
        this.onSingleTap = onSingleTap
        this.onDoubleTapSeek = onDoubleTapSeek
        this.onDoubleTapPlayPause = onDoubleTapPlayPause
        this.onSeekConfirm = onSeekConfirm
        this.onFastForwardStart = onFastForwardStart
        this.onFastForwardEnd = onFastForwardEnd
        this.onHudStateChange = onHudStateChange
        this.onTriggerHudDismiss = onTriggerHudDismiss
        this.isLocked = isLocked
        this.onDoubleTapAction = onDoubleTapAction
        this.onHaptic = onHaptic
        this.onScrubbing = onScrubbing
    }

    @Suppress("unused")
    private val tapNode =
        delegate(
            SuspendingPointerInputModifierNode {
                detectTapGestures(
                    onTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        val now = System.currentTimeMillis()
                        val width = size.width
                        val isLeftActive =
                            lastDoubleTapSide == DoubleTapSide.LEFT &&
                                (now - lastDoubleTapTime) <= DOUBLE_TAP_WINDOW_MS &&
                                offset.x < width * 0.35f
                        val isRightActive =
                            lastDoubleTapSide == DoubleTapSide.RIGHT &&
                                (now - lastDoubleTapTime) <= DOUBLE_TAP_WINDOW_MS &&
                                offset.x > width * 0.65f

                        when {
                            isLeftActive -> handleQuickSeek(DoubleTapSide.LEFT)
                            isRightActive -> handleQuickSeek(DoubleTapSide.RIGHT)
                            else -> {
                                commitPendingQuickSeek()
                                onSingleTap()
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        val width = size.width
                        val x = offset.x
                        when {
                            x < width * 0.35f -> handleQuickSeek(DoubleTapSide.LEFT)
                            x > width * 0.65f -> handleQuickSeek(DoubleTapSide.RIGHT)
                            else -> {
                                commitPendingQuickSeek()
                                onHaptic(HapticFeedbackType.LongPress)
                                onDoubleTapPlayPause()
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked && isPlaying) {
                            commitPendingQuickSeek()
                            isFastForwarding = true
                            onHaptic(HapticFeedbackType.LongPress)
                            onFastForwardStart()
                            onHudStateChange(GestureHudState.FastForward(2.0f))
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isFastForwarding) {
                            isFastForwarding = false
                            onHaptic(HapticFeedbackType.TextHandleMove)
                            onFastForwardEnd()
                            onHudStateChange(GestureHudState.Idle)
                        }
                    },
                )
            },
        )

    @Suppress("unused")
    private val dragNode =
        delegate(
            SuspendingPointerInputModifierNode {
                var startOffset = Offset.Zero
                val thresholdPx = 14.dp.toPx()
                var initialPositionMs = 0L

                detectDragGestures(
                    onDragStart = { offset ->
                        if (isLocked) {
                            dragMode = DragMode.NONE
                            return@detectDragGestures
                        }
                        commitPendingQuickSeek()
                        startOffset = offset
                        dragMode = DragMode.NONE
                        dragAccumulatedX = 0f
                        dragAccumulatedY = 0f
                        initialPositionMs = currentPositionMs()
                        seekTargetMs = initialPositionMs

                        // 记录起始亮度
                        val activity = context.findActivity()
                        val windowLp = activity?.window?.attributes
                        initialBrightness = windowLp?.screenBrightness?.takeIf { it in 0.01f..1f } ?: 0.5f

                        // 记录起始音量
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                        initialVolume = currentVol.toFloat()
                    },
                    onDrag = { change, dragAmount ->
                        if (isLocked) return@detectDragGestures
                        change.consume()
                        dragAccumulatedX += dragAmount.x
                        dragAccumulatedY += dragAmount.y

                        val width = size.width
                        val height = size.height
                        val total = totalDurationMs()

                        if (dragMode == DragMode.NONE) {
                            if (abs(dragAccumulatedX) > abs(dragAccumulatedY) && abs(dragAccumulatedX) > thresholdPx) {
                                dragMode = DragMode.HORIZONTAL_SEEK
                                val initialFraction =
                                    if (total >
                                        0L
                                    ) {
                                        (initialPositionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                onScrubbing(true, initialFraction)
                            } else if (abs(dragAccumulatedY) > thresholdPx) {
                                dragMode =
                                    if (startOffset.x < width * 0.5f) {
                                        DragMode.VERTICAL_BRIGHTNESS
                                    } else {
                                        DragMode.VERTICAL_VOLUME
                                    }
                            }
                        }

                        when (dragMode) {
                            DragMode.HORIZONTAL_SEEK -> {
                                if (total > 0L) {
                                    val effectiveDragX =
                                        if (dragAccumulatedX > 0f) {
                                            (dragAccumulatedX - thresholdPx).coerceAtLeast(0f)
                                        } else {
                                            (dragAccumulatedX + thresholdPx).coerceAtMost(0f)
                                        }
                                    val deltaMs = calculateSeekDeltaMs(effectiveDragX, width.toFloat(), total)
                                    seekTargetMs = (initialPositionMs + deltaMs).coerceIn(0L, total)
                                    val currentProgress = (seekTargetMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    onScrubbing(true, currentProgress)
                                    onHudStateChange(GestureHudState.Seek(seekTargetMs, total, seekTargetMs - initialPositionMs))
                                }
                            }
                            DragMode.VERTICAL_BRIGHTNESS -> {
                                val delta = -dragAccumulatedY / height
                                val newBrightness = (initialBrightness + delta).coerceIn(0.01f, 1f)
                                context.findActivity()?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                }
                                onHudStateChange(GestureHudState.Brightness((newBrightness * 100).roundToInt()))
                            }
                            DragMode.VERTICAL_VOLUME -> {
                                val delta = -dragAccumulatedY / height
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                val targetVol = (initialVolume + delta * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                val percent = if (maxVolume > 0) ((targetVol.toFloat() / maxVolume) * 100).roundToInt() else 0
                                onHudStateChange(GestureHudState.Volume(percent))
                            }
                            DragMode.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        if (isLocked) return@detectDragGestures
                        val wasSeeking = dragMode == DragMode.HORIZONTAL_SEEK
                        val total = totalDurationMs()
                        if (wasSeeking && total > 0L) {
                            onSeekConfirm(seekTargetMs)
                        }
                        if (wasSeeking) {
                            val finalProgress = if (total > 0L) (seekTargetMs.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            onScrubbing(false, finalProgress)
                        }
                        dragMode = DragMode.NONE
                        onTriggerHudDismiss()
                    },
                    onDragCancel = {
                        if (isLocked) return@detectDragGestures
                        if (dragMode == DragMode.HORIZONTAL_SEEK) {
                            val total = totalDurationMs()
                            val cancelProgress = if (total > 0L) (seekTargetMs.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            onScrubbing(false, cancelProgress)
                        }
                        dragMode = DragMode.NONE
                        onTriggerHudDismiss()
                    },
                )
            },
        )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * 播放器手势检测与浮动 HUD 交互层
 */
@Composable
internal fun PlayerGestureDetector(
    isPlaying: Boolean,
    currentPositionMs: () -> Long,
    totalDurationMs: () -> Long,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onSeekConfirm: (Long) -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onScrubbing: (isScrubbing: Boolean, progress: Float) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var hudState by remember { mutableStateOf<GestureHudState>(GestureHudState.Idle) }
    var hideHudTrigger by remember { mutableIntStateOf(0) }
    var activeDoubleTap by remember { mutableStateOf<DoubleTapIndicatorState?>(null) }
    var doubleTapTrigger by remember { mutableLongStateOf(0L) }

    // 手势结束后自动淡出 HUD
    LaunchedEffect(hideHudTrigger) {
        if (hideHudTrigger > 0) {
            delay(800)
            if (hudState !is GestureHudState.FastForward) {
                hudState = GestureHudState.Idle
            }
        }
    }

    // 双击微动效 650ms 后自动平滑淡出
    LaunchedEffect(doubleTapTrigger) {
        if (doubleTapTrigger > 0L) {
            delay(650)
            activeDoubleTap = null
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .playerGestures(
                    context = context,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    onSingleTap = onSingleTap,
                    onDoubleTapSeek = onDoubleTapSeek,
                    onDoubleTapPlayPause = onDoubleTapPlayPause,
                    onSeekConfirm = onSeekConfirm,
                    onFastForwardStart = onFastForwardStart,
                    onFastForwardEnd = onFastForwardEnd,
                    onHudStateChange = { hudState = it },
                    onTriggerHudDismiss = { hideHudTrigger++ },
                    isLocked = isLocked,
                    onDoubleTapAction = { side, seconds ->
                        val token = System.currentTimeMillis()
                        activeDoubleTap = DoubleTapIndicatorState(side = side, seconds = seconds, token = token)
                        doubleTapTrigger = token
                    },
                    onHaptic = { type ->
                        haptic.performHapticFeedback(type)
                    },
                    onScrubbing = onScrubbing,
                ),
    ) {
        content()

        // 现代 YouTube 风格双击微动效（无大面积涟漪遮挡，仅侧边灵动小胶囊 + 累加数字跳动）
        AnimatedVisibility(
            visible = activeDoubleTap != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f),
            modifier = Modifier.fillMaxSize(),
        ) {
            activeDoubleTap?.let { state ->
                YouTubeDoubleTapIndicator(state = state)
            }
        }

        // 浮动 HUD 指示层（侧边竖向灵动微胶囊 + 顶部/中央提示）
        AnimatedVisibility(
            visible = hudState !is GestureHudState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = hudState) {
                    is GestureHudState.Brightness -> {
                        TopCapsuleIndicator(
                            icon = BgmIcons.BrightnessLow,
                            percent = state.percent,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    is GestureHudState.Volume -> {
                        TopCapsuleIndicator(
                            icon = if (state.percent == 0) BgmIcons.VolumeMute else BgmIcons.VolumeUp,
                            percent = state.percent,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    is GestureHudState.Seek -> {
                        SeekFeedbackCard(
                            targetMs = state.targetMs,
                            totalMs = state.totalMs,
                            deltaMs = state.deltaMs,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    is GestureHudState.FastForward -> {
                        FastForwardChip(
                            speed = state.speed,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    is GestureHudState.Idle -> {}
                }
            }
        }
    }
}

/**
 * 屏幕顶部正上方横向灵动微胶囊指示器（防大拇指遮挡、视线居中自然、不挡字幕）
 */
@Composable
private fun TopCapsuleIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        contentColor = Color.White,
        modifier = modifier.padding(top = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            // 横向胶囊填充轨
            Box(
                modifier =
                    Modifier
                        .width(108.dp)
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(percent = 50)),
                )
            }
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/**
 * 进度快进/快退 HUD 卡片
 */
@Composable
private fun SeekFeedbackCard(
    targetMs: Long,
    totalMs: Long,
    deltaMs: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        contentColor = Color.White,
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (deltaMs >= 0) BgmIcons.FastForward else BgmIcons.FastRewind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                val deltaSec = (abs(deltaMs) / 1000).toInt()
                Text(
                    text = "${if (deltaMs >= 0) "+" else "-"}${deltaSec}s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (deltaMs >= 0) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            Text(
                text = "${formatDuration(targetMs)} / ${formatDuration(totalMs)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

/**
 * 长按倍速微胶囊指示器（置于顶部中央，避免遮挡字幕）
 */
@Composable
private fun FastForwardChip(
    speed: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        contentColor = Color.White,
        modifier = modifier.padding(top = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = BgmIcons.FastForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.feature_subject_player_speed_fast_forward, speed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/**
 * 现代 YouTube 风格双击快进/快退指示器：
 * 移除大面积老式弧形涟漪遮罩，采用极简灵动药丸（居中偏侧），
 * 搭配微缩放、快进/快退双箭头与实时累加秒数（如 "10 秒" / "20 秒" / "30 秒"）。
 */
@Composable
private fun YouTubeDoubleTapIndicator(
    state: DoubleTapIndicatorState,
    modifier: Modifier = Modifier,
) {
    val isLeft = state.side == DoubleTapSide.LEFT
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            contentColor = Color.White,
            modifier =
                Modifier
                    .padding(horizontal = 48.dp)
                    .size(width = 110.dp, height = 110.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (isLeft) BgmIcons.FastRewind else BgmIcons.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_seek_seconds_format, state.seconds),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

private const val DOUBLE_TAP_WINDOW_MS = 800L
private const val DOUBLE_TAP_COMMIT_DELAY_MS = 650L
