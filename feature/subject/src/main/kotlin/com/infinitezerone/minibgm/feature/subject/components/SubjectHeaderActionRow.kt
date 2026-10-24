package com.infinitezerone.minibgm.feature.subject.components

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
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectWatchStatus

/**
 * 条目详情全宽横排操作区：
 * 1. 左侧大播放胶囊（自适应占满，高 thumb reachability）；非剧集类为大收藏按钮；
 * 2. 右侧实体微操作圆盘群（40dp 大靶区，M3 视觉与微光轮廓）；
 * 3. 独立全宽横排，在 360dp 窄屏上绝无截断与挤压风险。
 */
@Composable
internal fun SubjectHeaderActionRow(
    subjectType: SubjectType,
    isEpisodeLike: Boolean,
    collection: UserCollection?,
    totalEpisodes: Int,
    isLoggedIn: Boolean,
    watchStatus: SubjectWatchStatus,
    nextEpSort: Float?,
    onPlayNext: (() -> Unit)?,
    onOpenSources: (() -> Unit)?,
    onIncrementWatched: (() -> Unit)?,
    onOpenCollectionSheet: (() -> Unit)?,
    onPromptLogin: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
            val playLabel =
                when (watchStatus) {
                    is SubjectWatchStatus.ContinueWatching ->
                        stringResource(R.string.feature_subject_resume_watch, watchStatus.nextEpisode.formattedNumber)
                    is SubjectWatchStatus.Completed ->
                        stringResource(R.string.feature_subject_rewatch_from_start)
                    else ->
                        if (nextEpSort != null && nextEpSort > 0f) {
                            stringResource(R.string.feature_subject_play_episode_format, nextEpSort.toEpisodeLabel())
                        } else {
                            stringResource(R.string.feature_subject_cd_play)
                        }
                }

            Button(
                onClick = { onPlayNext?.invoke() ?: onOpenSources?.invoke() },
                enabled = onPlayNext != null || onOpenSources != null,
                shape = CircleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.PlayArrow,
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
                containerColor =
                    if (isCollected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (isDark) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                contentColor =
                    if (isCollected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        // 打卡 +1
        if (isEpisodeLike) {
            val canCheckIn = totalEpisodes > 0 || (collection?.epStatus ?: 0) > 0
            BgmCircleActionButton(
                onClick = {
                    if (!isLoggedIn) {
                        onPromptLogin?.invoke() ?: onIncrementWatched?.invoke()
                    } else {
                        onIncrementWatched?.invoke()
                    }
                },
                icon = BgmIcons.Check,
                contentDescription = stringResource(R.string.feature_subject_check_in_next_ep),
                enabled = canCheckIn && onIncrementWatched != null,
                size = 40.dp,
                iconSize = 20.dp,
                containerColor =
                    if (isDark) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 换源
        if (isEpisodeLike && onOpenSources != null) {
            BgmCircleActionButton(
                onClick = onOpenSources,
                icon = BgmIcons.Tv,
                contentDescription = stringResource(R.string.feature_subject_ep_sources),
                size = 40.dp,
                iconSize = 20.dp,
                containerColor =
                    if (isDark) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
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
                containerColor =
                    if (isDark) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
