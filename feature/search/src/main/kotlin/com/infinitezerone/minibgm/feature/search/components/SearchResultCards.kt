package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.ActionCollect
import com.infinitezerone.minibgm.core.designsystem.theme.ActionDoing
import com.infinitezerone.minibgm.core.designsystem.theme.ActionWish
import com.infinitezerone.minibgm.core.designsystem.theme.HighlightAmber
import com.infinitezerone.minibgm.core.designsystem.theme.HighlightContainer
import com.infinitezerone.minibgm.core.designsystem.theme.OnHighlightContainer
import com.infinitezerone.minibgm.core.designsystem.theme.OnRatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeAnime
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeBook
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeGame
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeMusic
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeReal
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGoldBright
import com.infinitezerone.minibgm.core.designsystem.theme.TypeAnimeContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeBookContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeGameContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeMusicContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeRealContainer
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType

/** 高质感详细卡片（多品类自适应徽章、关键词高亮、度量适配与 1-Tap 快捷三态打卡） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultCard(
    subject: Subject,
    currentStatus: CollectionType?,
    query: String,
    onSubjectClick: (Long) -> Unit,
    onToggleCollection: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectType = remember(subject.type) { SubjectType.fromValue(subject.type) }
    val typeTheme = remember(subjectType) { getSubjectTypeTheme(subjectType) }

    val primaryTitle = subject.displayName
    val secondaryTitle =
        if (subject.nameCn.isNotBlank() && subject.name.isNotBlank() && subject.nameCn != subject.name) {
            subject.name
        } else {
            null
        }

    val primaryTitleAnnotated =
        remember(primaryTitle, query) {
            highlightKeywords(primaryTitle, query, HighlightAmber)
        }
    val secondaryTitleAnnotated =
        remember(secondaryTitle, query) {
            secondaryTitle?.let { highlightKeywords(it, query, HighlightAmber) }
        }

    val dateText = subject.date.ifBlank { subject.airDate }
    val episodesNum = if (subject.totalEpisodes > 0) subject.totalEpisodes else subject.eps
    val metricText =
        when {
            subjectType == SubjectType.GAME -> null
            episodesNum > 0 -> "全 $episodesNum ${subjectType.unitName}"
            else -> null
        }

    val rating = subject.rating
    val rank = rating?.rank ?: 0

    val topTags =
        remember(subject.tags) {
            subject.tags
                .filter { it.name !in setOf("TV", "日本", "动画", "原创", "漫改", "轻改") && !it.name.all { c -> c.isDigit() } }
                .take(3)
        }

    Card(
        onClick = { onSubjectClick(subject.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 74dp x 104dp 高清封面
            Box(
                modifier =
                    Modifier
                        .width(74.dp)
                        .height(104.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(8.dp),
                        ),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 右侧内容区
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // 1. 中文主标题（带高亮）
                Text(
                    text = primaryTitleAnnotated,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // 2. 原名小字（带高亮）
                if (secondaryTitleAnnotated != null) {
                    Text(
                        text = secondaryTitleAnnotated,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.88f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 3. 元数据标识行（Rank + 品类专属徽章 + 开播/发售 + 话数/卷数）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 1.dp),
                ) {
                    if (rank > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = HighlightContainer,
                        ) {
                            Text(
                                text = "Rank #$rank",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                fontWeight = FontWeight.ExtraBold,
                                color = OnHighlightContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    // 品类定制色调徽章
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeTheme.containerColor,
                    ) {
                        Text(
                            text = "${subjectType.iconEmoji} ${subjectType.label}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                            fontWeight = FontWeight.Bold,
                            color = typeTheme.contentColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }

                    if (dateText.isNotBlank()) {
                        Text(
                            text = "${subjectType.releaseVerb}: $dateText",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (metricText != null) {
                        Text(
                            text = "· $metricText",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }

                // 4. 社区同好标签
                if (topTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        topTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = "#${tag.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp),
                                )
                            }
                        }
                    }
                }

                // 5. 评分与 1-Tap 追番三态胶囊
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                ) {
                    // 评分
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (rating != null && rating.score > 0.0) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = rating.score.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = RatingGold,
                            )
                        } else {
                            Text(
                                text = "暂无评分",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    // 1-Tap 快捷胶囊组（想看/想读/想听/想玩）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 想看 / 想读 / 想听 / 想玩
                        QuickCapsuleButton(
                            label = subjectType.actionWish,
                            isActive = currentStatus == CollectionType.WISH,
                            activeColor = ActionWish,
                            onClick = { onToggleCollection(CollectionType.WISH) },
                        )
                        // 在看 / 在读 / 在听 / 在玩
                        QuickCapsuleButton(
                            label = subjectType.actionDoing,
                            isActive = currentStatus == CollectionType.DOING,
                            activeColor = ActionDoing,
                            onClick = { onToggleCollection(CollectionType.DOING) },
                        )
                        // 看过 / 读过 / 听过 / 玩过
                        QuickCapsuleButton(
                            label = subjectType.actionCollect,
                            isActive = currentStatus == CollectionType.COLLECT,
                            activeColor = ActionCollect,
                            onClick = { onToggleCollection(CollectionType.COLLECT) },
                        )
                    }
                }
            }
        }
    }
}

/** 3 列高密度海报网格卡片 */
@Composable
fun SearchResultGridCard(
    subject: Subject,
    currentStatus: CollectionType?,
    query: String,
    onSubjectClick: (Long) -> Unit,
    onToggleDoing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectType = remember(subject.type) { SubjectType.fromValue(subject.type) }
    val typeTheme = remember(subjectType) { getSubjectTypeTheme(subjectType) }
    val primaryTitle = subject.displayName
    val primaryTitleAnnotated =
        remember(primaryTitle, query) {
            highlightKeywords(primaryTitle, query, HighlightAmber)
        }
    val rank = subject.rating?.rank ?: 0
    val score = subject.rating?.score ?: 0.0

    Card(
        onClick = { onSubjectClick(subject.id) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // 3:4 纵深海报
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    modifier = Modifier.fillMaxSize(),
                )

                // 左上角 Rank
                if (rank > 0) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        color = RatingGold,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = "#$rank",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.82f,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnRatingGold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // 右上角品类 Emoji
                Surface(
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    color = typeTheme.containerColor.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = subjectType.iconEmoji,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }

                // 右下角评分
                if (score > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(
                            text = "★ $score",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                            fontWeight = FontWeight.Bold,
                            color = RatingGoldBright,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            // 底部内容
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = primaryTitleAnnotated,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 快捷打卡单键（显示当前状态或一键在看/在读/在玩）
                val isDoing = currentStatus == CollectionType.DOING
                Surface(
                    onClick = onToggleDoing,
                    shape = RoundedCornerShape(6.dp),
                    color =
                        if (isDoing) {
                            ActionDoing
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            if (isDoing) {
                                "✓ ${subjectType.actionDoing}"
                            } else {
                                "+ ${subjectType.actionDoing}"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color =
                            if (isDoing) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** 1-Tap 快捷三态打卡小胶囊 */
@Composable
fun QuickCapsuleButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) activeColor.copy(alpha = 0.18f) else Color.Transparent,
        border =
            BorderStroke(
                0.8.dp,
                if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
        ) {
            if (isActive) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(
                text = label,
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 关键词高亮辅助工具函数 */
fun highlightKeywords(
    text: String,
    query: String,
    highlightColor: Color,
): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank() || !text.contains(trimmedQuery, ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = trimmedQuery.lowercase()
        val queryLength = lowerQuery.length

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex < 0) {
                append(text.substring(currentIndex))
                break
            }
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }
            val matchedPart = text.substring(matchIndex, matchIndex + queryLength)
            val startPos = length
            append(matchedPart)
            addStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.ExtraBold,
                    background = highlightColor.copy(alpha = 0.16f),
                ),
                startPos,
                length,
            )
            currentIndex = matchIndex + queryLength
        }
    }
}

data class SubjectTypeColorTheme(
    val containerColor: Color,
    val contentColor: Color,
)

fun getSubjectTypeTheme(subjectType: SubjectType): SubjectTypeColorTheme =
    when (subjectType) {
        SubjectType.BOOK ->
            SubjectTypeColorTheme(
                containerColor = TypeBookContainer,
                contentColor = OnTypeBook,
            )
        SubjectType.ANIME ->
            SubjectTypeColorTheme(
                containerColor = TypeAnimeContainer,
                contentColor = OnTypeAnime,
            )
        SubjectType.MUSIC ->
            SubjectTypeColorTheme(
                containerColor = TypeMusicContainer,
                contentColor = OnTypeMusic,
            )
        SubjectType.GAME ->
            SubjectTypeColorTheme(
                containerColor = TypeGameContainer,
                contentColor = OnTypeGame,
            )
        SubjectType.REAL ->
            SubjectTypeColorTheme(
                containerColor = TypeRealContainer,
                contentColor = OnTypeReal,
            )
    }

fun getSubjectTypeName(type: Int): String = SubjectType.fromValue(type).label
