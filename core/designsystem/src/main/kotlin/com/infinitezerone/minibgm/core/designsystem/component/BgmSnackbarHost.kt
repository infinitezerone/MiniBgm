package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.BgmElevation
import com.infinitezerone.minibgm.core.designsystem.theme.BgmGlassLevel
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.designsystem.theme.LocalAppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo

/**
 * 悬浮底栏避让间距与规范常量：
 * 手机端居中悬浮胶囊底栏自身高度 ~52dp + 底部下沉外边距 12dp = 64dp。
 * 加上 8dp 安全呼吸间距，合计 72dp，确保顶层页面 Snackbar 无论在手势条还是三大金刚键下
 * 均优雅悬浮在底栏正上方，严禁产生任何物理重叠遮挡。
 */
object BgmSnackbarDefaults {
    /** 顶层页面在手机紧凑排版下避让悬浮胶囊底栏的推荐间距 */
    val FloatingBarClearance: Dp = BgmSpacing.Snackbar.floatingBarClearance

    /** 二级页面或大屏模式下，避让系统手势小白条/导航栏后的推荐默认底部呼吸间距 */
    val DefaultBottomMargin: Dp = BgmSpacing.Snackbar.bottomMargin

    /**
     * 计算特定排版下的 Snackbar 底部安全避让间距
     */
    fun calculateBottomPadding(
        isTopLevel: Boolean,
        isWideScreen: Boolean,
        bottomOffset: Dp = 0.dp,
    ): Dp =
        when {
            isTopLevel && !isWideScreen -> FloatingBarClearance + bottomOffset
            else -> DefaultBottomMargin + bottomOffset
        }
}

/**
 * MiniBgm 统一自适应 [SnackbarHost]：
 *
 * 1. 顶层目的地（isTopLevel = true 且处于手机紧凑排版时）：
 *    自动增加 [BgmSnackbarDefaults.FloatingBarClearance] (72dp) 底部安全避让间距，
 *    使打卡、收藏修改、网络恢复等各类提示浮动于悬浮底栏上方，绝不遮挡底栏与触控区域；
 * 2. 二级详情页或平板宽屏模式（isTopLevel = false 或 isWideScreen）：
 *    底栏下沉隐藏或侧置为 NavigationRail，保留 [BgmSnackbarDefaults.DefaultBottomMargin] (12dp) 默认呼吸间距，
 *    防止贴合系统手势条导致误触，且支持传入 [bottomOffset] 微调；
 * 3. 横屏场景适配：
 *    通过 [WindowInsetsSides.Horizontal] 自动内缩左右导航栏安全区，避免横屏时系统侧边按键遮挡 Snackbar。
 */
@Composable
fun BgmSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    isTopLevel: Boolean = false,
    bottomOffset: Dp = 0.dp,
    snackbar: @Composable (SnackbarData) -> Unit = { snackbarData -> BgmSnackbar(snackbarData) },
) {
    val isWideScreen = LocalWindowAdaptiveInfo.current.isWide
    val bottomPadding =
        BgmSnackbarDefaults.calculateBottomPadding(
            isTopLevel = isTopLevel,
            isWideScreen = isWideScreen,
            bottomOffset = bottomOffset,
        )

    SnackbarHost(
        hostState = hostState,
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = bottomPadding),
        snackbar = snackbar,
    )
}

@Composable
fun BgmSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val uiStyle = LocalAppUiStyle.current
    val shape = MaterialTheme.shapes.large

    if (uiStyle == AppUiStyle.Luminous) {
        BgmGlassSurface(
            modifier = modifier.padding(horizontal = 16.dp),
            shape = shape,
            level = BgmGlassLevel.Prominent,
            shadowElevation = BgmElevation.floatingChrome,
        ) {
            Snackbar(
                snackbarData = snackbarData,
                shape = RectangleShape,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary,
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Surface(
            modifier = modifier.padding(horizontal = 16.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = BgmElevation.floatingChrome,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Snackbar(
                snackbarData = snackbarData,
                shape = RectangleShape,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary,
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
