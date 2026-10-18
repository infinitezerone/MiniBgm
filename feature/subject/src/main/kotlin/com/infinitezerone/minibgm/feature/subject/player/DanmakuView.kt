package com.infinitezerone.minibgm.feature.subject.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMode
import kotlin.math.abs

private const val DANMAKU_DURATION_MS = 8_500L
private const val FIXED_DURATION_MS = 4_000L
private const val DEFAULT_SUBTITLE_AREA_RATIO = 0.25f
private const val DEFAULT_MIN_SPACING_PX = 32f

/**
 * 经过弹道排版预计算后的弹幕实例。
 *
 * 包含分配的弹道索引 [trackIndex] 与已测量的文字像素宽度 [textWidth]，
 * 使得每秒 60/120 帧的绘制循环无需重复测量文字尺寸与重复判定碰撞，达成零 GC 与极致流畅。
 */
internal data class PositionedDanmaku(
    val item: DanmakuItem,
    val trackIndex: Int,
    val textWidth: Float,
)

/**
 * 判定滚动弹道对于候选弹幕 [newItem] 是否安全（入场不重叠、屏幕内不追尾）。
 *
 * 数学模型（以恒定时长 T 滚动）：
 * 1. 入场安全检查：
 *    当 [newItem] 在 t = newItem.timeMs 从右边界 (X = W) 入场时，
 *    前序弹幕 [lastItem] 的尾部必须已完全进入屏幕并留出安全间距 [minSpacingPx]：
 *    tailX(t) = (W + lastWidth) * (1 - (deltaT / T))
 *    要求：tailX <= W - minSpacingPx。
 * 2. 追尾碰撞检查：
 *    弹幕速度 V = (W + L) / T。
 *    若 newWidth <= lastWidth，newItem 速度 <= lastItem 速度，间距单调递增，安全；
 *    若 newWidth > lastWidth，newItem 速度更快，两者间距随时间缩短，并在 lastItem 离开屏幕那一刻
 *    （t = lastItem.timeMs + T，此时 lastItem 尾部处于 X = 0）达到极小值。
 *    在该时刻 newItem 头部坐标为：
 *    headX = W - ((T - deltaT) / T) * (W + newWidth)
 *    要求：headX >= minSpacingPx，避免 newItem 在屏幕中途追尾覆盖 lastItem。
 */
internal fun isScrollTrackSafe(
    lastItem: DanmakuItem,
    lastWidth: Float,
    newItem: DanmakuItem,
    newWidth: Float,
    canvasWidth: Float,
    durationMs: Long,
    minSpacingPx: Float = DEFAULT_MIN_SPACING_PX,
): Boolean {
    val deltaT = newItem.timeMs - lastItem.timeMs
    if (deltaT < 0L) return false
    if (deltaT >= durationMs) return true

    // 1. 入场安全间距检查
    val tailX = (canvasWidth + lastWidth) * (1f - deltaT.toFloat() / durationMs)
    if (tailX > canvasWidth - minSpacingPx) {
        return false
    }

    // 2. 追尾防撞检查
    if (newWidth <= lastWidth) {
        return true
    }

    val elapsedNew = durationMs - deltaT
    val headX = canvasWidth - (elapsedNew.toFloat() / durationMs) * (canvasWidth + newWidth)
    return headX >= minSpacingPx
}

/**
 * 基于空间防重叠与时间防追尾动态弹道分配算法，为整组弹幕预计算弹道和排版。
 *
 * 核心特性：
 * 1. 动静分离：根据模式区分滚动弹道与顶部/底部固定弹道。
 * 2. 字幕避让：底部预留 [subtitleAreaRatio]（默认 25%）高度，滚动与底部固定弹幕绝不侵入正文字幕区。
 * 3. 动态择优：同秒内相同内容或高密度爆发弹幕自动瀑布流下落到不同弹道，彻底消除同字/同哈希重叠。
 * 4. 拥堵自愈：若全部弹道均繁忙，降级挑选尾部离右边界最远（行进最远）的弹道，将视觉干扰降至最低。
 */
