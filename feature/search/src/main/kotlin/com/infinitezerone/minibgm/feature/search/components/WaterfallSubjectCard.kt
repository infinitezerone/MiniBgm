package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.BadgeClassic
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusAiring
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

private val GENERIC_TAG_FILTER = setOf("TV", "日本", "动画", "原创", "漫画改", "轻改", "小说改", "漫改")

/**
 * 双列安利瀑布流卡片（小红书 / 小黑盒形态）：
 * 1. 超清海报视觉呈现与主色渐变氛围；
 * 2. 硬核评分与社区在看热度徽章（★ 8.8 · 🔥 3.4k人在看）；
 * 3. 筛选高共鸣的同好圈子标签（如 治愈、寿命论、高燃、反转）；
 * 4. 提取吸引人的第一人称或剧情悬念钩子，告别冷冰冰的百科简介；
 * 5. 零阻力快捷动作（右下角一键「+ 想看」微交互）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterfallSubjectCard(
    subject: Subject,
    isWished: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleWish: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedTags: Set<String> = emptySet(),
    onTagClick: (String) -> Unit = {},
    hotComment: SubjectComment? = null,
) {
    val primaryTitle = subject.displayName
    val rating = subject.rating
    val rank = rating?.rank ?: 0
    val score = rating?.score ?: 0.0
    val doingCount = subject.collection?.doing ?: 0

    // 智能提取有悬念或情绪感的安利钩子（过滤掉枯燥的百科说明），使用 remember 避免滚动重组高频计算
    val summaryQuote =
        remember(subject.summary) {
            subject.summary
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { line ->
                    line.isNotBlank() &&
                        !line.startsWith("电视动画") &&
                        !line.startsWith("《") &&
                        !line.startsWith("改编自") &&
                        !line.startsWith("由")
                } ?: subject.summary
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
        }

    // 筛选有性格、高共鸣的同好标签（剔除冷冰冰的格式标签），使用 remember 避免高频集合创建与过滤
    val flavorfulTags =
        remember(subject.tags) {
            subject.tags
                .filter { tag ->
                    tag.name !in GENERIC_TAG_FILTER &&
                        !tag.name.all { c -> c.isDigit() }
                }.take(3)
        }

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = subject.id,
                    initialName = primaryTitle,
                    initialCoverUrl = subject.images?.bestImage.orEmpty(),
                    initialScore = score,
                    source = "explore",
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 封面图区域（带评分/热度浮层角标）
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    cornerRadius = 10.dp,
                    aspectRatio = 0.72f,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, "explore"),
                                clipInOverlayDuringTransition = RoundedCornerShape(10.dp),
                            ),
                )

                // 评分与社区在看热度胶囊（左上角浮层）
                Row(
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (score > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = RatingGold,
                                    modifier = Modifier.size(11.dp),
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
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = "${formatCount(doingCount)} 追",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    } else if (ratingTotal > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BadgeClassic.copy(alpha = 0.85f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = "${formatCount(ratingTotal)} 评",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    } else if (rank in 1..100) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        ) {
                            Text(
                                text = "Top $rank",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // 2. 信息与安利文案区
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
            ) {
                // 标题
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 同好高票特色标签
                if (flavorfulTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxLines = 1,
                    ) {
                        flavorfulTags.forEach { tag ->
                            val isTagSelected = tag.name in selectedTags
                            Surface(
                                onClick = { onTagClick(tag.name) },
                                shape = RoundedCornerShape(6.dp),
                                color =
                                    if (isTagSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    },
                            ) {
                                Text(
                                    text = (if (isTagSelected) "✓ " else "#") + tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (isTagSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 社区精选热评或剧情安利句
                if (hotComment != null && hotComment.comment.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "💬 " + (hotComment.user?.displayName ?: "同好"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (hotComment.rate > 0) {
                                    Text(
                                        text = "★${hotComment.rate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RatingGold,
                                    )
                                }
                            }
                            Text(
                                text = "“${hotComment.comment.trim()}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (!summaryQuote.isNullOrBlank()) {
                    Text(
                        text = "“ ${summaryQuote.take(50)} ”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 底部操作栏：播出年份与「+ 想看」按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dateText = subject.date.ifBlank { subject.airDate }
                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText.take(7),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // 想看按钮
                    WishButton(
                        isWished = isWished,
                        onClick = { onToggleWish(subject.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WishButton(
    isWished: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue =
            if (isWished) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        label = "WishContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (isWished) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "WishContentColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (isWished) 1.05f else 1.0f,
        label = "WishScale",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier.scale(scale),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = if (isWished) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isWished) "已想看" else "想看",
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (isWished) "已想看" else "+ 想看",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

internal fun formatCount(count: Int): String =
    when {
        count >= 10000 -> {
            val wan = count / 10000.0
            String.format(java.util.Locale.getDefault(), "%.1fw", wan)
        }
        count >= 1000 -> {
            val qian = count / 1000.0
            String.format(java.util.Locale.getDefault(), "%.1fk", qian)
        }
        else -> count.toString()
    }

internal fun isRecentAiring(dateStr: String): Boolean {
    if (dateStr.isBlank()) return false
    val year = dateStr.take(4).toIntOrNull() ?: return false
    val currentYear =
        java.time.LocalDate
            .now()
            .year
    return year >= currentYear
}
