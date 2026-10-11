package com.infinitezerone.minibgm.feature.subject.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMode
import kotlin.math.abs

private const val DANMAKU_DURATION_MS = 6_000L
private const val FIXED_DURATION_MS = 4_000L

/**
 * 轻量高性能弹幕渲染层。
 *
 * 遵循 Fail-Open 架构原则：
 * 任何绘制测量异常自动捕获降级，绝不崩溃播放器宿主。
 * 纯 Canvas 原生文字双层绘制（外层黑色描边 + 内层主题色填充），保障各种画面明暗对比度下的极致清晰度。
 */
@Composable
fun DanmakuView(
    danmakus: List<DanmakuItem>,
    currentPositionMs: Long,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isEnabled || danmakus.isEmpty() || currentPositionMs < 0) return

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

    Canvas(modifier = modifier.fillMaxSize()) {
        runCatching {
            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0 || canvasHeight <= 0) return@runCatching

            val maxTracks = ((canvasHeight * 0.6f) / trackHeightPx).toInt().coerceAtLeast(1)
            val nativeCanvas = drawContext.canvas.nativeCanvas

            // 过滤当前活跃时间窗口的弹幕
            val visibleItems =
                danmakus.filter { item ->
                    val elapsed = currentPositionMs - item.timeMs
                    when (item.mode) {
                        DanmakuMode.SCROLL -> elapsed in 0..DANMAKU_DURATION_MS
                        DanmakuMode.TOP, DanmakuMode.BOTTOM -> elapsed in 0..FIXED_DURATION_MS
                    }
                }

            for (item in visibleItems) {
                val text = item.text
                val textWidth = fillPaint.measureText(text)
                val trackIndex = abs(item.text.hashCode()) % maxTracks
                val elapsed = currentPositionMs - item.timeMs

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
                        // 保证 Alpha 通道不透明
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
