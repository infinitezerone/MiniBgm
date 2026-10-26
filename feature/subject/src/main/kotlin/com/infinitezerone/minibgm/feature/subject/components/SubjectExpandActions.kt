package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 小节级集合折叠展开控制器 (Collection-level Section Toggle Action)：
 * 统领位于小节标题栏右侧，遵循渐进式呈现与格式塔邻近原则。
 * 具有固定的空间锚点，箭头随展开状态 0° -> 180° 平滑旋转。
 */
@Composable
fun SubjectSectionToggleAction(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    totalCount: Int? = null,
    collapsedText: String? = null,
    expandedText: String = stringResource(R.string.feature_subject_collapse),
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "SectionToggleArrowRotation",
    )

    val labelText =
        when {
            isExpanded -> expandedText
            collapsedText != null -> collapsedText
            totalCount != null -> stringResource(R.string.feature_subject_view_all_count, totalCount)
            else -> stringResource(R.string.feature_subject_expand_all)
        }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        Icon(
            imageVector = BgmIcons.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp).rotate(rotation),
            tint = contentColor,
        )
    }
}

/**
 * 连续正文长文本截断展开行 (Inline Content Text Expand Row)：
 * 专用于剧情简介等长段落流文本底部，顺应自然阅读动线。
 * 点击触发行数平滑伸缩，箭头带 0° -> 180° 旋转。
 */
@Composable
fun SubjectContentExpandRow(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    collapsedText: String = stringResource(R.string.feature_subject_expand_summary),
    expandedText: String = stringResource(R.string.feature_subject_collapse_summary),
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ContentExpandArrowRotation",
    )

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (isExpanded) expandedText else collapsedText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        Icon(
            imageVector = BgmIcons.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp).rotate(rotation),
            tint = contentColor,
        )
    }
}