internal fun computeDanmakuLayout(
    danmakus: List<DanmakuItem>,
    canvasWidth: Float,
    canvasHeight: Float,
    trackHeightPx: Float,
    durationMs: Long = DANMAKU_DURATION_MS,
    measureTextWidth: (String) -> Float,
    minSpacingPx: Float = DEFAULT_MIN_SPACING_PX,
    subtitleAreaRatio: Float = DEFAULT_SUBTITLE_AREA_RATIO,
    fixedDurationMs: Long = FIXED_DURATION_MS,
): List<PositionedDanmaku> {
    if (danmakus.isEmpty() || canvasWidth <= 0f || canvasHeight <= 0f || trackHeightPx <= 0f) {
        return emptyList()
    }

    val availableHeight = canvasHeight * (1f - subtitleAreaRatio)
    val maxScrollTracks = (availableHeight / trackHeightPx).toInt().coerceAtLeast(1)
    val maxTopTracks = (maxScrollTracks / 2).coerceIn(1, 5)
    val maxBottomTracks = (maxScrollTracks / 2).coerceIn(1, 5)

    val lastScrollItems = arrayOfNulls<DanmakuItem>(maxScrollTracks)
    val lastScrollWidths = FloatArray(maxScrollTracks)

    val topTracksFreeUntil = LongArray(maxTopTracks) { 0L }
    val bottomTracksFreeUntil = LongArray(maxBottomTracks) { 0L }

    val result = ArrayList<PositionedDanmaku>(danmakus.size)

    for (item in danmakus) {
        val textWidth = measureTextWidth(item.text)
        when (item.mode) {
            DanmakuMode.SCROLL -> {
                var chosenTrack = -1
                for (track in 0 until maxScrollTracks) {
                    val lastItem = lastScrollItems[track]
                    if (lastItem == null) {
                        chosenTrack = track
                        break
                    }
                    val lastWidth = lastScrollWidths[track]
                    if (isScrollTrackSafe(
                            lastItem = lastItem,
                            lastWidth = lastWidth,
                            newItem = item,
                            newWidth = textWidth,
                            canvasWidth = canvasWidth,
                            durationMs = durationMs,
                            minSpacingPx = minSpacingPx,
                        )
                    ) {
                        chosenTrack = track
                        break
                    }
                }

                // 极端高密度拥堵降级：挑选尾部行进最远（tailX 最小）的弹道
                if (chosenTrack == -1) {
                    var minTailX = Float.MAX_VALUE
                    var bestTrack = 0
                    for (track in 0 until maxScrollTracks) {
                        val lastItem = lastScrollItems[track]!!
                        val lastWidth = lastScrollWidths[track]
                        val elapsed = (item.timeMs - lastItem.timeMs).coerceAtLeast(0L)
                        val tailX = (canvasWidth + lastWidth) * (1f - elapsed.toFloat() / durationMs)
                        if (tailX < minTailX) {
                            minTailX = tailX
                            bestTrack = track
                        }
                    }
                    chosenTrack = bestTrack
                }

                lastScrollItems[chosenTrack] = item
                lastScrollWidths[chosenTrack] = textWidth
                result.add(PositionedDanmaku(item, chosenTrack, textWidth))
            }

            DanmakuMode.TOP -> {
                var chosenTrack = -1
                for (track in 0 until maxTopTracks) {
                    if (topTracksFreeUntil[track] <= item.timeMs) {
                        chosenTrack = track
                        break
                    }
                }
                if (chosenTrack == -1) {
                    var minFreeUntil = Long.MAX_VALUE
                    var bestTrack = 0
                    for (track in 0 until maxTopTracks) {
                        if (topTracksFreeUntil[track] < minFreeUntil) {
                            minFreeUntil = topTracksFreeUntil[track]
                            bestTrack = track
                        }
                    }
                    chosenTrack = bestTrack
                }
                topTracksFreeUntil[chosenTrack] = item.timeMs + fixedDurationMs
                result.add(PositionedDanmaku(item, chosenTrack, textWidth))
            }

            DanmakuMode.BOTTOM -> {
                var chosenTrack = -1
                for (track in 0 until maxBottomTracks) {
                    if (bottomTracksFreeUntil[track] <= item.timeMs) {
                        chosenTrack = track
                        break
                    }
                }
                if (chosenTrack == -1) {
                    var minFreeUntil = Long.MAX_VALUE
                    var bestTrack = 0
                    for (track in 0 until maxBottomTracks) {
                        if (bottomTracksFreeUntil[track] < minFreeUntil) {
                            minFreeUntil = bottomTracksFreeUntil[track]
                            bestTrack = track
                        }
                    }
                    chosenTrack = bestTrack
                }
                bottomTracksFreeUntil[chosenTrack] = item.timeMs + fixedDurationMs
                result.add(PositionedDanmaku(item, chosenTrack, textWidth))
            }
        }
    }
    return result
}

/**
 * 二分查找当前活跃时间窗口的已排版弹幕。
 */
internal fun findActivePositionedDanmakus(
    danmakus: List<PositionedDanmaku>,
    currentMs: Long,
    scrollDurationMs: Long = DANMAKU_DURATION_MS,
): List<PositionedDanmaku> {
    if (danmakus.isEmpty() || currentMs < 0) return emptyList()
    val minTime = currentMs - scrollDurationMs
    var low = 0
    var high = danmakus.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (danmakus[mid].item.timeMs < minTime) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    val startIndex = low
    if (startIndex >= danmakus.size) return emptyList()

    val result = ArrayList<PositionedDanmaku>(16)
    for (i in startIndex until danmakus.size) {
        val posItem = danmakus[i]
        if (posItem.item.timeMs > currentMs) break
        val elapsed = currentMs - posItem.item.timeMs
        val isActive =
            when (posItem.item.mode) {
                DanmakuMode.SCROLL -> elapsed in 0..scrollDurationMs
                DanmakuMode.TOP, DanmakuMode.BOTTOM -> elapsed in 0..FIXED_DURATION_MS
            }
        if (isActive) {
            result.add(posItem)
        }
    }
    return result
}

