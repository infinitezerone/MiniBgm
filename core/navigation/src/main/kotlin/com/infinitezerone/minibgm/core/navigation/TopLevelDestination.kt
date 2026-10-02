package com.infinitezerone.minibgm.core.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/**
 * 底部导航栏顶层 Tab 配置枚举；route 为各顶层 Tab 声明的 NavKey
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelText: String,
    val route: NavKey,
) {
    SCHEDULE(
        selectedIcon = BgmIcons.Calendar,
        unselectedIcon = BgmIcons.CalendarBorder,
        labelText = "放送",
        route = ScheduleRoute,
    ),
    EXPLORE(
        selectedIcon = BgmIcons.Explore,
        unselectedIcon = BgmIcons.ExploreBorder,
        labelText = "探索",
        route = ExploreRoute,
    ),
    USER(
        selectedIcon = BgmIcons.User,
        unselectedIcon = BgmIcons.UserBorder,
        labelText = "我的",
        route = UserRoute,
    ),
}
