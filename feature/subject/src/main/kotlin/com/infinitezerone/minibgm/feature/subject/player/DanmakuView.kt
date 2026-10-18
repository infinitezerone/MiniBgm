package com.infinitezerone.minibgm.feature.subject.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMode
import kotlin.math.abs

private const val DANMAKU_DURATION_MS = 8_500L
private const val FIXED_DURATION_MS = 4_000L

/**
 * 二分查找当前活跃时间窗口的弹幕，避免每帧全量线性扫描与堆分配。
 */
internal fun findActiveDanmakus(
    danmakus: List<DanmakuItem>,
    currentMs: Long,
): List<DanmakuItem> {
    if (danmakus.isEmpty() || currentMs < 0) return emptyList()
    val minTime = currentMs - DANMAKU_DURATION_MS
    var low = 0
    var high = danmakus.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (danmakus[mid].timeMs < minTime) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    val startIndex = low
    if (startIndex >= danmakus.size) return emptyList()

    val result = ArrayList<DanmakuItem>(16)
    for (i in startIndex until danmakus.size) {
        val item = danmakus[i]
        if (item.timeMs > currentMs) break
        val elapsed = currentMs - item.timeMs
        val isActive =
            when (item.mode) {
                DanmakuMode.SCROLL -> elapsed in 0..DANMAKU_DURATION_MS
                DanmakuMode.TOP, DanmakuMode.BOTTOM -> elapsed in 0..FIXED_DURATION_MS
            }
        if (isActive) {
            result.add(item)
        }
    }
    return result
}

/**
 * 高性能流畅弹幕渲染层。
 *
 * 遵循 Fail-Open 架构原则：
 * 任何绘制测量异常自动捕获降级，绝不崩溃播放器宿主。
 * 纯 Canvas 原生文字双层绘制（外层黑色描边 + 内层主题色填充），保障各种画面明暗对比度下的极致清晰度。
 * 针对 ExoPlayer 500ms 轮询心跳进行平滑插值，消除 2 FPS 卡顿；二分查找时间窗口避免每帧 GC。
 */
@Composable
fun DanmakuView(
    danmakus: List<DanmakuItem>,
    currentPositionMs: Long,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    playbackSpeed: Float = 1f,
    timeOffsetMs: Long = 0L,
) {
    if (!isEnabled || danmakus.isEmpty() || currentPositionMs < 0) return

    val effectivePositionMs = (currentPositionMs + timeOffsetMs).coerceAtLeast(0L)

    val density = LocalDensity.current
    val fontSizePx = with(density) { 15.sp.toPx() }
    val trackHeightPx = with(density) { 26.dp.toPx() }
    val strokeWidthPx = with(density) { 3.dp.toPx() }

    val fillPaint =
        remember(fontSizePx) {
            Paint().apply {
                isAntiAlias = true
                textSize = fontSizePx
                typeface = Typeface.DEFAULT_BOLD
                style = Paint.Style.FILL
            }
        }

    val strokePaint =
        remember(fontSizePx, strokeWidthPx) {
            Paint().apply {
                isAntiAlias = true
                textSize = fontSizePx
                typeface = Typeface.DEFAULT_BOLD
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = android.graphics.Color.BLACK
            }
        }

    val sortedDanmakus =
        remember(danmakus) {
            if (danmakus.size <= 1) {
                danmakus
            } else {
                var isSorted = true
                for (i in 0 until danmakus.size - 1) {
                    if (danmakus[i].timeMs > danmakus[i + 1].timeMs) {
                        isSorted = false
                        break
                    }
                }
                if (isSorted) danmakus else danmakus.sortedBy { it.timeMs }
            }
        }

    val animPosition = remember { Animatable(effectivePositionMs.toFloat()) }

    LaunchedEffect(effectivePositionMs, isPlaying, playbackSpeed) {
        if (!isPlaying) {
            animPosition.snapTo(effectivePositionMs.toFloat())
            return@LaunchedEffect
        }
        val currentAnimVal = animPosition.value
        val jump = abs(effectivePositionMs - currentAnimVal)
        if (jump > 1_500f) {
            animPosition.snapTo(effectivePositionMs.toFloat())
        }
        val targetMs = effectivePositionMs + (500L * playbackSpeed)
        animPosition.animateTo(
            targetValue = targetMs,
            animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        )
    }

    val renderedPositionMs = animPosition.value.toLong()

    Canvas(modifier = modifier.fillMaxSize()) {
        runCatching {
            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0 || canvasHeight <= 0) return@runCatching

            val maxTracks = ((canvasHeight * 0.6f) / trackHeightPx).toInt().coerceAtLeast(1)
            val nativeCanvas = drawContext.canvas.nativeCanvas

            val visibleItems = findActiveDanmakus(sortedDanmakus, renderedPositionMs)

            for (item in visibleItems) {
                val text = item.text
                val textWidth = fillPaint.measureText(text)
                val trackIndex = abs(item.text.hashCode()) % maxTracks
                val elapsed = renderedPositionMs - item.timeMs

                val (x, y) =
                    when (item.mode) {
                        DanmakuMode.SCROLL -> {
                            val progress = elapsed.toFloat() / DANMAKU_DURATION_MS
                            val posX = canvasWidth - progress * (canvasWidth + textWidth)
                            val posY = (trackIndex + 1) * trackHeightPx
                            posX to posY
                        }
                        DanmakuMode.TOP -> {
                            val posX = (canvasWidth - textWidth) / 2f
                            val topTrack = trackIndex % 3
                            val posY = (topTrack + 1) * trackHeightPx
                            posX to posY
                        }
                        DanmakuMode.BOTTOM -> {
                            val posX = (canvasWidth - textWidth) / 2f
                            val bottomTrack = trackIndex % 3
                            val posY = canvasHeight - ((bottomTrack + 1) * trackHeightPx)
                            posX to posY
                        }
                    }

                // 先绘制黑色描边外廓
                nativeCanvas.drawText(text, x, y, strokePaint)

                // 再绘制高对比度字色
                val colorInt =
                    if (item.color != 0) {
                        item.color or (0xFF shl 24)
                    } else {
                        android.graphics.Color.WHITE
                    }
                fillPaint.color = colorInt
                nativeCanvas.drawText(text, x, y, fillPaint)
            }
        }
    }
}
