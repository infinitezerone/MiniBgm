package com.infinitezerone.minibgm.feature.subject.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    var hudState by remember { mutableStateOf<GestureHudState>(GestureHudState.Idle) }
    var hideHudTrigger by remember { mutableStateOf(0) }

    // 拖拽手势临时态
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragAccumulatedX by remember { mutableFloatStateOf(0f) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }

    // 音量与亮度临时态
    var initialBrightness by remember { mutableFloatStateOf(0.5f) }
    var currentBrightness by remember { mutableFloatStateOf(0.5f) }
    var initialVolume by remember { mutableFloatStateOf(0f) }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    val currentPositionState by rememberUpdatedState(currentPositionMs)
    val totalDurationState by rememberUpdatedState(totalDurationMs)
    val isPlayingState by rememberUpdatedState(isPlaying)
    val onSingleTapState by rememberUpdatedState(onSingleTap)
    val onDoubleTapSeekState by rememberUpdatedState(onDoubleTapSeek)
    val onDoubleTapPlayPauseState by rememberUpdatedState(onDoubleTapPlayPause)
    val onSeekConfirmState by rememberUpdatedState(onSeekConfirm)
    val onFastForwardStartState by rememberUpdatedState(onFastForwardStart)
    val onFastForwardEndState by rememberUpdatedState(onFastForwardEnd)

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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onSingleTapState() },
                        onDoubleTap = { offset ->
                            val width = size.width
                            val x = offset.x
                            val cur = currentPositionState
                            val total = totalDurationState
                            when {
                                x < width * 0.35f -> {
                                    val target = (cur - 10000L).coerceAtLeast(0L)
                                    onDoubleTapSeekState(target)
                                    hudState = GestureHudState.Seek(target, total, -10000L)
                                    hideHudTrigger++
                                }
                                x > width * 0.65f -> {
                                    val maxPos = if (total > 0L) total else Long.MAX_VALUE
                                    val target = (cur + 10000L).coerceAtMost(maxPos)
                                    onDoubleTapSeekState(target)
                                    hudState = GestureHudState.Seek(target, total, 10000L)
                                    hideHudTrigger++
                                }
                                else -> {
                                    onDoubleTapPlayPauseState()
                                }
                            }
                        },
                        onLongPress = {
                            if (isPlayingState) {
                                onFastForwardStartState()
                                hudState = GestureHudState.FastForward(2.0f)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (hudState is GestureHudState.FastForward) {
                                onFastForwardEndState()
                                hudState = GestureHudState.Idle
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    var startOffset = Offset.Zero
                    val thresholdPx = with(density) { 14.dp.toPx() }

                    detectDragGestures(
                        onDragStart = { offset ->
                            startOffset = offset
                            dragMode = DragMode.NONE
                            dragAccumulatedX = 0f
                            dragAccumulatedY = 0f
                            seekTargetMs = currentPositionState

                            // 记录起始亮度
                            val windowLp = activity?.window?.attributes
                            initialBrightness = windowLp?.screenBrightness?.takeIf { it in 0.01f..1f } ?: 0.5f
                            currentBrightness = initialBrightness

                            // 记录起始音量
                            val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            initialVolume = currentVol.toFloat()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulatedX += dragAmount.x
                            dragAccumulatedY += dragAmount.y

                            val width = size.width
                            val height = size.height
                            val cur = currentPositionState
                            val total = totalDurationState

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
                                        // 全屏横移一次约对应 90 秒快进/退
                                        val deltaFraction = dragAccumulatedX / width
                                        val deltaMs = (deltaFraction * 90000L).toLong()
                                        seekTargetMs = (cur + deltaMs).coerceIn(0L, total)
                                        hudState = GestureHudState.Seek(seekTargetMs, total, seekTargetMs - cur)
                                    }
                                }
                                DragMode.VERTICAL_BRIGHTNESS -> {
                                    val delta = -dragAccumulatedY / height
                                    val newBrightness = (initialBrightness + delta).coerceIn(0.01f, 1f)
                                    currentBrightness = newBrightness
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = newBrightness
                                        act.window.attributes = lp
                                    }
                                    hudState = GestureHudState.Brightness((newBrightness * 100).roundToInt())
                                }
                                DragMode.VERTICAL_VOLUME -> {
                                    val delta = -dragAccumulatedY / height
                                    val targetVol = (initialVolume + delta * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    val percent = if (maxVolume > 0) ((targetVol.toFloat() / maxVolume) * 100).roundToInt() else 0
                                    hudState = GestureHudState.Volume(percent)
                                }
                                DragMode.NONE -> {}
                            }
                        },
                        onDragEnd = {
                            val total = totalDurationState
                            if (dragMode == DragMode.HORIZONTAL_SEEK && total > 0L) {
                                onSeekConfirmState(seekTargetMs)
                            }
                            dragMode = DragMode.NONE
                            hideHudTrigger++
                        },
                        onDragCancel = {
                            dragMode = DragMode.NONE
                            hideHudTrigger++
                        },
                    )
                },
    ) {
        content()

        // 浮动 HUD 指示卡片
        AnimatedVisibility(
            visible = hudState !is GestureHudState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            when (val state = hudState) {
                is GestureHudState.Brightness -> {
                    GestureFeedbackCard(
                        icon = Icons.Filled.BrightnessLow,
                        label = "亮度",
                        percent = state.percent,
                    )
                }
                is GestureHudState.Volume -> {
                    GestureFeedbackCard(
                        icon = if (state.percent == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        label = "音量",
                        percent = state.percent,
                    )
                }
                is GestureHudState.Seek -> {
                    SeekFeedbackCard(
                        targetMs = state.targetMs,
                        totalMs = state.totalMs,
                        deltaMs = state.deltaMs,
                    )
                }
                is GestureHudState.FastForward -> {
                    FastForwardChip(speed = state.speed)
                }
                is GestureHudState.Idle -> {}
            }
        }
    }
}

/**
 * 音量 / 亮度 HUD 卡片
 */
@Composable
private fun GestureFeedbackCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White,
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$label $percent%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.width(100.dp).height(4.dp),
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
        color = Color.Black.copy(alpha = 0.8f),
        contentColor = Color.White,
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = if (deltaMs >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${formatDuration(targetMs)} / ${formatDuration(totalMs)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            val deltaSec = (abs(deltaMs) / 1000).toInt()
            Text(
                text = "${if (deltaMs >= 0) "+" else "-"}${deltaSec}s",
                style = MaterialTheme.typography.bodySmall,
                color = if (deltaMs >= 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * 长按倍速微胶囊指示器
 */
@Composable
private fun FastForwardChip(
    speed: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.8f),
        contentColor = Color.White,
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FastForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${speed}x 倍速播放中",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
