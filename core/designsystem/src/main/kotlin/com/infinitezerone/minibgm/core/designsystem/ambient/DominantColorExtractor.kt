package com.infinitezerone.minibgm.core.designsystem.ambient

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 光晕主色的亮度下限：低于此亮度的颜色会被线性抬升，避免在深色模式下把背景压成死黑 */
const val AMBIENT_MIN_LUMINANCE = 0.16f

/** 光晕主色的亮度上限：高于此亮度的颜色会被线性压暗，避免在浅色模式下冲掉前景文字对比度 */
const val AMBIENT_MAX_LUMINANCE = 0.86f

/** 半透明以下（alpha 不足）的像素不参与统计 */
private const val ALPHA_THRESHOLD = 128

/** 每通道量化位数：4 位/通道 → 16^3 = 4096 个色桶，兼顾精度与遍历开销 */
private const val QUANTIZATION_BITS = 4

/**
 * 封面主色提取（纯 Kotlin，不依赖 android 与 Coil，便于 JVM 单测）。
 *
 * 算法：对降采样后的 ARGB 像素按每通道高 4 位量化分桶，统计各桶均值；
 * 在亮度落在 [AMBIENT_MIN_LUMINANCE, AMBIENT_MAX_LUMINANCE] 的桶里，
 * 选「像素数 × (0.15 + 饱和度)」得分最高者作为主色（倾向大面积且有辨识度的颜色）；
 * 若所有桶都过亮/过暗，则退回像素数最多的桶，并把亮度夹回可读区间。
 *
 * @return ARGB 主色（alpha 恒为不透明），像素全透明或输入为空时返回 null
 */
fun extractDominantColor(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null

    val shift = 8 - QUANTIZATION_BITS
    val bucketCount = 1 shl (QUANTIZATION_BITS * 3)
    val counts = IntArray(bucketCount)
    val sumReds = IntArray(bucketCount)
    val sumGreens = IntArray(bucketCount)
    val sumBlues = IntArray(bucketCount)

    for (argb in pixels) {
        if (((argb ushr 24) and 0xFF) < ALPHA_THRESHOLD) continue
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        val index =
            ((red shr shift) shl (QUANTIZATION_BITS * 2)) or
                ((green shr shift) shl QUANTIZATION_BITS) or
                (blue shr shift)
        counts[index]++
        sumReds[index] += red
        sumGreens[index] += green
        sumBlues[index] += blue
    }

    var bestReadableIndex = -1
    var bestReadableScore = -1f
    var largestIndex = -1
    var largestCount = 0
    for (index in counts.indices) {
        val count = counts[index]
        if (count == 0) continue
        if (count > largestCount) {
            largestCount = count
            largestIndex = index
        }
        val average = bucketAverageArgb(index, count, sumReds, sumGreens, sumBlues)
        val luminance = argbLuminance(average)
        if (luminance < AMBIENT_MIN_LUMINANCE || luminance > AMBIENT_MAX_LUMINANCE) continue
        // 少量但高饱和的点缀色也要有机会胜出，因此得分叠加饱和度权重
        val score = count * (0.15f + argbSaturation(average))
        if (score > bestReadableScore) {
            bestReadableScore = score
            bestReadableIndex = index
        }
    }

    val winner = if (bestReadableIndex >= 0) bestReadableIndex else largestIndex
    if (winner < 0) return null
    val color = bucketAverageArgb(winner, counts[winner], sumReds, sumGreens, sumBlues)
    return normalizeArgbLuminance((0xFF shl 24) or color)
}

/** ARGB 颜色的相对亮度（Rec. 601 加权，0f..1f，不考虑 alpha） */
fun argbLuminance(argb: Int): Float {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
}

/** ARGB 颜色的 HSV 饱和度（0f..1f，不考虑 alpha） */
fun argbSaturation(argb: Int): Float {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    val maxChannel = max(red, max(green, blue)).toFloat()
    val minChannel = min(red, min(green, blue)).toFloat()
    return if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
}

/**
 * 将 ARGB 颜色的亮度线性夹到 [minLuminance, maxLuminance] 区间。
 * 亮度对 RGB 是线性的，因此与纯白/纯黑插值时系数可解析求解，结果确定可测。
 */
fun normalizeArgbLuminance(
    argb: Int,
    minLuminance: Float = AMBIENT_MIN_LUMINANCE,
    maxLuminance: Float = AMBIENT_MAX_LUMINANCE,
): Int {
    val luminance = argbLuminance(argb)
    val target =
        when {
            luminance < minLuminance -> minLuminance
            luminance > maxLuminance -> maxLuminance
            else -> return argb
        }
    val t =
        if (target > luminance) {
            (target - luminance) / (1f - luminance)
        } else {
            1f - target / luminance
        }
    val red = lerpChannel((argb shr 16) and 0xFF, t, towardWhite = target > luminance)
    val green = lerpChannel((argb shr 8) and 0xFF, t, towardWhite = target > luminance)
    val blue = lerpChannel(argb and 0xFF, t, towardWhite = target > luminance)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun lerpChannel(
    channel: Int,
    t: Float,
    towardWhite: Boolean,
): Int =
    if (towardWhite) {
        (channel + (255 - channel) * t).roundToInt()
    } else {
        (channel * (1f - t)).roundToInt()
    }

private fun bucketAverageArgb(
    index: Int,
    count: Int,
    sumReds: IntArray,
    sumGreens: IntArray,
    sumBlues: IntArray,
): Int {
    val red = sumReds[index] / count
    val green = sumGreens[index] / count
    val blue = sumBlues[index] / count
    return (red shl 16) or (green shl 8) or blue
}
