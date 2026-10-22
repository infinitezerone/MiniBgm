package com.infinitezerone.minibgm.ui.component

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.R
import com.infinitezerone.minibgm.core.designsystem.component.aquamorphicPress
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.AiGlowSweepColors
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing

/**
 * 底部导航栏右侧独立的 AI Action Button。
 *
 * 采用 56dp 圆形形态（与悬浮底栏严格齐平），外围环绕动态彩虹呼吸晕染阴影 (sweepGradient shadow)，
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
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        }

    val infiniteTransition = rememberInfiniteTransition(label = "ai_shadow_breathing")
    val animatedSpread by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 5.5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "ai_shadow_spread",
    )
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.85f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "ai_shadow_alpha",
    )

    val shape = CircleShape
    val colors = AiGlowSweepColors

    Box(
        modifier =
            modifier
                .size(BgmSpacing.FloatingNav.height)
                .aquamorphicPress(
                    interactionSource = interactionSource,
                    pressedScale = 0.92f,
                ).dropShadow(
                    shape = shape,
                    shadow =
                        Shadow(
                            radius = 10.dp,
                            spread = animatedSpread.dp,
                            brush = Brush.sweepGradient(colors),
                            offset = DpOffset(x = 0.dp, y = 0.dp),
                            alpha = animatedAlpha,
                        ),
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BgmSpacing.FloatingNav.iconSize),
        )
    }
}
