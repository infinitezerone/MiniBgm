package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ScoreBadge
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.ActionCollect
import com.infinitezerone.minibgm.core.designsystem.theme.ActionDoing
import com.infinitezerone.minibgm.core.designsystem.theme.ActionWish
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.HighlightAmber
import com.infinitezerone.minibgm.core.designsystem.theme.OnRatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.highlightContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onHighlightContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onTypeAnimeColor
import com.infinitezerone.minibgm.core.designsystem.theme.onTypeBookColor
import com.infinitezerone.minibgm.core.designsystem.theme.onTypeGameColor
import com.infinitezerone.minibgm.core.designsystem.theme.onTypeMusicColor
import com.infinitezerone.minibgm.core.designsystem.theme.onTypeRealColor
import com.infinitezerone.minibgm.core.designsystem.theme.typeAnimeContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.typeBookContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.typeGameContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.typeMusicContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.typeRealContainerColor
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

/** 高质感详细卡片（多品类自适应徽章、关键词高亮、度量适配与 1-Tap 快捷三态打卡） */
@Composable
fun SearchResultCard(
    subject: Subject,
    currentStatus: CollectionType?,
    query: String,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleCollection: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectType = remember(subject.type) { SubjectType.fromValue(subject.type) }
    val typeTheme = getSubjectTypeTheme(subjectType)

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

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = subject.id,
                    initialName = primaryTitle,
                    initialCoverUrl = subject.images?.bestImage.orEmpty(),
                    initialScore = rating?.score ?: 0.0,
                    source = "search_list",
                ),
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 封面（承载共享元素 + 评分角标，与瀑布流卡片一致）
            Box(
                modifier =
                    Modifier
                        .width(74.dp)
                        .bgmSharedElement(
                            key = BgmSharedElementKeys.subjectCover(subject.id, "search_list"),
                            clipInOverlayDuringTransition = RoundedCornerShape(8.dp),
                        ),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    cornerRadius = 8.dp,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    modifier = Modifier.fillMaxWidth(),
                )

                ScoreBadge(
                    score = rating?.score ?: 0.0,
                    modifier = Modifier.align(Alignment.BottomStart),
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                    starSize = 9.dp,
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
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
                            color = highlightContainerColor(),
                        ) {
                            Text(
                                text = "Rank #$rank",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                fontWeight = FontWeight.ExtraBold,
                                color = onHighlightContainerColor(),
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
                            text = subjectType.label,
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

                // 4. 1-Tap 追番三态胶囊（右对齐）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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

/** 3 列高密度海报网格卡片 */
@Composable
fun SearchResultGridCard(
    subject: Subject,
    currentStatus: CollectionType?,
    query: String,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleDoing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectType = remember(subject.type) { SubjectType.fromValue(subject.type) }
    val typeTheme = getSubjectTypeTheme(subjectType)
    val primaryTitle = subject.displayName
    val primaryTitleAnnotated =
        remember(primaryTitle, query) {
            highlightKeywords(primaryTitle, query, HighlightAmber)
        }
    val rank = subject.rating?.rank ?: 0
    val score = subject.rating?.score ?: 0.0

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = subject.id,
                    initialName = primaryTitle,
                    initialCoverUrl = subject.images?.bestImage.orEmpty(),
                    initialScore = score,
                    source = "search_grid",
                ),
            )
        },
        shape = RoundedCornerShape(8.dp),
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
                        .bgmSharedElement(
                            key = BgmSharedElementKeys.subjectCover(subject.id, "search_grid"),
                            clipInOverlayDuringTransition = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        ).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
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
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
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

                // 右上角品类徽章
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = typeTheme.containerColor.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = subjectType.label,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                        color = typeTheme.contentColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }

                // 右下角评分
                ScoreBadge(
                    score = score,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    shape = RoundedCornerShape(topStart = 8.dp),
                    starSize = 10.dp,
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                )
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
                    shape = RoundedCornerShape(8.dp),
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
        shape = RoundedCornerShape(8.dp),
        color =
            if (isActive) {
                activeColor.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            if (isActive) {
                BorderStroke(0.8.dp, activeColor)
            } else {
                null
            },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
        ) {
            if (isActive) {
                Icon(
                    imageVector = BgmIcons.Check,
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

@Composable
fun getSubjectTypeTheme(subjectType: SubjectType): SubjectTypeColorTheme =
    when (subjectType) {
        SubjectType.BOOK ->
            SubjectTypeColorTheme(
                containerColor = typeBookContainerColor(),
                contentColor = onTypeBookColor(),
            )
        SubjectType.ANIME ->
            SubjectTypeColorTheme(
                containerColor = typeAnimeContainerColor(),
                contentColor = onTypeAnimeColor(),
            )
        SubjectType.MUSIC ->
            SubjectTypeColorTheme(
                containerColor = typeMusicContainerColor(),
                contentColor = onTypeMusicColor(),
            )
        SubjectType.GAME ->
            SubjectTypeColorTheme(
                containerColor = typeGameContainerColor(),
                contentColor = onTypeGameColor(),
            )
        SubjectType.REAL ->
            SubjectTypeColorTheme(
                containerColor = typeRealContainerColor(),
                contentColor = onTypeRealColor(),
            )
    }

fun getSubjectTypeName(type: Int): String = SubjectType.fromValue(type).label
