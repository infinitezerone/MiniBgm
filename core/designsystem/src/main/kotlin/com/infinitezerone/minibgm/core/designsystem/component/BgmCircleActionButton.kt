package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ChatGPT / ColorOS 15 式微交互圆形操作按键 (Circle Action Button)。
 *
 * 遵循现代极简与自然光影设计：
 * - 40dp 视觉大圆盘，外层经 [minimumInteractiveComponentSize] 保证 ≥48dp 触控靶区（M3 可访问性）；
 * - 搭载双层复合高斯弥散光晕 (Aquamorphic Diffuse Shadow)，阴影向四周柔和晕开，无生硬黑圈；
 * - 纯净交互（零强制震动，尊重系统触感立场）；
 * - 暗色模式下辅以 0.5dp 微光极细轮廓，确保深色背景上的悬浮通透感。
 */
@Composable
fun BgmCircleActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    hasDiffuseShadow: Boolean = true,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }

    // 暗色模式默认与卡片一致零描边，依靠 surface 与 surfaceContainerLow 的明度阶梯自然浮起
    val effectiveBorder = border

    val shadowModifier =
        if (hasDiffuseShadow && enabled) {
            Modifier.aquamorphicShadow(
                shape = CircleShape,
                isDark = isDark,
            )
        } else {
            Modifier
        }

    // 触控靶区节点持有 clickable（与视觉圆盘共享 interactionSource），
    // 40dp 视觉圆盘居中其上，按压动效事件仍由同一 interactionSource 驱动。
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .expressivePress(
                        interactionSource = interactionSource,
                        pressedScale = 0.92f,
                        enabled = enabled,
                    ).then(shadowModifier)
                    .size(size)
                    .background(
                        color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
                        shape = CircleShape,
                    ).then(
                        if (effectiveBorder != null) {
                            Modifier.border(effectiveBorder, CircleShape)
                        } else {
                            Modifier
                        },
                    ).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
            )
        }
    }
}
