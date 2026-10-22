package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 现代极简分段控制器 (Segmented Control)
 *
 * 遵循 iOS / Now-in-Android 设计语言：
 * 1. 扁平嵌入式胶囊底盘，无生硬外边框线；
 * 2. 选中项采用高阶灰色块平滑承载，无突兀勾选图标；
 * 3. 零点击涟漪，搭配 expressivePress 细微物理微动效；
 * 4. 完美融入深色模式 #26262B 阶梯。
 */
@Composable
fun <T> BgmSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerShape: Shape = RoundedCornerShape(12.dp),
    itemShape: Shape = RoundedCornerShape(8.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedItemColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    unselectedItemColor: Color = Color.Transparent,
    height: Dp = 38.dp,
    contentPadding: Dp = 3.dp,
    itemSpacing: Dp = 4.dp,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit = { item, isSelected ->
        Text(
            text = item.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    },
) {
    Surface(
        shape = containerShape,
        color = containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val interactionSource = remember { MutableInteractionSource() }

                Surface(
                    shape = itemShape,
                    color = if (isSelected) selectedItemColor else unselectedItemColor,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(height)
                            .expressivePress(
                                interactionSource = interactionSource,
                                pressedScale = 0.97f,
                            ).clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onItemSelected(item) },
                            ),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemContent(item, isSelected)
                    }
                }
            }
        }
    }
}
