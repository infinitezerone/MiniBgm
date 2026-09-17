package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

/**
 * 季度新番导视 2:3 黄金比例海报展板卡片
 */
@Composable
fun SeasonalAnimeCard(
    subject: Subject,
    isWished: Boolean,
    isDoing: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleCollection: (Long, CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val primaryTitle = subject.displayName
    val rating = subject.rating
    val score = rating?.score ?: 0.0
    val airDate = subject.date.ifBlank { subject.airDate }

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = subject.id,
                    initialName = primaryTitle,
                    initialCoverUrl = subject.images?.bestImage.orEmpty(),
                    initialScore = score,
                    source = "seasonal_guide",
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 2:3 黄金比例封面海报区
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
                    aspectRatio = 2f / 3f,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, "seasonal_guide"),
                                clipInOverlayDuringTransition = RoundedCornerShape(10.dp),
                            ),
                )

                // 评分徽章（左上角）
                if (score > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 10.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.TopStart),
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

                // 快捷收藏/追番悬浮轻量按钮（右上角）
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 10.dp),
                    color = Color.Black.copy(alpha = 0.60f),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleCollection(
                                subject.id,
                                if (isWished) CollectionType.DOING else CollectionType.WISH,
                            )
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector =
                                when {
                                    isDoing -> Icons.Filled.Check
                                    isWished -> Icons.Filled.Bookmark
                                    else -> Icons.Filled.BookmarkBorder
                                },
                            contentDescription =
                                if (isDoing) {
                                    "在看"
                                } else if (isWished) {
                                    "想看"
                                } else {
                                    "标记想看"
                                },
                            tint =
                                when {
                                    isDoing -> MaterialTheme.colorScheme.primaryContainer
                                    isWished -> RatingGold
                                    else -> Color.White
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // 2. 标题与信息元数据区
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = airDate.ifBlank { "放送日期待定" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (subject.eps > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${subject.eps}话",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. 底部 1-Tap 快捷追番按钮
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val targetType = if (isDoing) CollectionType.WISH else CollectionType.DOING
                        onToggleCollection(subject.id, targetType)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor =
                                if (isDoing) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            contentColor =
                                if (isDoing) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = if (isDoing) Icons.Filled.Check else Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            if (isDoing) {
                                "在追"
                            } else if (isWished) {
                                "想看"
                            } else {
                                "追番"
                            },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isDoing) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
