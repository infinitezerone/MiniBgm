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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination

/**
 * 手机端居中悬浮胶囊底栏（Floating Navigation Island）：
 * 1. 水平居中悬浮胶囊形态（CircleShape），内容在悬浮岛下方自然穿行下潜；
 * 2. 纵向垂直排版（上方图标 + 下方文字常驻显示），避免横向伸缩位移，切换稳定流畅；
 * 3. 选中项采用 Material 3 规范胶囊指示器：胶囊弹性展开，图标在两个形态间弹性形变（M3 Expressive），
 *    配合 SegmentTick 触感反馈。
 */
@Composable
fun BgmFloatingNavigationBar(
    currentDestination: NavKey,
    onDestinationSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            ),
        modifier =
            modifier
                .wrapContentWidth()
                .widthIn(min = 220.dp, max = 380.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

@Composable
private fun BgmFloatingNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val indicatorColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill_indicator_background",
    )

    val iconColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
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
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 28.dp),
                    role = Role.Tab,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    },
                ).defaultMinSize(minWidth = 64.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        val label = stringResource(destination.labelRes)
        Box(contentAlignment = Alignment.Center) {
            // 指示器胶囊：绘制在图标层之下，选中时弹性展开、取消时收缩消失（M3 Expressive spring）
            val indicatorScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                label = "pill_indicator_scale",
            )
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = indicatorScale
                            scaleY = indicatorScale
                        }.clip(CircleShape)
                        .background(indicatorColor),
            )
            // 图标形变：选中/未选中两个形态之间做弹性缩放交替——
            // 新形态以 0.55 倍轻微回弹切入，旧形态放大淡出，读作连续的"变形"而非跳变
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).size(22.dp),
            ) { selected ->
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}
