package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import java.util.Locale

/**
 * 格式化时间毫秒为 mm:ss 或 hh:mm:ss 格式
 */
internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * 嗅探加载中视图
 */
@Composable
internal fun PlayerResolvingView(
    sourceName: String,
    attempt: Int = 0,
    attemptTotal: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        ResolvingIndicator(sourceName = sourceName, attempt = attempt, attemptTotal = attemptTotal)
    }
}

/**
 * 切换源/切集时的半透明嗅探遮罩：叠在**仍在播放**的旧画面上，而不是整块黑，
 * 让用户看得到“正在换源”而不只是“卡住”。
 */
@Composable
internal fun PlayerResolvingOverlay(
    sourceName: String,
    attempt: Int = 0,
    attemptTotal: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        ResolvingIndicator(
            sourceName = sourceName,
            attempt = attempt,
            attemptTotal = attemptTotal,
            prefix = stringResource(R.string.feature_subject_player_switching_source),
        )
    }
}

@Composable
private fun ResolvingIndicator(
    sourceName: String,
    attempt: Int,
    attemptTotal: Int,
    prefix: String = stringResource(R.string.feature_subject_player_sniffing_from_prefix),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp),
        )
        val sniffing =
            if (attemptTotal > 0) {
                stringResource(R.string.feature_subject_player_sniffing_stream_with_attempt, attempt, attemptTotal)
            } else {
                stringResource(R.string.feature_subject_player_sniffing_stream)
            }
        Text(
            text = "$prefix【$sourceName】$sniffing",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

/**
 * 剧集标题与看过标记
 */
@Composable
internal fun PlayerHeaderInfo(
    subjectName: String,
    episodeTitle: String,
    isWatched: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = subjectName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isWatched) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_player_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 屡试屡败的源提示
 */
@Composable
internal fun SourceFailureHint(failureCount: Int) {
    if (failureCount <= 0) return
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(R.string.feature_subject_player_failures_count, failureCount),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * 暂无直链时的友好占位与向导入口
 */
@Composable
internal fun PlayerEmptyView(
    subjectName: String,
    epLabel: String,
    errorMessage: String? = null,
    onBackClick: () -> Unit,
    onRetry: () -> Unit = {},
    onNextSource: (() -> Unit)? = null,
    onRequestOpenSources: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = BgmIcons.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp),
            )
            Text(
                text =
                    if (errorMessage != null) {
                        stringResource(R.string.feature_subject_player_unresolved_stream)
                    } else {
                        stringResource(R.string.feature_subject_player_no_online_stream)
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text =
                    if (errorMessage != null) {
                        errorMessage
                    } else {
                        stringResource(R.string.feature_subject_player_no_stream_hint, subjectName, epLabel)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBackClick) {
                    Text(stringResource(R.string.feature_subject_cd_back), color = Color.White)
                }
                if (errorMessage != null) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.feature_subject_player_retry_sniff))
                    }
                    if (onNextSource != null) {
                        OutlinedButton(onClick = onNextSource) {
                            Text(stringResource(R.string.feature_subject_player_switch_next_source), color = Color.White)
                        }
                    }
                } else if (onRequestOpenSources != null) {
                    Button(onClick = onRequestOpenSources) {
                        Text(stringResource(R.string.feature_subject_source_ai_search))
                    }
                }
            }
        }
    }
}

/**
 * 将技术向的规则名称（如域名 cj.lziapi.com）清洗为用户友好的线路名（如 线路 1 · lziapi），
 * 若规则本身已有中文或自定义友好名称则予以保留。
 */
@Composable
internal fun formatSourceDisplayName(
    rawName: String,
    index: Int,
    isDirect: Boolean,
): String {
    if (isDirect) return rawName
    val trimmed = rawName.trim()
    val isDomainOrUrl = trimmed.contains(".") || trimmed.startsWith("http://") || trimmed.startsWith("https://")
    if (!isDomainOrUrl) {
        return trimmed.ifBlank {
            stringResource(R.string.feature_subject_player_source_fallback_format, index + 1)
        }
    }
    val host =
        trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
    val cleanHost =
        host
            .removePrefix("www.")
            .removePrefix("cj.")
            .removePrefix("api.")
            .substringBefore(".")
    return if (cleanHost.isNotBlank()) {
        stringResource(R.string.feature_subject_player_source_named_format, index + 1, cleanHost)
    } else {
        stringResource(R.string.feature_subject_player_source_fallback_format, index + 1)
    }
}

