package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ScoreBadge
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

/**
 * 季度片单 2:3 黄金比例海报展板卡片
 */
@Composable
fun SeasonalAnimeCard(
    subject: Subject,
    isWished: Boolean,
    isDoing: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleCollection: (Long, CollectionType) -> Unit,
    modifier: Modifier = Modifier,
    isContinuing: Boolean = false,
    continuingEpisodeText: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val primaryTitle = subject.displayName
    val rating = subject.rating
    val score = rating?.score ?: 0.0
    val airDate = subject.date.ifBlank { subject.airDate }

    Card(
        onClick = {
            if (subject.id > 0) {
                onSubjectClick(
                    SubjectDetailRoute(
                        subjectId = subject.id,
                        initialName = primaryTitle,
                        initialCoverUrl = subject.images?.bestImage.orEmpty(),
                        initialScore = score,
                        source = "seasonal_guide",
                    ),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
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
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, "seasonal_guide"),
                                clipInOverlayDuringTransition = RoundedCornerShape(8.dp),
                            ),
                )

                // 评分徽章（左上角）
                ScoreBadge(
                    score = score,
                    modifier = Modifier.align(Alignment.TopStart),
                    shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                )

                // 跨季在播角标（右上角）
                if (isContinuing) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Text(
                            text = continuingEpisodeText ?: "跨季在播",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // 2. 标题与信息元数据区
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val displayDate =
                        if (isContinuing) {
                            continuingEpisodeText ?: "跨季在播"
                        } else {
                            airDate.ifBlank { "待定" }
                        }
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isContinuing) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontWeight = if (isContinuing) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (subject.eps > 0) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${subject.eps}话",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                            .height(28.dp),
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
                            .PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = if (isDoing) BgmIcons.Check else BgmIcons.AddBorder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text =
                            if (isDoing) {
                                "在追"
                            } else if (isWished) {
                                "想看"
                            } else {
                                "追番"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isDoing) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
