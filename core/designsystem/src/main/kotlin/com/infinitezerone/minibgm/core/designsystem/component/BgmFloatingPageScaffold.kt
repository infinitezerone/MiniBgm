package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing

/**
 * 页面级悬浮框架（不是内容卡片 [BgmFloatingFrame]）。
 *
 * 对应 HyperOS 4 / ColorOS 17 系统应用共同采用的“内容全幅铺满、chrome 悬浮在内容之上”：
 * - 背景由 [BgmBackdrop] 铺满整页，内容可自然延伸到状态栏与导航栏下方；
 * - [topBarMode] 控制顶栏是沉浸天幕模式还是实体稳固结构模式；
 * - [topBar] 是唯一的结构性顶栏槽位，默认由调用方传入 [BgmTopAppBar]；
 * - [content] 铺满整页并绘制在 chrome 之下，滚动内容可以自然穿行到顶栏与状态栏后方；
 *   沉浸模式下，首项通过 contentPadding 避让 [BgmFloatingPageScaffoldDefaults.ContentTopClearance]；
 * - [floatingBottomBar] 独立悬浮在内容层上方，并负责导航栏安全区与下沉呼吸距离。
 */
object BgmFloatingPageScaffoldDefaults {
    /** 悬浮 chrome 与内容底部之间的视觉呼吸距离。 */
    val FloatingChromeSink: Dp = BgmSpacing.md

    /** 给滚动内容留出的底部避让，避免最后一项被悬浮底栏遮住。 */
    val ContentBottomClearance: Dp = BgmSpacing.Snackbar.floatingBarClearance

    /** 沉浸模式下滚动内容首项距离顶栏的建议避让（56dp 紧凑顶栏标准高度）。 */
    val ContentTopClearance: Dp = BgmSpacing.TopBar.height
}

@Composable
fun BgmFloatingPageScaffold(
    modifier: Modifier = Modifier,
    topBarMode: BgmTopBarMode = BgmTopBarMode.Immersive,
    topScrimHeight: Dp? = null,
    topScrimSolidHeight: Dp? = null,
    topBar: @Composable () -> Unit = {},
    floatingBottomBar: (@Composable () -> Unit)? = null,
    floatingBottomBarSink: Dp = BgmFloatingPageScaffoldDefaults.FloatingChromeSink,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    BgmBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            // 顶部渐隐天幕：仅在沉浸模式下开启，当内容向上滚动滑向状态栏时，平滑消融于天幕
            if (topBarMode == BgmTopBarMode.Immersive) {
                BgmTopChromeScrim(
                    modifier = Modifier.align(Alignment.TopCenter),
                    height = topScrimHeight,
                    solidHeight = topScrimSolidHeight,
                )
            }

            // 顶栏覆盖层：沉浸模式下为透明穿透层，实体模式下填充底色并覆盖状态栏
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            ) {
                topBar()
            }

            if (floatingBottomBar != null) {
                BgmFloatingChromeScrim(modifier = Modifier.align(Alignment.BottomCenter))
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = floatingBottomBarSink),
                ) {
                    floatingBottomBar()
                }
            }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
            ) {
                snackbarHost()
            }
        }
    }
}
