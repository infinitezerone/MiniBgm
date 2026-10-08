package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeContent

/**
 * 可折叠评论/回帖正文组件：
 * 基于 Compose 原生 [maxLines] 与 [onTextLayout]（hasVisualOverflow）实现行级折叠。
 * 折叠状态下点击正文任意区域或文字链接均可展开，使用 [key] 保证列表滑动复用状态不混淆。
 */
@Composable
internal fun ExpandableCommentContent(
    content: String,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 5,
    key: Any? = null,
) {
    var isExpanded by rememberSaveable(key, content) { mutableStateOf(false) }
    var canExpand by rememberSaveable(key, content) { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (!isExpanded && canExpand) {
                        Modifier.clickable { isExpanded = true }
                    } else {
                        Modifier
                    },
                ).animateContentSize(),
    ) {
        BgmBbCodeContent(
            content = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            onOverflowChanged = { overflow ->
                if (overflow) canExpand = true
            },
            onUrlClick = onUrlClick,
        )

        if (canExpand) {
            Text(
                text = if (isExpanded) "收起" else "展开全文",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .clickable { isExpanded = !isExpanded },
            )
        }
    }
}
