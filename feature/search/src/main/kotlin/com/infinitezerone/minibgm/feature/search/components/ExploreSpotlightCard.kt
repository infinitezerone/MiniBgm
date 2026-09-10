package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.designsystem.theme.BadgeClassic
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusAiring
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute

/**
 * 发现流顶部「今日焦点 / 深度安利」大卡（打破千篇一律的网格货架，注入编辑感与视觉重心）：
 * - 采用宽幅横版电影海报背景与暗色渐变氛围；
 * - 突出「🔥 同好力荐 / 本季焦点」徽章与高赞看点；
 * - 展示真实社区热度（如 3.6k 人在追 / 9.0 分）；
 * - 提供沉浸式的安利导语与零阻力快速追番。
 */
@Composable
fun ExploreSpotlightCard(
    subject: Subject,
    isWished: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleWish: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedTags: Set<String> = emptySet(),
    onTagClick: (String) -> Unit = {},
    hotComment: SubjectComment? = null,
) {
    val rating = subject.rating
    val score = rating?.score ?: 0.0
    val rank = rating?.rank ?: 0
    val doingCount = subject.collection?.doing ?: 0
    val imageUrl = subject.images?.bestImage.orEmpty()

    // 提炼有悬念、吸引人的剧情钩子
    val storyHook =
        subject.summary
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("电视动画") && !it.startsWith("《") }
            ?: subject.summary.take(80)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    onSubjectClick(
                        SubjectDetailRoute(
                            subjectId = subject.id,
                            initialName = subject.displayName,
                            initialCoverUrl = imageUrl,
                            initialScore = score,
                        ),
                    )
                },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
        ) {
            // 1. 宽幅海报背景
            AsyncImage(
                model = imageUrl,
                contentDescription = subject.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 2. 纵向暗色渐变遮罩（确保文字在任何明暗背景下都绝对清晰）
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.65f),
                                        Color.Black.copy(alpha = 0.92f),
                                    ),
                            ),
                        ),
            )

            // 3. 内容层
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 顶部：客观数据浮层（评分 + 社交人数），左侧留白展现海报构图
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (score > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = RatingGold,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = score.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    val isRecent = isRecentAiring(subject.date.ifBlank { subject.airDate })
                    val ratingTotal = rating?.total ?: 0

                    if (isRecent && doingCount > 50) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusAiring.copy(alpha = 0.88f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "${formatCount(doingCount)} 人在追",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    } else if (ratingTotal > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BadgeClassic.copy(alpha = 0.88f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "${formatCount(ratingTotal)} 人评分",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                // 中间与底部：标题、看点钩子与快捷按钮
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = subject.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (hotComment != null && hotComment.comment.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            ) {
                                Text(
                                    text = "🔥 社区热评" + if (hotComment.rate > 0) " ★${hotComment.rate}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                            if (!hotComment.user?.displayName.isNullOrBlank()) {
                                Text(
                                    text = "@${hotComment.user?.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "“ ${hotComment.comment.trim()} ”",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.92f),
                            fontStyle = FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (storyHook.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "“ $storyHook ”",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontStyle = FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 底部操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 社区精选标签
                        val highValueTags =
                            subject.tags
                                .filter { it.name !in setOf("TV", "日本", "动画", "原创", "漫画改") }
                                .take(3)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            highValueTags.forEach { tag ->
                                val isTagSelected = tag.name in selectedTags
                                Surface(
                                    onClick = { onTagClick(tag.name) },
                                    shape = RoundedCornerShape(6.dp),
                                    color =
                                        if (isTagSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White.copy(alpha = 0.25f)
                                        },
                                ) {
                                    Text(
                                        text = (if (isTagSelected) "✓ " else "#") + tag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        // 追番按钮
                        val wishButtonColor by animateColorAsState(
                            targetValue =
                                if (isWished) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                            label = "SpotlightWishColor",
                        )
                        val wishTextColor by animateColorAsState(
                            targetValue =
                                if (isWished) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    Color.Black
                                },
                            label = "SpotlightWishTextColor",
                        )

                        Surface(
                            onClick = { onToggleWish(subject.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = wishButtonColor,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (isWished) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = null,
                                    tint = wishTextColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (isWished) "已在想看" else "+ 加入想看",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = wishTextColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
