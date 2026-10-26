package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectWatchStatus

/**
 * 条目详情全宽横排操作区：
 * 1. 左侧大播放胶囊（自适应占满，高 thumb reachability）；非剧集类为大收藏按钮；
 * 2. 右侧实体微操作圆盘群（40dp 大靶区，收藏、章节选择与分享）；
 * 3. 独立全宽横排，在 360dp 窄屏上绝无截断与挤压风险。
 */
@Composable
internal fun SubjectHeaderActionRow(
    subjectType: SubjectType,
    isEpisodeLike: Boolean,
    collection: UserCollection?,
    isLoggedIn: Boolean,
    watchStatus: SubjectWatchStatus,
    onPlayNext: (() -> Unit)?,
    onOpenEpisodePicker: (() -> Unit)?,
    onOpenCollectionSheet: (() -> Unit)?,
    onPromptLogin: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val actionBorder = if (isDark) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null
    val isCollected = collection != null
    val collectLabel =
        if (isCollected) {
            CollectionType.fromValue(collection.type).getVerb(subjectType)
        } else {
            stringResource(R.string.feature_subject_action_collect)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (isEpisodeLike) {
            val isUpcoming = watchStatus is SubjectWatchStatus.Upcoming
            val isCaughtUp = watchStatus is SubjectWatchStatus.CaughtUp
            val isCompleted = watchStatus is SubjectWatchStatus.Completed

            val playLabel =
                when (watchStatus) {
                    is SubjectWatchStatus.Upcoming -> {
                        val date = watchStatus.airDate.trim()
                        val shortDate = if (date.contains("-") && date.length >= 8) date.substringAfter("-") else date
                        if (shortDate.isNotBlank()) {
                            stringResource(R.string.feature_subject_action_upcoming_with_date, shortDate)
                        } else {
                            stringResource(R.string.feature_subject_action_upcoming)
                        }
                    }
                    is SubjectWatchStatus.ContinueWatching -> {
                        val epNumber = watchStatus.nextEpisode.episodeInt
                        val isFirstTime = (collection?.epStatus ?: 0) == 0
                        if (isFirstTime) {
                            if (epNumber > 0) {
                                stringResource(R.string.feature_subject_action_start_with_ep, epNumber)
                            } else {
                                stringResource(R.string.feature_subject_start_watching)
                            }
                        } else {
                            if (epNumber > 0) {
                                stringResource(R.string.feature_subject_action_continue_with_ep, epNumber)
                            } else {
                                stringResource(R.string.feature_subject_continue_watching)
                            }
                        }
                    }
                    is SubjectWatchStatus.CaughtUp -> {
                        val epNumber = watchStatus.lastWatchedEpisode?.episodeInt ?: 0
                        if (epNumber > 0) {
                            stringResource(R.string.feature_subject_action_caught_up_rewatch, epNumber)
                        } else {
                            stringResource(R.string.feature_subject_status_caught_up)
                        }
                    }
                    is SubjectWatchStatus.Completed ->
                        stringResource(R.string.feature_subject_rewatch_from_start)
                    SubjectWatchStatus.None ->
                        if ((collection?.epStatus ?: 0) > 0) {
                            stringResource(R.string.feature_subject_continue_watching)
                        } else {
                            stringResource(R.string.feature_subject_start_watching)
                        }
                }

            val playIcon =
                when {
                    isUpcoming -> BgmIcons.Schedule
                    isCaughtUp || isCompleted -> BgmIcons.Replay
                    else -> BgmIcons.PlayArrow
                }

            val isPlayEnabled = !isUpcoming && onPlayNext != null

            val (containerColor, contentColor) =
                when {
                    isUpcoming ->
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f) to
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    isCaughtUp || isCompleted ->
                        MaterialTheme.colorScheme.primaryContainer to
                            MaterialTheme.colorScheme.onPrimaryContainer
                    else ->
                        MaterialTheme.colorScheme.primary to
                            MaterialTheme.colorScheme.onPrimary
                }

            Button(
                onClick = { onPlayNext?.invoke() },
                enabled = isPlayEnabled,
                shape = CircleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                        disabledContainerColor = containerColor,
                        disabledContentColor = contentColor,
                    ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = playIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = playLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 追番 / 收藏
        if (!isEpisodeLike) {
            Button(
                onClick = {
                    if (!isLoggedIn) {
                        onPromptLogin?.invoke() ?: onOpenCollectionSheet?.invoke()
                    } else {
                        onOpenCollectionSheet?.invoke()
                    }
                },
                shape = CircleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (isCollected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        contentColor =
                            if (isCollected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = if (isCollected) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = collectLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            BgmCircleActionButton(
                onClick = {
                    if (!isLoggedIn) {
                        onPromptLogin?.invoke() ?: onOpenCollectionSheet?.invoke()
                    } else {
                        onOpenCollectionSheet?.invoke()
                    }
                },
                icon = if (isCollected) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                contentDescription = collectLabel,
                size = 40.dp,
                iconSize = 20.dp,
                border = actionBorder,
                contentColor =
                    if (isCollected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        // 分集选择入口不限定影视：书籍/音乐/游戏同样需要卷册、曲目和章节入口。
        if (onOpenEpisodePicker != null) {
            BgmCircleActionButton(
                onClick = onOpenEpisodePicker,
                icon = BgmIcons.FormatListNumbered,
                contentDescription =
                    stringResource(
                        when (subjectType) {
                            SubjectType.BOOK -> R.string.feature_subject_action_pick_volume
                            SubjectType.MUSIC -> R.string.feature_subject_action_pick_track
                            SubjectType.GAME -> R.string.feature_subject_action_pick_chapter
                            SubjectType.ANIME, SubjectType.REAL -> R.string.feature_subject_action_pick_episode
                        },
                    ),
                size = 40.dp,
                iconSize = 20.dp,
                border = actionBorder,
            )
        }

        // 分享
        if (onShare != null) {
            BgmCircleActionButton(
                onClick = onShare,
                icon = BgmIcons.Share,
                contentDescription = stringResource(R.string.feature_subject_share_title),
                size = 40.dp,
                iconSize = 20.dp,
                border = actionBorder,
            )
        }
    }
}
