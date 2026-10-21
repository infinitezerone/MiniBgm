package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens

enum class BgmPillSize {
    Compact,
    Standard,
    Large,
}

enum class BgmPillVariant {
    Primary,
    Secondary,
    Tonal,
    Outlined,
}

/**
 * ChatGPT 风格高反差实体胶囊操作键 (Solid Pill Button)
 *
 * 在深色模式下呈现纯白实体药丸键，触觉分量极强，是卡片或界面的第一视觉落点。
 * 纯净交互（零强制震动）。
 */
@Composable
fun BgmPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: BgmPillSize = BgmPillSize.Standard,
    variant: BgmPillVariant = BgmPillVariant.Primary,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val height: Dp =
        when (size) {
            BgmPillSize.Compact -> BgmContentTokens.PillButton.compactHeight
            BgmPillSize.Standard -> BgmContentTokens.PillButton.height
            BgmPillSize.Large -> BgmContentTokens.PillButton.largeHeight
        }

    val horizontalPadding: Dp =
        when (size) {
            BgmPillSize.Compact -> BgmContentTokens.PillButton.compactHorizontalPadding
            BgmPillSize.Standard -> BgmContentTokens.PillButton.horizontalPadding
            BgmPillSize.Large -> BgmContentTokens.PillButton.largeHorizontalPadding
        }

    val iconSize: Dp =
        when (size) {
            BgmPillSize.Compact -> BgmContentTokens.PillButton.compactIconSize
            else -> BgmContentTokens.PillButton.iconSize
        }

    val fontSize =
        when (size) {
            BgmPillSize.Compact -> 12.sp
            BgmPillSize.Standard -> 14.sp
            BgmPillSize.Large -> 15.sp
        }

    val containerColor: Color =
        when (variant) {
            BgmPillVariant.Primary -> BgmContentTokens.PillButton.primaryContainerColor()
            BgmPillVariant.Secondary -> BgmContentTokens.PillButton.secondaryContainerColor()
            BgmPillVariant.Tonal -> MaterialTheme.colorScheme.secondaryContainer
            BgmPillVariant.Outlined -> Color.Transparent
        }

    val contentColor: Color =
        when (variant) {
            BgmPillVariant.Primary -> BgmContentTokens.PillButton.onPrimaryContainerColor()
            BgmPillVariant.Secondary -> BgmContentTokens.PillButton.onSecondaryContainerColor()
            BgmPillVariant.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
            BgmPillVariant.Outlined -> MaterialTheme.colorScheme.onSurface
        }

    val border: BorderStroke? =
        if (variant == BgmPillVariant.Outlined) {
            BorderStroke(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
            )
        } else {
            null
        }

    val effectiveContainer = if (enabled) containerColor else containerColor.copy(alpha = 0.38f)
    val effectiveContent = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)

    Surface(
        shape = BgmContentTokens.PillButton.shape,
        color = effectiveContainer,
        contentColor = effectiveContent,
        border = border,
        modifier =
            modifier
                .expressivePress(
                    interactionSource = interactionSource,
                    pressedScale = 0.95f,
                    enabled = enabled,
                ).height(height)
                .clip(BgmContentTokens.PillButton.shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = effectiveContent),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BgmContentTokens.PillButton.iconSpacing),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = effectiveContent,
                )
            }
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp,
                    ),
                color = effectiveContent,
                maxLines = 1,
            )
        }
    }
}