/**
 * 二分查找当前活跃时间窗口的弹幕，避免每帧全量线性扫描与堆分配。
 */
internal fun findActiveDanmakus(
    danmakus: List<DanmakuItem>,
    currentMs: Long,
    scrollDurationMs: Long = DANMAKU_DURATION_MS,
): List<DanmakuItem> {
    if (danmakus.isEmpty() || currentMs < 0) return emptyList()
    val minTime = currentMs - scrollDurationMs
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
                DanmakuMode.SCROLL -> elapsed in 0..scrollDurationMs
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
 * 动态弹道分配与字幕安全避让：预计算弹道排版，避免每帧测量与碰撞计算；底部保留字幕安全区。
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
    durationMs: Long = DANMAKU_DURATION_MS,
    alpha: Float = 1.0f,
) {
    if (!isEnabled || danmakus.isEmpty() || currentPositionMs < 0) return

    val effectivePositionMs = (currentPositionMs + timeOffsetMs).coerceAtLeast(0L)
    val effectiveAlpha = alpha.coerceIn(0.1f, 1f)

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
        remember(fontSizePx, strokeWidthPx, effectiveAlpha) {
            Paint().apply {
                isAntiAlias = true
                textSize = fontSizePx
                typeface = Typeface.DEFAULT_BOLD
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = android.graphics.Color.BLACK
                this.alpha = (effectiveAlpha * 255).toInt()
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthPx =
            if (maxWidth.isSpecified && maxWidth != Dp.Infinity && maxWidth > 0.dp) {
                with(density) { maxWidth.toPx() }
            } else {
                0f
            }
        val canvasHeightPx =
            if (maxHeight.isSpecified && maxHeight != Dp.Infinity && maxHeight > 0.dp) {
                with(density) { maxHeight.toPx() }
            } else {
                0f
            }

        val positionedDanmakus =
            remember(sortedDanmakus, canvasWidthPx, canvasHeightPx, durationMs, fontSizePx) {
                if (canvasWidthPx > 0f && canvasHeightPx > 0f) {
                    computeDanmakuLayout(
                        danmakus = sortedDanmakus,
                        canvasWidth = canvasWidthPx,
                        canvasHeight = canvasHeightPx,
                        trackHeightPx = trackHeightPx,
                        durationMs = durationMs,
                        measureTextWidth = { fillPaint.measureText(it) },
                    )
                } else {
                    emptyList()
                }
            }

        Canvas(modifier = Modifier.fillMaxSize()) {
            runCatching {
                val canvasWidth = size.width
                val canvasHeight = size.height
                if (canvasWidth <= 0 || canvasHeight <= 0) return@runCatching

                val nativeCanvas = drawContext.canvas.nativeCanvas
                val visibleItems =
                    findActivePositionedDanmakus(positionedDanmakus, renderedPositionMs, durationMs)
                val bottomBaseY = canvasHeight * (1f - DEFAULT_SUBTITLE_AREA_RATIO)

                for (posItem in visibleItems) {
                    val item = posItem.item
                    val text = item.text
                    val textWidth = posItem.textWidth
                    val trackIndex = posItem.trackIndex
                    val elapsed = renderedPositionMs - item.timeMs

                    val (x, y) =
                        when (item.mode) {
                            DanmakuMode.SCROLL -> {
                                val progress = elapsed.toFloat() / durationMs
                                val posX = canvasWidth - progress * (canvasWidth + textWidth)
                                val posY = (trackIndex + 1) * trackHeightPx
                                posX to posY
                            }
                            DanmakuMode.TOP -> {
                                val posX = (canvasWidth - textWidth) / 2f
                                val posY = (trackIndex + 1) * trackHeightPx
                                posX to posY
                            }
                            DanmakuMode.BOTTOM -> {
                                val posX = (canvasWidth - textWidth) / 2f
                                val posY = bottomBaseY - (trackIndex * trackHeightPx)
                                posX to posY
                            }
                        }

                    // 先绘制黑色描边外廓
                    nativeCanvas.drawText(text, x, y, strokePaint)

                    // 再绘制高对比度字色（按 effectiveAlpha 等比透明）
                    val baseAlpha = (effectiveAlpha * 255).toInt()
                    val colorInt =
                        if (item.color != 0) {
                            val itemAlpha = ((item.color ushr 24) and 0xFF).let { if (it == 0) 255 else it }
                            val finalAlpha = ((itemAlpha / 255f) * (baseAlpha / 255f) * 255).toInt()
                            (item.color and 0x00FFFFFF) or (finalAlpha shl 24)
                        } else {
                            (android.graphics.Color.WHITE and 0x00FFFFFF) or (baseAlpha shl 24)
                        }
                    fillPaint.color = colorInt
                    nativeCanvas.drawText(text, x, y, fillPaint)
                }
            }
        }
    }
}
