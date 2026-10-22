package com.infinitezerone.minibgm.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicShadow
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination

/**
 * 平板与折叠屏大屏下的侧边导航栏。
 *
 * 容器采用新 UI 悬浮风格（外边距悬浮、圆角、柔和阴影与暗色描边），
 * 内部文字与图标排版遵守侧边导航栏规范准则（NavigationRailItem、上下布局、活动指示器药丸）。
 */
@Composable
fun BgmNavigationRail(
    currentDestination: NavKey,
    onDestinationSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val containerColor =
        if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        }
    val effectiveBorder =
        if (isDark) {
            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
        } else {
            null
        }
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .systemBarsPadding()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                .width(BgmSpacing.SideNavigation.railWidth),
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = effectiveBorder,
            modifier =
                Modifier
                    .fillMaxSize()
                    .aquamorphicShadow(
                        shape = shape,
                        isDark = isDark,
                    ),
        ) {
            NavigationRail(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxSize(),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination.route == currentDestination
                    val label = stringResource(destination.labelRes)
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onDestinationSelected(destination.route) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = label,
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors =
                            NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
                Spacer(modifier = Modifier.weight(1.5f))
            }
        }
    }
}
