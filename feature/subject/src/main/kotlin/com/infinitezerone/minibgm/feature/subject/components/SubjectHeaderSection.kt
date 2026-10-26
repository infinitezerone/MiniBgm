package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.ratingTextColor
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectWatchStatus
import kotlin.math.roundToInt

/**
 * 紧凑现代条目头部（首屏高效生产力，高度约 200dp，保留水生弥散光晕与 M3 视觉风格）：
 * 1. 左侧圆角海报（104dp 宽，3:4 大画幅比例，14dp 圆角，背景弥散微光，共享元素锚点）；
 * 2. 右侧紧凑信息栏：大标题、原名、类型/开播年份/总集数、评分金徽章、全站 Rank、主播放 CTA 大胶囊与快捷微操作；
 * 3. 个人追番进度条与 -1/+1 快捷微调（登录且有在看/进度时自然融入）；
 * 4. 核心制作人员与智能可折叠简介。
 */
@Composable
fun SubjectHeaderCard(
    subject: Subject,
    subjectType: SubjectType,
    modifier: Modifier = Modifier,
    sharedElementSource: String = "",
    collection: UserCollection? = null,
    totalEpisodes: Int = 0,
    isLoggedIn: Boolean = false,
    episodes: List<Episode> = emptyList(),
    onSelectEpisode: ((Episode) -> Unit)? = null,
    onOpenCollectionSheet: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onOpenEpisodePicker: (() -> Unit)? = null,
    onIncrementWatched: (() -> Unit)? = null,
    onDecrementWatched: (() -> Unit)? = null,
    onPromptLogin: (() -> Unit)? = null,
    onUpdateCollectionStatus: ((CollectionType) -> Unit)? = null,
    watchStatus: SubjectWatchStatus = SubjectWatchStatus.None,
    onShare: (() -> Unit)? = null,
    onTitleBoundsChanged: (Rect) -> Unit = {},
) {
    val isEpisodeLike = subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL
    // 明暗判定走当前生效色阶：应用支持强制亮/暗主题覆盖，isSystemInDarkTheme() 会与之相悖
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // 1. 顶部图文紧凑混排：左侧海报 + 右侧标题与操作
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧海报与弥散光晕
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            ) {
                // 背景微光晕
                Box(
                    modifier =
                        Modifier
                            .size(width = 114.dp, height = 156.dp)
                            .background(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = if (isDark) 0.28f else 0.16f,
                                                ),
                                                MaterialTheme.colorScheme.tertiary.copy(
                                                    alpha = if (isDark) 0.12f else 0.06f,
                                                ),
                                                Color.Transparent,
                                            ),
                                    ),
                                shape = RoundedCornerShape(16.dp),
                            ),
                )

                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = subject.displayName,
                    cornerRadius = 16.dp,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    modifier =
                        Modifier
                            .width(104.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false,
                            ).bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, sharedElementSource),
                                clipInOverlayDuringTransition = RoundedCornerShape(16.dp),
                            ),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 右侧信息栏
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // 标题与顶栏折叠测算
                Text(
                    text = subject.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { onTitleBoundsChanged(it.boundsInWindow()) },
                )

                if (subject.name.isNotBlank() && subject.name != subject.displayName) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 评分与 Rank 行
                val rating = subject.rating
                if (rating != null && rating.score > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RatingGold.copy(alpha = 0.15f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Star,
                                    contentDescription = null,
                                    tint = RatingGold,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = rating.score.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ratingTextColor(),
                                )
                            }
                        }

                        if (rating.rank > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "Rank #${rating.rank}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 元信息胶囊（类型、日期、集数）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = subjectType.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    val dateText = subject.date.ifBlank { subject.airDate }
                    if (dateText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    val episodeCount = if (subject.eps > 0) subject.eps else subject.totalEpisodes
                    if (episodeCount > 0 && subjectType != SubjectType.GAME) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.feature_subject_total_episodes_format,
                                        episodeCount,
                                        subjectType.unitName,
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // 核心制作阵容提要（如：动画制作、监督/导演）
                    val keyStaff = subject.keyStaff
                    if (keyStaff.isNotEmpty()) {
                        keyStaff.take(2).forEach { (role, name) ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = "$role: $name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 全宽横排操作区（由独立组件 SubjectHeaderActionRow 承接）
        SubjectHeaderActionRow(
            subjectType = subjectType,
            isEpisodeLike = isEpisodeLike,
            collection = collection,
            isLoggedIn = isLoggedIn,
            watchStatus = watchStatus,
            onPlayNext = onPlayNext,
            onOpenEpisodePicker = onOpenEpisodePicker.takeIf { totalEpisodes > 0 || episodes.isNotEmpty() } ?: onOpenEpisodePicker,
            onOpenCollectionSheet = onOpenCollectionSheet,
            onPromptLogin = onPromptLogin,
            onShare = onShare,
        )

        // 2. 个人追番进度自然流（无多余灰框，极简呼吸感）
        if (isLoggedIn && (collection != null || totalEpisodes > 0)) {
            val currentEp = collection?.epStatus ?: 0
            val progressFraction =
                if (totalEpisodes > 0) {
                    (currentEp.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            val progressPercent = (progressFraction * 100f).roundToInt()

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val progressLabel =
                            when (watchStatus) {
                                is SubjectWatchStatus.Upcoming ->
                                    if (watchStatus.airDate.isNotBlank()) {
                                        stringResource(R.string.feature_subject_status_upcoming_format, watchStatus.airDate)
                                    } else {
                                        stringResource(R.string.feature_subject_status_upcoming)
                                    }
                                is SubjectWatchStatus.CaughtUp ->
                                    if (watchStatus.nextAirDate != null) {
                                        stringResource(R.string.feature_subject_status_caught_up_with_next, watchStatus.nextAirDate)
                                    } else {
                                        stringResource(R.string.feature_subject_status_caught_up)
                                    }
                                is SubjectWatchStatus.Completed ->
                                    stringResource(R.string.feature_subject_status_completed)
                                else ->
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
                            }

                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val actionBorder =
                                if (isDark) {
                                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                } else {
                                    null
                                }

                            if (isEpisodeLike && onDecrementWatched != null && currentEp > 0) {
                                BgmCircleActionButton(
                                    onClick = onDecrementWatched,
                                    size = 28.dp,
                                    border = actionBorder,
                                ) {
                                    Text(
                                        text = "-1",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            if (isEpisodeLike && onIncrementWatched != null) {
                                BgmCircleActionButton(
                                    onClick = onIncrementWatched,
                                    icon = BgmIcons.Check,
                                    contentDescription = stringResource(R.string.feature_subject_check_in_next_ep),
                                    size = 28.dp,
                                    iconSize = 16.dp,
                                    border = actionBorder,
                                )
                            }
                        }
                    }

                    if (totalEpisodes > 0) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}
