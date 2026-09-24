package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 具有微交互自适应形态的精美双层播放器进度条
 *
 * 常态：3dp 细线轨道，轻量通透，最小化遮挡视频画面
 * 交互态（按下/拖拽中）：平滑动画加粗至 6dp，Thumb 弹性放大至 14dp 高亮圆球
 */
@Composable
internal fun PlayerProgressBar(
    progress: Float,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubbing: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    bufferedColor: Color = Color.White.copy(alpha = 0.35f),
    trackColor: Color = Color.White.copy(alpha = 0.2f),
) {
    // 弹性交互高度：平时 3dp，拖拽按压时 6dp
    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 3.dp,
        animationSpec = tween(durationMillis = 180),
        label = "trackHeight",
    )

    // Thumb 弹性半径：平时 4dp，拖拽按压时 7dp
    val thumbRadius by animateDpAsState(
        targetValue = if (isScrubbing) 7.dp else 4.dp,
        animationSpec = tween(durationMillis = 180),
        label = "thumbRadius",
    )

    // Thumb 光晕透明度
    val glowAlpha by animateFloatAsState(
        targetValue = if (isScrubbing) 0.35f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "glowAlpha",
    )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrubStart()
                            onScrubbing(fraction)
                            val released = tryAwaitRelease()
                            if (released) {
                                onScrubEnd(fraction)
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrubStart()
                            onScrubbing(fraction)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrubbing(fraction)
                        },
                        onDragEnd = {
                            onScrubEnd(progress)
                        },
                        onDragCancel = {
                            onScrubEnd(progress)
                        },
                    )
                },
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val currentTrackHeight = trackHeight.toPx()
        val currentThumbRadius = thumbRadius.toPx()

        // 1. 底轨 (Background Track)
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = currentTrackHeight,
            cap = StrokeCap.Round,
        )

        // 2. 缓冲轨 (Buffered Track)
        if (bufferedProgress > 0f) {
            val bufferedWidth = (width * bufferedProgress.coerceIn(0f, 1f)).coerceIn(0f, width)
            drawLine(
                color = bufferedColor,
                start = Offset(0f, centerY),
                end = Offset(bufferedWidth, centerY),
                strokeWidth = currentTrackHeight,
                cap = StrokeCap.Round,
            )
        }

        // 3. 播放进度主轨 (Active Progress Track)
        val activeWidth = (width * progress.coerceIn(0f, 1f)).coerceIn(0f, width)
        drawLine(
            color = activeColor,
            start = Offset(0f, centerY),
            end = Offset(activeWidth, centerY),
            strokeWidth = currentTrackHeight,
            cap = StrokeCap.Round,
        )

        // 4. Thumb 外层微光晕 (Glow)
        if (glowAlpha > 0f) {
            drawCircle(
                color = activeColor.copy(alpha = glowAlpha),
                radius = currentThumbRadius + 4.dp.toPx(),
                center = Offset(activeWidth, centerY),
            )
        }

        // 5. Thumb 核心圆点 (高亮白芯)
        drawCircle(
            color = Color.White,
            radius = currentThumbRadius,
            center = Offset(activeWidth, centerY),
        )
    }
}
