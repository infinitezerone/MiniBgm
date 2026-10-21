package com.infinitezerone.minibgm.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicShadow
import com.infinitezerone.minibgm.core.designsystem.component.expressivePress
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination

/**
 * 手机端居中悬浮胶囊底栏（Floating Navigation Island）：
 * 1. 与顶栏 [com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton] 动作按钮同源对齐：
 *    - 浅色采用纯白实色表面 [MaterialTheme.colorScheme.surface]，深色采用曜石黑 [MaterialTheme.colorScheme.surfaceContainerHigh]；
 *    - 搭载双层复合高斯弥散光晕 [aquamorphicShadow]，淘汰粗糙生硬的黑圈阴影；
 *    - 深色模式辅以 0.5dp 微光极细轮廓发丝线，维持深色天幕上的立体悬浮感；
 * 2. 微交互与纯净物理反馈：
 *    - 各导航项集成 [expressivePress] 弹性物理缩放；
 *    - 零强制触觉震动，遵循系统纯净交互立场；
 * 3. 选中项采用灵动指示器与品牌主色形变切换。
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
            Modifier.padding(
                horizontal = BgmSpacing.FloatingNav.horizontalPadding,
                vertical = BgmSpacing.FloatingNav.verticalPadding,
            ),
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
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val indicatorColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                if (isDark) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                }
            } else {
                Color.Transparent
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill_indicator_background",
    )

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
                .padding(BgmSpacing.FloatingNav.itemPadding),
    ) {
        val label = stringResource(destination.labelRes)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 44.dp, height = 26.dp),
        ) {
            // 指示器胶囊：绘制在图标层之下，选中时弹性展开、取消时收缩消失
            val indicatorScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                label = "pill_indicator_scale",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = indicatorScale
                            scaleY = indicatorScale
                        }.clip(CircleShape)
                        .background(indicatorColor),
            )
            // 图标形变：选中/未选中两个形态之间做弹性缩放交替
            AnimatedContent(
                targetState = isSelected,
                transitionSpec = {
                    (
                        scaleIn(
                            initialScale = 0.55f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        ) +
                            fadeIn(
                                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh),
                            )
                    ).togetherWith(
                        scaleOut(
                            targetScale = 1.35f,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
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
        }
        Spacer(modifier = Modifier.height(BgmSpacing.FloatingNav.labelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}
