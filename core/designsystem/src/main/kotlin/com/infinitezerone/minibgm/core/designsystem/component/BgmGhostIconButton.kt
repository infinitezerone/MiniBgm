package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 小号幽灵图标键 (Ghost Icon Button)：无底色的行内/对话框小操作键。
 *
 * - 视觉仅为图标本身（默认 [MaterialTheme.colorScheme.onSurfaceVariant] 档），
 *   替代 M3 [androidx.compose.material3.IconButton]——全局移除涟漪后，
 *   M3 原生键按下半径内零反馈，本原语是唯一带水生微缩反馈的小图标键；
 * - 外层 [minimumInteractiveComponentSize] 保证 ≥48dp 触控靶区（视觉≠靶区，同 BgmCircleActionButton 范式）；
 * - 纯净交互（零强制震动，尊重系统触感立场）。
 */
@Composable
fun BgmGhostIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // 触控靶区节点持有 clickable（与视觉图标共享 interactionSource），
    // 图标居中其上，按压水生微缩事件由同一 interactionSource 驱动。
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .expressivePress(
                    interactionSource = interactionSource,
                    pressedScale = 0.9f,
                    enabled = enabled,
                ).clickable(
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
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}
