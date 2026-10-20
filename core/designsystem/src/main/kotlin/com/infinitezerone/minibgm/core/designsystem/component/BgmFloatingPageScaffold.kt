package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
 * - [topBar] 是唯一的结构性顶栏槽位，默认由调用方传入 [BgmTopAppBar]；
 * - [content] 占据剩余空间，横向不设默认边距，页面继续持有自己的内容间距；
 * - [floatingBottomBar] 独立悬浮在内容层上方，并负责导航栏安全区与下沉呼吸距离。
 *
 * 这是布局原语，不包含任何业务页面、路由或状态。页面接入时必须单独评审，
 * 不得因为组件存在就批量替换现有 `Scaffold`。
 */
object BgmFloatingPageScaffoldDefaults {
    /** 悬浮 chrome 与内容底部之间的视觉呼吸距离。 */
    val FloatingChromeSink: Dp = BgmSpacing.md

    /** 给滚动内容留出的底部避让，避免最后一项被悬浮底栏遮住。 */
    val ContentBottomClearance: Dp = BgmSpacing.Snackbar.floatingBarClearance
}

@Composable
fun BgmFloatingPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingBottomBar: (@Composable () -> Unit)? = null,
    floatingBottomBarSink: Dp = BgmFloatingPageScaffoldDefaults.FloatingChromeSink,
    content: @Composable BoxScope.() -> Unit,
) {
    BgmBackdrop(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                content()
                if (floatingBottomBar != null) {
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
            }
        }
    }
}
