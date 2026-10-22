package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ChatGPT / ColorOS 15 式微交互圆形操作按键 (Circle Action Button)。
 *
 * 遵循现代极简与自然光影设计：
 * - 40dp 精准触控大圆盘；
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
        if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    hasDiffuseShadow: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    // 暗色模式默认 0.5dp 弱光发丝描边，亮色模式下无边框纯靠弥散光晕浮起
    val effectiveBorder =
        border ?: if (isDark) {
            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
        } else {
            null
        }

    val shadowModifier =
        if (hasDiffuseShadow && enabled) {
            Modifier.aquamorphicShadow(
                shape = CircleShape,
                isDark = isDark,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
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
                ).clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
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
