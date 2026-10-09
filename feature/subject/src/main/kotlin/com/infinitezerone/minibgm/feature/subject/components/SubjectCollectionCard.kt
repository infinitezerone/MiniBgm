package com.infinitezerone.minibgm.feature.subject.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.subject.R
import kotlin.math.roundToInt

private val STATUS_TYPES =
    listOf(
        CollectionType.WISH,
        CollectionType.DOING,
        CollectionType.COLLECT,
        CollectionType.ON_HOLD,
        CollectionType.DROPPED,
    )

/**
 * 个人追番与进度看板卡片：
 * 位于 Header 卡片正下方，作为用户与作品关系的第一交互触点：
 * 1. 顶部：追番状态、个人评分与详细编辑入口；
 * 2. 中部：5 状态一键快速切换胶囊排（想看 / 在看 / 看过 / 搁置 / 抛弃）；
 * 3. 底部（有进度或在看时）：观看进度条、-1/+1 快捷打卡按钮与续看播放直达入口。
 */
@Composable
fun SubjectCollectionCard(
    collection: UserCollection?,
    totalEpisodes: Int,
    subjectType: SubjectType,
    isLoggedIn: Boolean,
    onOpenCollectionSheet: () -> Unit,
    onUpdateCollectionStatus: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
    nextEpSort: Float? = null,
    onPlayNext: (() -> Unit)? = null,
    onIncrementWatched: (() -> Unit)? = null,
    onDecrementWatched: (() -> Unit)? = null,
    onPromptLogin: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. 顶部栏：标题 + 评分徽章 + 详细编辑/评分按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (collection != null) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    val statusText =
                        if (collection != null) {
                            CollectionType.fromValue(collection.type).getVerb(subjectType)
                        } else {
                            stringResource(R.string.feature_subject_collection_card_title)
                        }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (collection != null && collection.rate > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RatingGold.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_subject_rating_score_stars, collection.rate.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RatingGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (isLoggedIn) {
                    FilledTonalButton(
                        onClick = onOpenCollectionSheet,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text =
                                if (collection !=
                                    null
                                ) {
                                    stringResource(R.string.feature_subject_btn_edit_rating)
                                } else {
                                    stringResource(R.string.feature_subject_btn_add_collection)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (!isLoggedIn) {
                // 未登录提示卡
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_login_to_sync),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onPromptLogin?.invoke() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_subject_action_login),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                // 2. 5 状态一键胶囊排（想看 / 在看 / 看过 / 搁置 / 抛弃）
                val currentType = collection?.let { CollectionType.fromValue(it.type) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    STATUS_TYPES.forEach { type ->
                        val isSelected = currentType == type
                        val verb = type.getVerb(subjectType)
                        Surface(
                            onClick = {
                                if (isSelected) {
                                    onOpenCollectionSheet()
                                } else {
                                    onUpdateCollectionStatus(type)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            modifier = Modifier.weight(1f).height(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = verb,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // 3. 追番进度管理与快捷打卡区（剧集类有分集或已收藏时展现）
                val currentEp = collection?.epStatus ?: 0
                val hasProgressSection =
                    totalEpisodes > 0 ||
                        currentEp > 0 ||
                        collection?.type == CollectionType.DOING.value ||
                        collection?.type == CollectionType.COLLECT.value

                if (hasProgressSection &&
                    (subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL || subjectType == SubjectType.BOOK)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )

                    // 进度文字与续看播放按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val progressPercent =
                            if (totalEpisodes > 0) {
                                ((currentEp.toFloat() / totalEpisodes.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                        val progressLabel =
                            if (totalEpisodes > 0) {
                                stringResource(
                                    R.string.feature_subject_progress_watched_with_total,
                                    currentEp,
                                    totalEpisodes,
                                    progressPercent,
                                )
                            } else if (currentEp > 0) {
                                stringResource(R.string.feature_subject_progress_watched_only, currentEp)
                            } else {
                                stringResource(R.string.feature_subject_progress_not_started)
                            }

                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (onPlayNext != null) {
                            val playLabel =
                                if (nextEpSort != null && nextEpSort > 0) {
                                    stringResource(R.string.feature_subject_resume_watch, nextEpSort.toEpisodeLabel())
                                } else {
                                    stringResource(R.string.feature_subject_cd_play)
                                }
                            FilledTonalButton(
                                onClick = onPlayNext,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Play,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = playLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // 进度条
                    if (totalEpisodes > 0) {
                        val progressFraction = (currentEp.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }

                    // 步退与快捷打卡按钮
                    if (onIncrementWatched != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onDecrementWatched != null) {
                                FilledTonalButton(
                                    onClick = onDecrementWatched,
                                    enabled = currentEp > 0,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Remove,
                                        contentDescription = stringResource(R.string.feature_subject_step_back_one_ep),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "-1",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            val canIncrement = totalEpisodes <= 0 || currentEp < totalEpisodes
                            Button(
                                onClick = onIncrementWatched,
                                enabled = canIncrement,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.PlusOne,
                                    contentDescription = stringResource(R.string.feature_subject_check_in_next_ep),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val incrementText =
                                    if (canIncrement) {
                                        stringResource(R.string.feature_subject_check_in_ep_format, currentEp + 1)
                                    } else {
                                        stringResource(R.string.feature_subject_reached_latest_ep)
                                    }
                                Text(
                                    text = incrementText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
