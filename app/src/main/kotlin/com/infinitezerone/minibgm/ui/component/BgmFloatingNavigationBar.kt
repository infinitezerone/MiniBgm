package com.infinitezerone.minibgm.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination

/** 平板/折叠屏大屏下垂直悬浮导航栏的停靠边侧。 */
enum class NavDockSide {
    LEFT,
    RIGHT,
}

/**
 * MiniBgm 现代化自适应悬浮胶囊导航栏（Floating Navigation Island / Dock）：
 * 1. 手机端（水平形态）：居中悬浮胶囊形态（CircleShape），内容在悬浮岛下方自然穿行下潜；
 * 2. 大屏端（垂直形态）：自动变身侧边垂直悬浮胶囊，支持左右侧一键对飞与拖拽磁吸停靠；
 * 3. 选中项采用 Expressive 弹簧动效胶囊指示器，图标与文字平滑过渡，配合细腻触感反馈。
 */
@Composable
fun BgmFloatingNavigationBar(
    currentDestination: NavKey,
    onDestinationSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
    dockSide: NavDockSide = NavDockSide.LEFT,
    onToggleDockSide: (() -> Unit)? = null,
) {
    if (isVertical) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border =
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
            modifier =
                modifier
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .pointerInput(dockSide) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount > 20 && dockSide == NavDockSide.LEFT) {
                                onToggleDockSide?.invoke()
                            } else if (dragAmount < -20 && dockSide == NavDockSide.RIGHT) {
                                onToggleDockSide?.invoke()
                            }
                        }
                    },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            ) {
                // 顶部左右侧切换极简按钮（像华为一样一键飞跃至对侧）
                if (onToggleDockSide != null) {
                    val haptic = LocalHapticFeedback.current
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleDockSide()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = if (dockSide == NavDockSide.LEFT) "移至右侧" else "移至左侧",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination.route == currentDestination
                    BgmVerticalFloatingNavItem(
                        destination = destination,
                        isSelected = isSelected,
                        onClick = { onDestinationSelected(destination.route) },
                    )
                }
            }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border =
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
            modifier =
                modifier
                    .wrapContentWidth()
                    .widthIn(min = 200.dp, max = 380.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
    }
}

@Composable
private fun BgmVerticalFloatingNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val backgroundColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "vertical_pill_background",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "vertical_pill_content_color",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 28.dp),
                    role = Role.Tab,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                ).defaultMinSize(minWidth = 56.dp, minHeight = 52.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.labelText,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = destination.labelText,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun BgmFloatingNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val backgroundColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill_background",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill_content_color",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            modifier
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 28.dp),
                    role = Role.Tab,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                ).defaultMinSize(minWidth = 52.dp, minHeight = 44.dp)
                .padding(horizontal = if (isSelected) 16.dp else 12.dp, vertical = 8.dp)
                .animateContentSize(
                    animationSpec =
                        spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                ),
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.labelText,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = destination.labelText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
