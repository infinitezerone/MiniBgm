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
    EXPANDED, // 平板横屏/展开大屏/桌面（宽度 >= 840dp）
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

    /** 是否为扩展宽度模式（宽度 >= 840dp，如平板横屏、展开大屏） */
    val isExpanded: Boolean get() = mode == BgmDisplayMode.EXPANDED

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
    val isExpanded =
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
        )
    val isMedium =
        !isExpanded &&
            adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            )
    val isHeightCompact =
        !adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(
            WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
        )

    val mode =
        when {
            isHeightCompact -> BgmDisplayMode.COMPACT
            isExpanded -> BgmDisplayMode.EXPANDED
            isMedium -> BgmDisplayMode.MEDIUM
            else -> BgmDisplayMode.COMPACT
        }

    CompositionLocalProvider(
        LocalWindowAdaptiveInfo provides BgmWindowAdaptiveInfo(mode),
        content = content,
    )
}
