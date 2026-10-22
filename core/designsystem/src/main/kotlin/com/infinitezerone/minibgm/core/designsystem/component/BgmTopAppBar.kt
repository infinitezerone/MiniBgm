package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.designsystem.theme.LocalAppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalLuminousTokens

object BgmTopAppBarDefaults {
    /** 沉浸模式默认颜色：透明容器，高对比前景。 */
    @Composable
    fun immersiveColors(): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    /** 实体结构模式默认颜色：坚实不透明容器。 */
    @Composable
    fun solidColors(containerColor: Color = MaterialTheme.colorScheme.surface): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}

/**
 * MiniBgm 紧凑型现代顶栏：
 * 相比 Material 3 默认笨重庞大的 64dp 顶栏，采用 48dp 紧凑高度设计，
 * 大幅减少上下留白，完美居中 48dp 标准点击区域的图标按钮与精致加粗标题，
 * 最大程度释放手机纵向首屏的黄金浏览空间。
 *
 * 支持双模式：
 * - [BgmTopBarMode.Immersive]：顶栏全透明，无底边分隔线，内容可自由穿入上方的渐隐天幕；
 * - [BgmTopBarMode.Solid]：顶栏填充坚实底色并覆盖状态栏，底边绘制微光 hairline 分割线。
 *
 * 传入 [scrollBehavior] 时支持滚动收合（如 enterAlways）：内容区高度随滚动从 [height]
 * 收合至 0（仅保留状态栏内边距），调用方需自行挂接 `scrollBehavior.nestedScrollConnection`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgmTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    mode: BgmTopBarMode = BgmTopBarMode.Immersive,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    height: Dp = BgmSpacing.TopBar.height,
    contentMaxWidth: Dp? = null,
) {
    val density = LocalDensity.current
    val contentHeightPx = with(density) { height.toPx() }
    val uiStyle = LocalAppUiStyle.current
    val tokens = LocalLuminousTokens.current

    val resolvedColors =
        colors ?: when (mode) {
            BgmTopBarMode.Immersive -> BgmTopAppBarDefaults.immersiveColors()
            BgmTopBarMode.Solid -> {
                val solidBg =
                    if (uiStyle == AppUiStyle.Luminous) {
                        tokens.sheetContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                BgmTopAppBarDefaults.solidColors(containerColor = solidBg)
            }
        }

    // 收合上限以自身实际高度为准（覆盖 M3 默认的 56dp 假设）
    if (scrollBehavior != null) {
        SideEffect { scrollBehavior.state.heightOffsetLimit = -contentHeightPx }
    }

    val contentHeight =
        if (scrollBehavior != null) {
            with(density) { (contentHeightPx + scrollBehavior.state.heightOffset).coerceAtLeast(0f).toDp() }
        } else {
            height
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(resolvedColors.containerColor)
                .pointerInput(Unit) {},
        propagateMinConstraints = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Row(
                    modifier =
                        Modifier
                            .then(
                                if (contentMaxWidth != null) {
                                    Modifier.widthIn(max = contentMaxWidth)
                                } else {
                                    Modifier
                                },
                            ).fillMaxWidth()
                            .windowInsetsPadding(windowInsets)
                            .height(contentHeight)
                            .padding(horizontal = BgmSpacing.TopBar.horizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides resolvedColors.navigationIconContentColor,
                        content = navigationIcon,
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = BgmSpacing.TopBar.titleHorizontal),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        ProvideTextStyle(
                            value =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = resolvedColors.titleContentColor,
                                ),
                        ) {
                            title()
                        }
                    }
                    CompositionLocalProvider(
                        LocalContentColor provides resolvedColors.actionIconContentColor,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            content = actions,
                        )
                    }
                }
            }

            // 实体模式下在底边绘制精致的 hairline 分割线
            if (mode == BgmTopBarMode.Solid) {
                val hairlineBrush =
                    if (uiStyle == AppUiStyle.Luminous) {
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    tokens.topBarHairline,
                                    Color.Transparent,
                                ),
                        )
                    } else {
                        SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(BgmSpacing.TopBar.hairline)
                            .background(hairlineBrush),
                )
            }
        }
    }
}
