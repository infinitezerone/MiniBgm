package com.infinitezerone.minibgm.feature.subject.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 评论/讨论版回复排序规则（贴吧/论坛通用标准：热门、正序、倒序）
 */
enum class CommentSortOrder(
    @StringRes val labelRes: Int,
) {
    HOT(R.string.feature_subject_comment_sort_hot),
    ASCENDING(R.string.feature_subject_comment_sort_asc),
    DESCENDING(R.string.feature_subject_comment_sort_desc),
}

/**
 * 评论/回帖排序切换选项卡
 */
@Composable
internal fun CommentSortOrderTabs(
    currentOrder: CommentSortOrder,
    onOrderSelected: (CommentSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(2.dp),
        ) {
            CommentSortOrder.entries.forEach { order ->
                val isSelected = order == currentOrder
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOrderSelected(order) },
                ) {
                    Text(
                        text = stringResource(order.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
