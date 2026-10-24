package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MiniBgm 间距阶梯。
 *
 * 来源与边界：
 * - 基础阶梯沿用项目既有的 4dp 基线（4/8/12/16/20/24/32），与 Material 3 的
 *   4dp baseline grid 一致，不引入 iOS 的 8pt 网格，也不重排现有页面边距。
 * - [TopBar] / [SideNavigation] / [FloatingNav] / [Snackbar] 只固化跨页面复用的 chrome 尺寸；
 *   页面级 padding 仍由各 feature 持有，后续页面结构重构时可独立演进。
 * - Chrome 尺寸（顶栏/底栏 56dp 高度、底栏 12/4dp 呼吸、Snackbar 76dp 避让、136dp scrim）
 *   是实测后的固定交互尺寸，不参与 4dp 阶梯推导；48dp 触控靶区由组件层
 *   `minimumInteractiveComponentSize` 统一保证，不设间距 token。
 */
object BgmSpacing {
    val none: Dp = 0.dp
    val hairline: Dp = 1.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp

    /**
     * 现代顶栏：56dp 高度，左右 16dp（BgmSpacing.lg）标准内边距，
     * 为 40dp 实体大圆盘操作键（BgmCircleActionButton）保留上下各 8dp 呼吸空间，
     * 同时保证操作键与下方内容卡片网格完美左对齐，远离屏幕边框。
     */
    object TopBar {
        val height: Dp = 56.dp
        val horizontal: Dp = BgmSpacing.lg
        val titleHorizontal: Dp = BgmSpacing.sm
    }

    /** 宽屏侧边导航。只有三个顶层目的地时保持紧凑轨道，不制造空侧栏。 */
    object SideNavigation {
        val railWidth: Dp = 80.dp
    }

    /**
     * 手机端悬浮胶囊底栏：外圈 12/4dp 呼吸，项间距 8dp，
     * 单项最小宽 68dp，标准 56dp 轻巧浮岛，24dp 饱满图标。
     */
    object FloatingNav {
        val height: Dp = 56.dp
        val minWidth: Dp = 220.dp
        val maxWidth: Dp = 380.dp
        val horizontalPadding: Dp = 12.dp
        val verticalPadding: Dp = 4.dp
        val itemGap: Dp = 8.dp
        val itemMinWidth: Dp = 68.dp
        val itemPadding: Dp = BgmSpacing.xxs
        val iconSize: Dp = 24.dp
        val iconHorizontalPadding: Dp = 12.dp
        val iconVerticalPadding: Dp = BgmSpacing.xxs
        val labelGap: Dp = 3.dp

        /** 悬浮底栏背后的底部渐隐高度；由内容滚动到胶囊下方时的可读性决定。 */
        val scrimHeight: Dp = 136.dp
    }

    /** Snackbar 避让：悬浮底栏高度(56dp)与下沉外边距(12dp)之和，再留 8dp 呼吸(共 76dp)。 */
    object Snackbar {
        val floatingBarClearance: Dp = 76.dp
        val bottomMargin: Dp = BgmSpacing.md
    }
}
