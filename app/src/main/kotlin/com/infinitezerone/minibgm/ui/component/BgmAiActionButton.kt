package com.infinitezerone.minibgm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.infinitezerone.minibgm.R
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicPress
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicShadow
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.AiIconGradientColors
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing

/**
 * 底部导航栏右侧独立的 AI Action Button。
 *
 * 采用 56dp 圆形形态（与悬浮底栏同款标准水生弥散阴影与背景），
 * 内部图标采用 Google Gemini / Apple Intelligence 风格的绚丽多色极光渐变 (Cyan -> Blue -> Purple -> Pink -> Amber)，
 * 响应触控压缩交互并直通 AI 助手会话。
 */
@Composable
fun BgmAiActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor =
        if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        }

    val shape = CircleShape

    Box(
        modifier =
            modifier
                .size(BgmSpacing.FloatingNav.height)
                .aquamorphicPress(
                    interactionSource = interactionSource,
                    pressedScale = 0.92f,
                ).aquamorphicShadow(
                    shape = shape,
                    isDark = isDark,
                ).clip(shape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BgmIcons.Assistant,
            contentDescription = stringResource(R.string.app_overview_assistant),
            tint = Color.White,
            modifier =
                Modifier
                    .size(BgmSpacing.FloatingNav.iconSize)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val brush =
                            Brush.linearGradient(
                                colors = AiIconGradientColors,
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                        }
                    },
        )
    }
}
