package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute

private val ONGOING_CARD_WIDTH = 108.dp

/**
 * 「本季连载中」横向分组卡片。
 *
 * 与 [SeasonalAnimeCard] 的区别：这里只承担"看海报 → 进详情"，
 * 不在横向滚动区里塞追番按钮（横滑时极易误触）；首播年份是这一组的核心信息，
 * 因为它正是这批条目被 Bangumi `air_date` 过滤漏掉的原因。
 */
@Composable
fun OngoingAnimeCard(
    subject: Subject,
    onClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTitle = subject.displayName
    val premiereYear = subject.date.take(4).takeIf { it.length == 4 }

    Column(
        modifier =
            modifier
                .width(ONGOING_CARD_WIDTH)
                .clickable {
                    onClick(
                        SubjectDetailRoute(
                            subjectId = subject.id,
                            initialName = primaryTitle,
                            initialCoverUrl = subject.images?.bestImage.orEmpty(),
                            initialScore = subject.rating?.score ?: 0.0,
                            source = "seasonal_guide",
                        ),
                    )
                },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CoverImage(
                url = subject.images?.bestImage.orEmpty(),
                contentDescription = primaryTitle,
                cornerRadius = 10.dp,
                aspectRatio = 2f / 3f,
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Text(
                    text = "连载中",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = primaryTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (premiereYear != null) {
            Text(
                text = "$premiereYear 年首播",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
