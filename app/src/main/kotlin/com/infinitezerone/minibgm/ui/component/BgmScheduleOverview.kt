package com.infinitezerone.minibgm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.R
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillSize
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillVariant
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** 宽屏 list-detail 的 Schedule 默认详情页：有内容可做，而不是一块空占位。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BgmScheduleOverview(
    recentHistory: List<PlaybackHistory>,
    onPlayHistory: ((PlaybackHistory) -> Unit)?,
    onOpenSubject: (PlaybackHistory) -> Unit,
    onOpenSeasonalGuide: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAssistant: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // bgm.tv 播出时刻与排期全链路按 CST 计算，总览的「今天」必须同源（对齐 ScheduleViewModel 口径）
    val cstZone = ZoneId.of("Asia/Shanghai")
    // 当日锚定：本组件常驻宽屏详情位，跨午夜自动翻到新的一天
    val today by
        produceState(initialValue = LocalDate.now(cstZone), cstZone) {
            while (isActive) {
                val now = ZonedDateTime.now(cstZone)
                val nextDayStart = now.toLocalDate().plusDays(1).atStartOfDay(cstZone)
                delay(Duration.between(now, nextDayStart).toMillis() + 1_000)
                value = LocalDate.now(cstZone)
            }
        }
    val gradientEndY = with(LocalDensity.current) { 460.dp.toPx() }
    val dateText = stringResource(R.string.app_overview_today_date_format, today.monthValue, today.dayOfMonth)
    val weekdayText = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dateLabel = "$dateText · $weekdayText"

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            ),
                        endY = gradientEndY,
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BgmSpacing.xxxl, vertical = BgmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(BgmSpacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BgmSpacing.xs)) {
                Text(
                    text = stringResource(R.string.app_overview_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.app_overview_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recentHistory.isNotEmpty()) {
                ContinueWatchingCard(
                    history = recentHistory.first(),
                    onPlayHistory = onPlayHistory,
                    onOpenSubject = onOpenSubject,
                )

                val recent = recentHistory.drop(1).take(2)
                if (recent.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.app_overview_recent_history))
                    if (recent.size == 1) {
                        RecentHistoryRow(
                            history = recent.first(),
                            onClick = { onOpenSubject(recent.first()) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(BgmSpacing.md)) {
                            recent.forEach { history ->
                                RecentHistoryRow(
                                    history = history,
                                    onClick = { onOpenSubject(history) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            } else {
                EmptyOverviewCard()
            }

            SectionTitle(stringResource(R.string.app_overview_quick_actions))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BgmSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BgmSpacing.sm),
            ) {
                BgmPillButton(
                    text = stringResource(R.string.app_overview_seasonal_guide),
                    icon = BgmIcons.CalendarBorder,
                    size = BgmPillSize.Compact,
                    variant = BgmPillVariant.Secondary,
                    onClick = onOpenSeasonalGuide,
                )
                BgmPillButton(
                    text = stringResource(R.string.app_overview_search),
                    icon = BgmIcons.SearchBorder,
                    size = BgmPillSize.Compact,
                    variant = BgmPillVariant.Secondary,
                    onClick = onOpenSearch,
                )
                if (onOpenAssistant != null) {
                    BgmPillButton(
                        text = stringResource(R.string.app_overview_assistant),
                        icon = BgmIcons.Assistant,
                        size = BgmPillSize.Compact,
                        variant = BgmPillVariant.Secondary,
                        onClick = onOpenAssistant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ContinueWatchingCard(
    history: PlaybackHistory,
    onPlayHistory: ((PlaybackHistory) -> Unit)?,
    onOpenSubject: (PlaybackHistory) -> Unit,
) {
    val progress =
        if (history.durationMs > 0L) {
            (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
    val shape = RoundedCornerShape(24.dp)
    val heroBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                    MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(heroBrush),
    ) {
        Row(
            modifier = Modifier.padding(BgmSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = history.coverUrl,
                contentDescription = history.subjectName,
                cornerRadius = 12.dp,
                modifier = Modifier.width(96.dp),
            )
            Spacer(modifier = Modifier.width(BgmSpacing.xl))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BgmSpacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.app_overview_continue_watching),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = history.subjectName.ifBlank { stringResource(R.string.app_subject_fallback_title) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = history.episodeLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (history.durationMs > 0L) {
                        Text(
                            text =
                                stringResource(
                                    R.string.app_overview_progress_format,
                                    formatDuration(history.positionMs),
                                    formatDuration(history.durationMs),
                                ),
                            style = BgmContentTokens.Typography.tabularCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    BgmPillButton(
                        text = stringResource(R.string.app_overview_resume),
                        icon = BgmIcons.PlayArrow,
                        size = BgmPillSize.Compact,
                        onClick = {
                            if (onPlayHistory != null) {
                                onPlayHistory(history)
                            } else {
                                onOpenSubject(history)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentHistoryRow(
    history: PlaybackHistory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(BgmSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = history.coverUrl,
                contentDescription = history.subjectName,
                cornerRadius = 8.dp,
                modifier = Modifier.width(48.dp),
            )
            Spacer(modifier = Modifier.width(BgmSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BgmSpacing.xxs),
            ) {
                Text(
                    text = history.subjectName.ifBlank { stringResource(R.string.app_subject_fallback_title) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = history.episodeLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyOverviewCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(BgmSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BgmSpacing.sm),
        ) {
            Icon(
                imageVector = BgmIcons.CalendarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.app_overview_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.app_overview_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackHistory.episodeLabel(): String {
    val episode =
        stringResource(
            R.string.app_overview_episode_format,
            episodeSort.toInt().coerceAtLeast(1),
        )
    return if (episodeName.isBlank()) episode else "$episode · $episodeName"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
}
