package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 现代插槽式媒体内容卡片 (Slot-based Media Card)
 *
 * 遵循 Compose 最佳实践 Slot API 模式与 M3 Expressive 设计规范：
 * - 纯实色自然明度差浮起，零生硬描边；
 * - 弹性物理微动效 (expressivePress 0.985f)；
 * - 完备的 Compose 插槽体系：leading、title、subtitle、badge、meta、footer；
 * - 内容自然排版与呼吸感，彻底告别死板定高挤压。
 */
@Composable
fun BgmMediaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    meta: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: Dp = 12.dp,
    itemGap: Dp = 12.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = null,
        modifier =
            modifier
                .fillMaxWidth()
                .expressivePress(interactionSource = interactionSource, pressedScale = 0.985f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(itemGap),
            ) {
                // Leading 插槽 (封面海报 / 缩略图)
                leading()

                // 主信息列插槽
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 顶行：标题与徽章插槽
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            title()
                        }
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            badge()
                        }
                    }

                    // 副标题插槽 (原名 / 制作方 / 别名)
                    if (subtitle != null) {
                        subtitle()
                    }

                    // 元信息 / 标签插槽 (集数、首播平台、播出状态等)
                    if (meta != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        meta()
                    }
                }
            }

            // 底部扩展插槽 (多集直达导轨、快捷操作等)
            if (footer != null) {
                footer()
            }
        }
    }
}
