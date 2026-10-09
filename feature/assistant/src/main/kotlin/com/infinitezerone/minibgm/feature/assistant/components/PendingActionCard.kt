package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_PORTRAIT_ASPECT_RATIO
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.feature.assistant.ActionStatus
import com.infinitezerone.minibgm.feature.assistant.PendingActionCardState
import com.infinitezerone.minibgm.feature.assistant.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 待确认写操作交互卡片。
 * 遵循 Material 3 Expressive 设计规范与 MiniBgm 主题 Token。
 */
@Composable
fun PendingActionCard(
    cardState: PendingActionCardState,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = cardState.action

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            // 顶部：拟定更新提示与状态指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feature_assistant_pending_proposed),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    Text(
                        text =
                            if (action is PendingAction.ImportPlaybackRules) {
                                stringResource(R.string.feature_assistant_pending_confirm_import)
                            } else {
                                stringResource(R.string.feature_assistant_pending_confirm_progress)
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 状态指示标
                when (cardState.status) {
                    ActionStatus.PENDING -> {
                        Text(
                            text = stringResource(R.string.feature_assistant_pending_status_waiting),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ActionStatus.EXECUTING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_assistant_pending_status_submitting),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    ActionStatus.SUCCESS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = BgmIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_assistant_pending_status_synced),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    ActionStatus.REJECTED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = BgmIcons.Cancel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_assistant_pending_status_cancelled),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    ActionStatus.FAILED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = BgmIcons.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_assistant_pending_status_failed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 变更条目详情展示：封面 + 结构化摘要
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (action is PendingAction.ImportPlaybackRules) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = BgmIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = action.sourceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    val fallbackTitle =
                        stringResource(R.string.feature_assistant_pending_subject_number, action.subjectId)
                    val quotedTitle =
                        stringResource(
                            R.string.feature_assistant_pending_subject_quoted,
                            action.subjectTitle.trim(),
                        )
                    val displayTitle =
                        remember(action.subjectTitle, fallbackTitle, quotedTitle) {
                            val clean = action.subjectTitle.trim()
                            when {
                                clean.isBlank() -> fallbackTitle
                                clean.startsWith("《") && clean.endsWith("》") -> clean
                                else -> quotedTitle
                            }
                        }

                    CoverImage(
                        url = action.coverUrl,
                        contentDescription = displayTitle,
                        cornerRadius = 8.dp,
                        aspectRatio = BGM_PORTRAIT_ASPECT_RATIO,
                        modifier =
                            Modifier
                                .width(40.dp)
                                .clickable { onSubjectClick(action.subjectId) },
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onSubjectClick(action.subjectId) },
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        when (action) {
                            is PendingAction.UpdateEpisode -> {
                                val statusLabel =
                                    if (action.isWatched) {
                                        stringResource(R.string.feature_assistant_pending_mark_watched)
                                    } else {
                                        stringResource(R.string.feature_assistant_pending_mark_unwatched)
                                    }
                                Text(
                                    text =
                                        stringResource(
                                            R.string.feature_assistant_pending_episode_line,
                                            statusLabel,
                                            action.episodeNumber,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is PendingAction.UpdateCollection -> {
                                val parts =
                                    mutableListOf(
                                        stringResource(
                                            R.string.feature_assistant_pending_mark_collection,
                                            action.collectionType.label,
                                        ),
                                    )
                                action.rating?.let {
                                    parts.add(stringResource(R.string.feature_assistant_pending_rating, it))
                                }
                                if (action.isPrivate) {
                                    parts.add(stringResource(R.string.feature_assistant_pending_private))
                                }
                                Text(
                                    text = parts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!action.comment.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.feature_assistant_pending_comment,
                                                action.comment.orEmpty(),
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            is PendingAction.ImportPlaybackRules -> Unit
                        }
                    }
                }
            }

            // 错误提示（若执行失败）
            if (cardState.status == ActionStatus.FAILED && !cardState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.feature_assistant_pending_fail_reason,
                            cardState.errorMessage.orEmpty(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 操作按钮组
            when (cardState.status) {
                ActionStatus.PENDING -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = { onReject(action.actionId) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(DesignSystemR.string.core_designsystem_action_cancel),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onApprove(action.actionId) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.feature_assistant_pending_action_confirm),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                ActionStatus.FAILED -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(
                            onClick = { onApprove(action.actionId) },
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                stringResource(R.string.feature_assistant_pending_action_retry),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                ActionStatus.EXECUTING, ActionStatus.SUCCESS, ActionStatus.REJECTED -> Unit
            }
        }
    }
}
