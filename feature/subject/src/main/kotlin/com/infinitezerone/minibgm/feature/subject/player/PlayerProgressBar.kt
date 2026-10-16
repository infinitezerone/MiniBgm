package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/**
 * 具有微交互自适应形态的精美双层播放器进度条
 *
 * 常态：3dp 细线轨道，轻量通透，最小化遮挡视频画面
 * 交互态（按下/拖拽中）：平滑动画加粗至 6dp，Thumb 弹性放大至 14dp 高亮圆球
 * 具备 44dp 无障碍推荐触摸热区，内部轨道垂直居中
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

    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubbing by rememberUpdatedState(onScrubbing)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val fraction = calculateProgressBarFraction(down.position.x, width)
                        currentOnScrubStart()
                        currentOnScrubbing(fraction)

                        var currentFraction = fraction
                        val dragSuccess =
                            drag(down.id) { change ->
                                if (change.positionChange() != Offset.Zero) {
                                    change.consume()
                                    currentFraction = calculateProgressBarFraction(change.position.x, width)
                                    currentOnScrubbing(currentFraction)
                                }
                            }
                        currentOnScrubEnd(currentFraction)
                    }
                },
    ) {
        val width = size.width
        val height = size.height
        // 进度线垂直居中于绘制区域，确保单行内嵌或双行布局均能完美居中对齐图标
        val centerY = height / 2f
        val currentTrackHeight = trackHeight.toPx()
        val currentThumbRadius = thumbRadius.toPx()

        // 几何行程：Thumb 圆心在两端分别为 currentThumbRadius 和 width - currentThumbRadius，
        // 确保 Progress 为 0% 时圆心正好在 currentThumbRadius，圆的左外切正好贴合轨道最左端 0f；
        // Progress 为 100% 时圆心在 width - currentThumbRadius，圆的右外切正好贴合轨道最右端 width。
        val thumbTravel = (width - 2f * currentThumbRadius).coerceAtLeast(0f)
        val clampedProgress = progress.coerceIn(0f, 1f)
        val clampedThumbX = currentThumbRadius + clampedProgress * thumbTravel

        // 1. 底轨 (Background Track)
        drawLine(
            color = trackColor,
            start = Offset(currentThumbRadius, centerY),
            end = Offset(width - currentThumbRadius, centerY),
            strokeWidth = currentTrackHeight,
            cap = StrokeCap.Round,
        )

        // 2. 缓冲轨 (Buffered Track)
        if (bufferedProgress > 0f) {
            val clampedBuffered = bufferedProgress.coerceIn(0f, 1f)
            val bufferedEndX = currentThumbRadius + clampedBuffered * thumbTravel
            drawLine(
                color = bufferedColor,
                start = Offset(currentThumbRadius, centerY),
                end = Offset(bufferedEndX, centerY),
                strokeWidth = currentTrackHeight,
                cap = StrokeCap.Round,
            )
        }

        // 3. 播放进度主轨 (Active Progress Track)
        if (clampedThumbX > currentThumbRadius) {
            drawLine(
                color = activeColor,
                start = Offset(currentThumbRadius, centerY),
                end = Offset(clampedThumbX, centerY),
                strokeWidth = currentTrackHeight,
                cap = StrokeCap.Round,
            )
        }

        // 4. Thumb 外层微光晕 (Glow)
        if (glowAlpha > 0f) {
            drawCircle(
                color = activeColor.copy(alpha = glowAlpha),
                radius = currentThumbRadius + 4.dp.toPx(),
                center = Offset(clampedThumbX, centerY),
            )
        }

        // 5. Thumb 核心圆点 (高亮白芯)
        drawCircle(
            color = Color.White,
            radius = currentThumbRadius,
            center = Offset(clampedThumbX, centerY),
        )
    }
}

/**
 * 屏幕/触摸 X 坐标映射为 [0f, 1f] 进度比例纯函数
 */
internal fun calculateProgressBarFraction(
    touchX: Float,
    totalWidth: Float,
): Float {
    if (totalWidth <= 0f) return 0f
    return (touchX / totalWidth).coerceIn(0f, 1f)
}

/**
 * 沉浸播放态常驻底边极简进度线（类似 Bilibili / YouTube）
 * 在控制栏收起隐藏且正常播放时常驻在视频最底边
 */
@Composable
internal fun PlayerBottomEdgeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    bufferedColor: Color = Color.White.copy(alpha = 0.35f),
    trackColor: Color = Color.White.copy(alpha = 0.25f),
) {
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(2.5.dp),
    ) {
        val width = size.width
        val currentProgress = progress.coerceIn(0f, 1f)
        val currentBuffered = bufferedProgress.coerceIn(0f, 1f)

        // 1. 底轨
        drawLine(
            color = trackColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Butt,
        )

        // 2. 缓冲轨
        if (currentBuffered > 0f) {
            drawLine(
                color = bufferedColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(width * currentBuffered, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Butt,
            )
        }

        // 3. 激活进度轨
        if (currentProgress > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(width * currentProgress, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Butt,
            )
        }
    }
}
