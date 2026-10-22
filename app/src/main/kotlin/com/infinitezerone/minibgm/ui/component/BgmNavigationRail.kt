package com.infinitezerone.minibgm.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing

/**
 * 平板与折叠屏大屏下的侧边导航栏。
 *
 * 采用与悬浮胶囊底栏统一的水生悬浮胶囊设计，
 * 垂直居中展示并独占水平物理占位，保障右侧内容与手势返回无遮挡。
 */
@Composable
fun BgmNavigationRail(
    currentDestination: NavKey,
    onDestinationSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(BgmSpacing.SideNavigation.railWidth),
        contentAlignment = Alignment.Center,
    ) {
        BgmFloatingNavigationBar(
            currentDestination = currentDestination,
            onDestinationSelected = onDestinationSelected,
            isVertical = true,
        )
    }
}
