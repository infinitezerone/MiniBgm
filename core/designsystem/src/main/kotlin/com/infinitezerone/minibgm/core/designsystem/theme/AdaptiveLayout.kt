package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.window.core.layout.WindowSizeClass

/**
 * 屏幕自适应显示模式枚举
 */
enum class BgmDisplayMode {
    COMPACT, // 手机竖屏标准紧凑模式（宽度 < 600dp）
    MEDIUM, // 折叠屏展开/小平板竖屏/横屏手机（600dp <= 宽度 < 840dp）
    EXPANDED, // 平板横屏/展开大屏（840dp <= 宽度 < 1200dp）
    LARGE, // 大平板/桌面窗口（1200dp <= 宽度 < 1600dp）
    EXTRA_LARGE, // 桌面/超宽窗口（宽度 >= 1600dp）
}

/**
 * 全局自适应窗口信息对象
 */
data class BgmWindowAdaptiveInfo(
    val mode: BgmDisplayMode = BgmDisplayMode.COMPACT,
) {
    /** 是否为手机竖屏紧凑模式（宽度 < 600dp） */
    val isCompact: Boolean get() = mode == BgmDisplayMode.COMPACT

    /** 是否为中等宽度模式（600dp <= 宽度 < 840dp，如折叠屏展开竖屏、小平板） */
    val isMedium: Boolean get() = mode == BgmDisplayMode.MEDIUM

    /** 是否为扩展宽度模式（840dp <= 宽度 < 1200dp，如平板横屏） */
    val isExpanded: Boolean get() = mode == BgmDisplayMode.EXPANDED

    /** 是否为大宽度模式（1200dp <= 宽度 < 1600dp） */
    val isLarge: Boolean get() = mode == BgmDisplayMode.LARGE

    /** 是否为超大宽度模式（宽度 >= 1600dp） */
    val isExtraLarge: Boolean get() = mode == BgmDisplayMode.EXTRA_LARGE

    /** 是否为 Expanded 及以上宽度 */
    val isExpandedOrWider: Boolean get() = isExpanded || isLarge || isExtraLarge

    /** 是否为宽屏模式（Medium 或 Expanded，即宽度 >= 600dp），可容纳侧边栏或两栏排版 */
    val isWide: Boolean get() = !isCompact
}

/**
 * 全局自适应窗口信息 CompositionLocal。
 * 业务屏幕可直接通过 `LocalWindowAdaptiveInfo.current` 读取标准模式，解耦底层框架与魔法数字。
 */
val LocalWindowAdaptiveInfo = compositionLocalOf { BgmWindowAdaptiveInfo() }

/**
 * 便捷注入当前 WindowAdaptiveInfo 的容器组件。
 */
@Composable
fun ProvideWindowAdaptiveInfo(content: @Composable () -> Unit) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val mode =
        displayModeFor(
            widthDp = adaptiveInfo.windowSizeClass.minWidthDp,
            heightDp = adaptiveInfo.windowSizeClass.minHeightDp,
        )

    CompositionLocalProvider(
        LocalWindowAdaptiveInfo provides BgmWindowAdaptiveInfo(mode),
        content = content,
    )
}

internal fun displayModeFor(
    widthDp: Int,
    heightDp: Int,
): BgmDisplayMode =
    when {
        heightDp < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND -> BgmDisplayMode.COMPACT
        widthDp >= WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND -> BgmDisplayMode.EXTRA_LARGE
        widthDp >= WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND -> BgmDisplayMode.LARGE
        widthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND -> BgmDisplayMode.EXPANDED
        widthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND -> BgmDisplayMode.MEDIUM
        else -> BgmDisplayMode.COMPACT
    }
