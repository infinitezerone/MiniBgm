package com.infinitezerone.minibgm.core.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/**
 * 底部导航栏顶层 Tab 配置枚举；route 为各顶层 Tab 声明的 NavKey
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
    val route: NavKey,
) {
    SCHEDULE(
        selectedIcon = BgmIcons.Calendar,
        unselectedIcon = BgmIcons.CalendarBorder,
        labelRes = R.string.core_navigation_tab_schedule,
        route = ScheduleRoute,
    ),
    EXPLORE(
        selectedIcon = BgmIcons.Explore,
        unselectedIcon = BgmIcons.ExploreBorder,
        labelRes = R.string.core_navigation_tab_explore,
        route = ExploreRoute,
    ),
    USER(
        selectedIcon = BgmIcons.User,
        unselectedIcon = BgmIcons.UserBorder,
        labelRes = R.string.core_navigation_tab_user,
        route = UserRoute,
    ),
}
