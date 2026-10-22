package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MiniBgm 间距阶梯。
 *
 * 来源与边界：
 * - 基础阶梯沿用项目既有的 4dp 基线（4/8/12/16/20/24/32），与 Material 3 的
 *   4dp baseline grid 一致，不引入 iOS 的 8pt 网格，也不重排现有页面边距。
 * - [TopBar] / [FloatingNav] / [Snackbar] 只固化跨页面复用的 chrome 尺寸；
 *   页面级 padding 仍由各 feature 持有，后续页面结构重构时可独立演进。
 * - 6dp、10dp、48dp、64dp、88dp 是实测后的固定交互尺寸（触控靶区、胶囊呼吸、
 *   底栏避让），不参与 4dp 阶梯推导，避免为了“整齐”牺牲可达性。
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
     * 紧凑顶栏：48dp 高度保证图标按钮仍有 48dp 触控区域，
     * 左右 4dp 外缩 + 标题 8dp 内缩用于抵消 IconButton 自带内边距。
     */
    object TopBar {
        val height: Dp = 48.dp
        val horizontal: Dp = BgmSpacing.xs
        val titleHorizontal: Dp = BgmSpacing.sm
        val hairline: Dp = BgmSpacing.hairline
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
