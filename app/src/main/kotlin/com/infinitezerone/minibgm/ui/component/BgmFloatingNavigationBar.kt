package com.infinitezerone.minibgm.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicShadow
import com.infinitezerone.minibgm.core.designsystem.component.expressivePress
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination

/**
 * 手机端居中悬浮胶囊底栏（Floating Navigation Island）：
 * 1. 标准 56dp 轻巧浮岛（8dp 工业栅格），去底色指示器，纯净图文排布；
 * 2. 24dp 饱满大图标 + 11.5sp 紧凑标签，形变与主色平滑过渡；
 * 3. 搭载双层复合高斯弥散光晕 [aquamorphicShadow] 与 [expressivePress] 弹性微触感。
 */
@Composable
fun BgmFloatingNavigationBar(
    currentDestination: NavKey,
    onDestinationSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
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

    val navModifier =
        modifier
            .wrapContentWidth()
            .height(BgmSpacing.FloatingNav.height)
            .widthIn(min = BgmSpacing.FloatingNav.minWidth, max = BgmSpacing.FloatingNav.maxWidth)
            .aquamorphicShadow(
                shape = CircleShape,
                isDark = isDark,
            )

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = effectiveBorder,
        modifier = navModifier,
    ) {
        BgmFloatingNavigationRow(
            currentDestination = currentDestination,
            onDestinationSelected = onDestinationSelected,
        )
    }
}

@Composable
private fun BgmFloatingNavigationRow(
    currentDestination: NavKey,
    onDestinationSelected: (NavKey) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BgmSpacing.FloatingNav.itemGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxHeight()
                .padding(horizontal = BgmSpacing.FloatingNav.horizontalPadding),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val isSelected = destination.route == currentDestination
            BgmFloatingNavItem(
                destination = destination,
                isSelected = isSelected,
                onClick = { onDestinationSelected(destination.route) },
            )
        }
    }
}

@Composable
private fun BgmFloatingNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill_icon_color",
    )

    val textColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill_text_color",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxHeight()
                .expressivePress(
                    interactionSource = interactionSource,
                    pressedScale = 0.94f,
                ).clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 28.dp),
                    role = Role.Tab,
                    onClick = onClick,
                ).defaultMinSize(minWidth = BgmSpacing.FloatingNav.itemMinWidth)
                .padding(horizontal = 8.dp),
    ) {
        val label = stringResource(destination.labelRes)
        AnimatedContent(
            targetState = isSelected,
            transitionSpec = {
                (
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                    ) +
                        fadeIn(
                            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh),
                        )
                ).togetherWith(
                    scaleOut(
                        targetScale = 1.2f,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
                    ) +
                        fadeOut(
                            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh),
                        ),
                )
            },
            label = "nav_icon_morph",
            modifier = Modifier.size(BgmSpacing.FloatingNav.iconSize),
        ) { selected ->
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(BgmSpacing.FloatingNav.labelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}
