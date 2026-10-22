package com.infinitezerone.minibgm.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.infinitezerone.minibgm.core.designsystem.component.CombinedInteractionSource
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicPress
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicShadow
import com.infinitezerone.minibgm.core.designsystem.theme.BgmMotion
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination

/**
 * 居中悬浮胶囊导航栏。
 */
@Composable
fun BgmFloatingNavigationBar(
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

    // 独立收集各项交互，汇聚为复合源供底栏 Node 零重组驱动 RenderNode 物理缩放
    val scheduleInteraction = remember { MutableInteractionSource() }
    val exploreInteraction = remember { MutableInteractionSource() }
    val userInteraction = remember { MutableInteractionSource() }
    val dockInteraction =
        remember(scheduleInteraction, exploreInteraction, userInteraction) {
            CombinedInteractionSource(scheduleInteraction, exploreInteraction, userInteraction)
        }

    val navModifier =
        modifier
            .wrapContentWidth()
            .height(BgmSpacing.FloatingNav.height)
            .widthIn(min = BgmSpacing.FloatingNav.minWidth, max = BgmSpacing.FloatingNav.maxWidth)
            .aquamorphicPress(
                interactionSource = dockInteraction,
                pressedScale = 0.95f,
            ).aquamorphicShadow(
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(BgmSpacing.FloatingNav.itemGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = BgmSpacing.FloatingNav.horizontalPadding),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val interactionSource =
                    when (destination) {
                        TopLevelDestination.SCHEDULE -> scheduleInteraction
                        TopLevelDestination.EXPLORE -> exploreInteraction
                        TopLevelDestination.USER -> userInteraction
                    }
                BgmFloatingNavItem(
                    destination = destination,
                    isSelected = destination.route == currentDestination,
                    interactionSource = interactionSource,
                    onClick = { onDestinationSelected(destination.route) },
                )
            }
        }
    }
}

@Composable
private fun BgmFloatingNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec =
            BgmMotion.effectsSpring(
                scheme = BgmMotion.Scheme.Aquamorphic,
                speed = BgmMotion.Speed.Fast,
            ),
        label = "pill_icon_color",
    )

    val textColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec =
            BgmMotion.effectsSpring(
                scheme = BgmMotion.Scheme.Aquamorphic,
                speed = BgmMotion.Speed.Fast,
            ),
        label = "pill_text_color",
    )

    val itemModifier =
        modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = BgmSpacing.FloatingNav.itemMinWidth)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = itemModifier,
    ) {
        val label = stringResource(destination.labelRes)
        AnimatedContent(
            targetState = isSelected,
            transitionSpec = {
                fadeIn(animationSpec = BgmMotion.m3StandardTween(durationMillis = 150))
                    .togetherWith(fadeOut(animationSpec = BgmMotion.m3StandardTween(durationMillis = 150)))
            },
            label = "nav_icon_fade",
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
