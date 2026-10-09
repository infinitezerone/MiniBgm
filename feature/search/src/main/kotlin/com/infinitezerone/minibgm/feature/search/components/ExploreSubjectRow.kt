package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute

private val ROW_LIMIT = 12

/**
 * 探索首页通用横滑行（B站首页 / Netflix Rows 形态）：
 * 区块标题 + 可选「更多」入口 + 横滑海报卡；加载中显示骨架卡，无数据且不在加载则整行隐藏（fail-open）。
 *
 * [horizontalPadding] 默认 16dp；嵌入瀑布流网格（自带 12dp 水平内边距）时传 4dp 以对齐卡片区。
 */
@Composable
fun ExploreSubjectRow(
    title: String,
    subjects: List<Subject>,
    isLoading: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMore: (() -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
) {
    if (!isLoading && subjects.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onOpenMore != null) {
                TextButton(onClick = onOpenMore, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(text = "更多", style = MaterialTheme.typography.labelMedium)
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowRight,
                        contentDescription = "查看「$title」完整榜单",
                        modifier = Modifier.size(16.dp).padding(start = 2.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLoading && subjects.isEmpty()) {
                items(4) { RowSkeletonCard() }
            } else {
                items(subjects.take(ROW_LIMIT), key = { it.id }) { subject ->
                    RowSubjectCard(
                        subject = subject,
                        onSubjectClick = onSubjectClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowSubjectCard(
    subject: Subject,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val score = subject.rating?.score ?: 0.0

    Column(
        modifier =
            modifier
                .width(108.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onSubjectClick(
                        SubjectDetailRoute(
                            subjectId = subject.id,
                            initialName = subject.displayName,
                            initialCoverUrl = subject.images?.bestImage.orEmpty(),
                            initialScore = score,
                            source = "explore",
                        ),
                    )
                },
    ) {
        CoverImage(
            url = subject.images?.bestImage.orEmpty(),
            contentDescription = subject.displayName,
            cornerRadius = 8.dp,
            aspectRatio = BGM_POSTER_ASPECT_RATIO,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = subject.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )

        Text(
            text = if (score > 0) "★ ${"%.1f".format(score)}" else "暂无评分",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (score > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun RowSkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(108.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(BGM_POSTER_ASPECT_RATIO)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .width(76.dp)
                    .height(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
        )
    }
}
