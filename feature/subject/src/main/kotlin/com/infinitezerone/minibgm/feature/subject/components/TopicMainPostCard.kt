package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeContent
import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.model.TopicParentSubject

/**
 * 讨论帖主楼卡片（楼主原帖、关联番剧、主楼正文与表态）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicMainPostCard(
    topic: TopicDetail,
    onSubjectClick: (Long) -> Unit,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 帖子大标题
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 楼主信息栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncImage(
                    model =
                        topic.creator
                            ?.avatar
                            ?.bestAvatar
                            .orEmpty(),
                    contentDescription = topic.creator?.displayName,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.creator?.displayName ?: "未知用户",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (topic.createdAt > 0) {
                        Text(
                            text = TimeUtils.formatEpochSecondsToDate(topic.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = "#1 楼主",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // 关联条目横幅（若属于条目讨论）
            topic.subject?.let { subject ->
                TopicLinkedSubjectBanner(
                    subject = subject,
                    onClick = { onSubjectClick(subject.id) },
                )
            }

            // 主楼正文（BBCode 原生渲染）
            val mainContent = topic.mainPost?.content.orEmpty()
            if (mainContent.isNotBlank()) {
                BgmBbCodeContent(
                    content = mainContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    onUrlClick = onUrlClick,
                )
            }

            // 主楼点赞反应（Reactions）
            val reactions = topic.mainPost?.reactions.orEmpty()
            if (reactions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    reactions.forEach { reaction ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "❤️ ${reaction.count}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 讨论帖关联番剧精简引导横幅
 */
@Composable
private fun TopicLinkedSubjectBanner(
    subject: TopicParentSubject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = subject.images?.bestImage.orEmpty(),
                contentDescription = subject.displayName,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(36.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                subject.rating?.let { rating ->
                    if (rating.score > 0.0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "★ ${rating.score}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "查看条目详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
