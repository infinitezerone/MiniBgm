package com.infinitezerone.minibgm.feature.subject.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

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
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onSeekConfirm: (Long) -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    onHudStateChange: (GestureHudState) -> Unit,
    onTriggerHudDismiss: () -> Unit,
    isLocked: Boolean = false,
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
        )

private data class PlayerGesturesElement(
    val context: Context,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val totalDurationMs: Long,
    val onSingleTap: () -> Unit,
    val onDoubleTapSeek: (Long) -> Unit,
    val onDoubleTapPlayPause: () -> Unit,
    val onSeekConfirm: (Long) -> Unit,
    val onFastForwardStart: () -> Unit,
    val onFastForwardEnd: () -> Unit,
    val onHudStateChange: (GestureHudState) -> Unit,
    val onTriggerHudDismiss: () -> Unit,
    val isLocked: Boolean = false,
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
    var currentPositionMs: Long,
    var totalDurationMs: Long,
    var onSingleTap: () -> Unit,
    var onDoubleTapSeek: (Long) -> Unit,
    var onDoubleTapPlayPause: () -> Unit,
    var onSeekConfirm: (Long) -> Unit,
    var onFastForwardStart: () -> Unit,
    var onFastForwardEnd: () -> Unit,
    var onHudStateChange: (GestureHudState) -> Unit,
    var onTriggerHudDismiss: () -> Unit,
    var isLocked: Boolean = false,
) : DelegatingNode() {
    private var isFastForwarding = false
    private var dragMode = DragMode.NONE
    private var dragAccumulatedX = 0f
    private var dragAccumulatedY = 0f
    private var seekTargetMs = 0L
    private var initialBrightness = 0.5f
    private var initialVolume = 0f

    fun update(
        context: Context,
        isPlaying: Boolean,
        currentPositionMs: Long,
        totalDurationMs: Long,
        onSingleTap: () -> Unit,
        onDoubleTapSeek: (Long) -> Unit,
        onDoubleTapPlayPause: () -> Unit,
        onSeekConfirm: (Long) -> Unit,
        onFastForwardStart: () -> Unit,
        onFastForwardEnd: () -> Unit,
        onHudStateChange: (GestureHudState) -> Unit,
        onTriggerHudDismiss: () -> Unit,
        isLocked: Boolean = false,
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
    }

    @Suppress("unused")
    private val tapNode =
        delegate(
            SuspendingPointerInputModifierNode {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        val width = size.width
                        val x = offset.x
                        val cur = currentPositionMs
                        val total = totalDurationMs
                        when {
                            x < width * 0.35f -> {
                                val target = (cur - 10000L).coerceAtLeast(0L)
                                onDoubleTapSeek(target)
                                onHudStateChange(GestureHudState.Seek(target, total, -10000L))
                                onTriggerHudDismiss()
                            }
                            x > width * 0.65f -> {
                                val maxPos = if (total > 0L) total else Long.MAX_VALUE
                                val target = (cur + 10000L).coerceAtMost(maxPos)
                                onDoubleTapSeek(target)
                                onHudStateChange(GestureHudState.Seek(target, total, 10000L))
                                onTriggerHudDismiss()
                            }
                            else -> {
                                onDoubleTapPlayPause()
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked && isPlaying) {
                            isFastForwarding = true
                            onFastForwardStart()
                            onHudStateChange(GestureHudState.FastForward(2.0f))
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isFastForwarding) {
                            isFastForwarding = false
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

                detectDragGestures(
                    onDragStart = { offset ->
                        if (isLocked) {
                            dragMode = DragMode.NONE
                            return@detectDragGestures
                        }
                        startOffset = offset
                        dragMode = DragMode.NONE
                        dragAccumulatedX = 0f
                        dragAccumulatedY = 0f
                        seekTargetMs = currentPositionMs

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
                        val cur = currentPositionMs
                        val total = totalDurationMs

                        if (dragMode == DragMode.NONE) {
                            if (abs(dragAccumulatedX) > abs(dragAccumulatedY) && abs(dragAccumulatedX) > thresholdPx) {
                                dragMode = DragMode.HORIZONTAL_SEEK
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
                                    val deltaFraction = dragAccumulatedX / width
                                    val deltaMs = (deltaFraction * 90000L).toLong()
                                    seekTargetMs = (cur + deltaMs).coerceIn(0L, total)
                                    onHudStateChange(GestureHudState.Seek(seekTargetMs, total, seekTargetMs - cur))
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
                        val total = totalDurationMs
                        if (dragMode == DragMode.HORIZONTAL_SEEK && total > 0L) {
                            onSeekConfirm(seekTargetMs)
                        }
                        dragMode = DragMode.NONE
                        onTriggerHudDismiss()
                    },
                    onDragCancel = {
                        if (isLocked) return@detectDragGestures
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
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onSeekConfirm: (Long) -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var hudState by remember { mutableStateOf<GestureHudState>(GestureHudState.Idle) }
    var hideHudTrigger by remember { mutableIntStateOf(0) }

    // 手势结束后自动淡出 HUD
    LaunchedEffect(hideHudTrigger) {
        if (hideHudTrigger > 0) {
            delay(800)
            if (hudState !is GestureHudState.FastForward) {
                hudState = GestureHudState.Idle
            }
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
                ),
    ) {
        content()

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
                        SideCapsuleIndicator(
                            icon = Icons.Filled.BrightnessLow,
                            percent = state.percent,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                        )
                    }
                    is GestureHudState.Volume -> {
                        SideCapsuleIndicator(
                            icon = if (state.percent == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                            percent = state.percent,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
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
 * 屏幕边缘竖向微胶囊指示器（对标 iOS/B站侧边条，不遮挡画面正中视线）
 */
@Composable
private fun SideCapsuleIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            // 垂直胶囊填充轨
            Box(
                modifier =
                    Modifier
                        .width(5.dp)
                        .height(84.dp)
                        .background(Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(2.5.dp)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((percent / 100f).coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(2.5.dp)),
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
                    imageVector = if (deltaMs >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind,
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
        shape = RoundedCornerShape(20.dp),
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
                imageVector = Icons.Filled.FastForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${speed}x 倍速快进中",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
