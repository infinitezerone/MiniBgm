package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectWatchStatus
import kotlin.math.roundToInt

/**
 * 电影级大画幅沉浸头部：
 * 1. 顶部大画幅圆角海报（140dp，14dp 圆角，背景弥散微光，共享元素飞渡锚点）；
 * 2. 醒目大标题、原名与多维元信息胶囊（类型、开播年份、集数、评分金徽章、全站 Rank）；
 * 3. 醒目主播放 CTA 大胶囊按钮（播放/续看第 X 话）；
 * 4. 紧凑副级操作条（追番、打卡+1、换源、分享）；
 * 5. 个人追番进度条与 -1/+1 快捷微调（登录且有在看/进度时自然融入，告别双重卡片堆叠）；
 * 6. 核心制作人员与智能可折叠简介。
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
    onOpenCollectionSheet: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onOpenSources: (() -> Unit)? = null,
    onIncrementWatched: (() -> Unit)? = null,
    onDecrementWatched: (() -> Unit)? = null,
    onPromptLogin: (() -> Unit)? = null,
    onUpdateCollectionStatus: ((CollectionType) -> Unit)? = null,
    nextEpSort: Float? = null,
    watchStatus: SubjectWatchStatus = SubjectWatchStatus.None,
    onShare: (() -> Unit)? = null,
    onTitleBoundsChanged: (Rect) -> Unit = {},
) {
    var isSummaryExpanded by rememberSaveable { mutableStateOf(false) }
    val isEpisodeLike = subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL
    // 明暗判定走当前生效色阶：应用支持强制亮/暗主题覆盖，isSystemInDarkTheme() 会与之相悖
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        // 1. 电影感立体大画幅海报 + 弥散微光
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        ) {
            // 背景弥散光晕
            Box(
                modifier =
                    Modifier
                        .size(width = 176.dp, height = 246.dp)
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
                            shape = RoundedCornerShape(24.dp),
                        ),
            )

            // 悬浮封面
            CoverImage(
                url = subject.images?.bestImage.orEmpty(),
                contentDescription = subject.displayName,
                cornerRadius = 16.dp,
                aspectRatio = BGM_POSTER_ASPECT_RATIO,
                modifier =
                    Modifier
                        .width(140.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            clip = false,
                        ).bgmSharedElement(
                            key = BgmSharedElementKeys.subjectCover(subject.id, sharedElementSource),
                            clipInOverlayDuringTransition = RoundedCornerShape(16.dp),
                        ),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. 醒目大标题与原名
        Text(
            text = subject.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .onGloballyPositioned { onTitleBoundsChanged(it.boundsInWindow()) },
        )

        if (subject.name.isNotBlank() && subject.name != subject.displayName) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subject.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 元信息胶囊（类型徽章、放送/发行日期、总集数、评分金徽章、全站 Rank）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
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
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
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
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            val rating = subject.rating
            if (rating != null && rating.score > 0.0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = RatingGold.copy(alpha = 0.15f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Star,
                            contentDescription = null,
                            tint = RatingGold,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = rating.score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = RatingGold,
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        // 4. 醒目主播放 CTA 大胶囊按钮（剧集类首要转化入口）
        if (isEpisodeLike) {
            Spacer(modifier = Modifier.height(16.dp))

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
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. 紧凑副级操作条 (追番、打卡+1、换源、分享)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // a. 追番 / 收藏
            val isCollected = collection != null
            val collectLabel =
                if (isCollected) {
                    CollectionType.fromValue(collection.type).getVerb(subjectType)
                } else {
                    stringResource(R.string.feature_subject_action_collect)
                }
            HeaderActionButton(
                icon = if (isCollected) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                label = collectLabel,
                contentDescription = collectLabel,
                isHighlighted = isCollected,
                onClick = {
                    if (!isLoggedIn) {
                        onPromptLogin?.invoke() ?: onOpenCollectionSheet?.invoke()
                    } else {
                        onOpenCollectionSheet?.invoke()
                    }
                },
            )

            // b. 打卡 +1（剧集类作品且有分集时可用）
            if (isEpisodeLike) {
                val canCheckIn = totalEpisodes > 0 || (collection?.epStatus ?: 0) > 0
                HeaderActionButton(
                    icon = BgmIcons.Check,
                    label = stringResource(R.string.feature_subject_action_check_in),
                    contentDescription = stringResource(R.string.feature_subject_check_in_next_ep),
                    enabled = canCheckIn && onIncrementWatched != null,
                    onClick = {
                        if (!isLoggedIn) {
                            onPromptLogin?.invoke() ?: onIncrementWatched?.invoke()
                        } else {
                            onIncrementWatched?.invoke()
                        }
                    },
                )
            }

            // c. 换源 / 多源（剧集类作品入口）
            if (isEpisodeLike && onOpenSources != null) {
                HeaderActionButton(
                    icon = BgmIcons.Tv,
                    label = stringResource(R.string.feature_subject_ep_sources),
                    contentDescription = stringResource(R.string.feature_subject_ep_sources),
                    onClick = onOpenSources,
                )
            }

            // d. 分享
            if (onShare != null) {
                HeaderActionButton(
                    icon = BgmIcons.Share,
                    label = stringResource(R.string.feature_subject_action_share),
                    contentDescription = stringResource(R.string.feature_subject_share_title),
                    onClick = onShare,
                )
            }
        }

        // 6. 个人追番进度条与 -1/+1 快捷微调（登录且有在看/进度时自然融入，告别双重卡片堆叠）
        if (isLoggedIn && (collection != null || totalEpisodes > 0)) {
            val currentEp = collection?.epStatus ?: 0
            val progressFraction =
                if (totalEpisodes > 0) {
                    (currentEp.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            val progressPercent = (progressFraction * 100f).roundToInt()

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (isEpisodeLike && onDecrementWatched != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    onClick = onDecrementWatched,
                                    enabled = currentEp > 0,
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.height(26.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = "-1",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color =
                                                if (currentEp > 0) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                },
                                        )
                                    }
                                }
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
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
            }
        }

        // 7. 核心制作人员
        val staffHighlights = subject.keyStaff.take(2)
        if (staffHighlights.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val staffText = staffHighlights.joinToString(separator = "  ·  ") { (role, name) -> "$role: $name" }
            Text(
                text = staffText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // 8. 智能段落折叠简介
        val trimmedSummary = remember(subject.summary) { subject.summary.trim() }
        if (trimmedSummary.isNotBlank()) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            )

            val summaryBlocks =
                remember(trimmedSummary) {
                    trimmedSummary.split(Regex("(?:\r?\n){2,}")).map { it.trim() }.filter { it.isNotEmpty() }
                }
            val hasMultiParagraphs = summaryBlocks.size > 1
            val firstParagraph = summaryBlocks.firstOrNull().orEmpty()
            val isSingleLongBlock =
                !hasMultiParagraphs &&
                    (trimmedSummary.length > 120 || trimmedSummary.count { it == '\n' } >= 3)
            val canExpand = hasMultiParagraphs || isSingleLongBlock

            val displayText =
                when {
                    !canExpand -> trimmedSummary
                    isSummaryExpanded -> trimmedSummary
                    hasMultiParagraphs -> firstParagraph
                    else -> trimmedSummary
                }

            val maxLines =
                if (isSummaryExpanded || !canExpand || hasMultiParagraphs) {
                    Int.MAX_VALUE
                } else {
                    4
                }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            )

            if (canExpand) {
                val expandLabel =
                    when {
                        isSummaryExpanded -> stringResource(R.string.feature_subject_collapse_summary)
                        hasMultiParagraphs -> stringResource(R.string.feature_subject_expand_summary_with_original)
                        else -> stringResource(R.string.feature_subject_expand_summary)
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isSummaryExpanded = !isSummaryExpanded }
                            .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = expandLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (isSummaryExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** 头部 40dp 实体圆盘操作项（包含微胶囊文字说明） */
@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isHighlighted: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        BgmCircleActionButton(
            onClick = onClick,
            icon = icon,
            contentDescription = contentDescription,
            enabled = enabled,
            size = 40.dp,
            iconSize = 20.dp,
            containerColor =
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f)
                    isHighlighted -> MaterialTheme.colorScheme.primaryContainer
                    isDark -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surface
                },
            contentColor =
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color =
                if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
