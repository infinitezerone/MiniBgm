package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import java.util.Locale

/**
 * 字幕选择模式：关闭、自动根据系统语言选择、或手动选定指定字幕轨。
 */
enum class SubtitleMode {
    OFF,
    AUTO,
    SPECIFIC,
}

/**
 * 播放器轨道项视图模型。
 */
data class PlayerTrackOption(
    val id: String,
    val name: String,
    val isSelected: Boolean,
    val group: Tracks.Group,
    val trackIndex: Int,
)

/**
 * 播放器当前音视频轨道状态快照。
 */
data class PlayerTracksSnapshot(
    val subtitleTracks: List<PlayerTrackOption> = emptyList(),
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val subtitleMode: SubtitleMode = SubtitleMode.AUTO,
    val hasSelectableTracks: Boolean = false,
)

/**
 * 将 ISO 语言代码解析为友好本地化语言名称。
 */
fun resolveLanguageName(languageCode: String?): String? {
    if (languageCode.isNullOrBlank() || languageCode.equals("und", ignoreCase = true)) return null
    val clean = languageCode.lowercase().trim()
    return when {
        clean in listOf("zh", "chi", "zho", "zh-cn", "zh-hans", "chs") -> "中文 (简体)"
        clean in listOf("zh-tw", "zh-hk", "zh-hant", "cht") -> "中文 (繁体)"
        clean in listOf("ja", "jpn", "jp") -> "日语"
        clean in listOf("en", "eng") -> "英语"
        clean in listOf("ko", "kor") -> "韩语"
        clean in listOf("fr", "fra", "fre") -> "法语"
        clean in listOf("de", "deu", "ger") -> "德语"
        clean in listOf("es", "spa") -> "西班牙语"
        clean in listOf("ru", "rus") -> "俄语"
        else -> {
            try {
                val loc = Locale.forLanguageTag(languageCode)
                val display = loc.getDisplayName(Locale.SIMPLIFIED_CHINESE)
                if (display.isNotBlank() && !display.equals(clean, ignoreCase = true)) display else null
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/**
 * 格式化音轨或字幕轨的友好展示名称。
 */
fun formatTrackDisplayName(
    format: Format,
    trackType: Int,
    index: Int,
    defaultLabel: String,
): String =
    formatTrackDisplayDetails(
        label = format.label,
        language = format.language,
        channelCount = format.channelCount,
        selectionFlags = format.selectionFlags,
        trackType = trackType,
        index = index,
        defaultLabel = defaultLabel,
    )

/**
 * 纯 Kotlin 格式化音轨或字幕轨参数，解耦 Android 运行时以便纯 JVM 单测。
 */
fun formatTrackDisplayDetails(
    label: String?,
    language: String?,
    channelCount: Int,
    selectionFlags: Int,
    trackType: Int,
    index: Int,
    defaultLabel: String,
): String {
    val langName = resolveLanguageName(language)
    val rawLabel = label?.trim()?.takeIf { it.isNotBlank() }

    val baseName =
        when {
            rawLabel != null && langName != null -> {
                if (rawLabel.contains(langName, ignoreCase = true)) rawLabel else "$langName ($rawLabel)"
            }
            rawLabel != null -> rawLabel
            langName != null -> langName
            else -> "$defaultLabel #${index + 1}"
        }

    val suffix = StringBuilder()
    if (trackType == C.TRACK_TYPE_AUDIO && channelCount > 0) {
        val channelStr =
            when (channelCount) {
                1 -> "单声道"
                2 -> "双声道"
                6 -> "5.1 环绕声"
                8 -> "7.1 环绕声"
                else -> "${channelCount}声道"
            }
        suffix.append(" · ").append(channelStr)
    }

    if ((selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0) {
        suffix.append(" [默认]")
    }
    if ((selectionFlags and C.SELECTION_FLAG_FORCED) != 0) {
        suffix.append(" [强制]")
    }

    return "$baseName$suffix"
}

/**
 * 从 Media3 [Player] 动态提取音轨与字幕轨状态快照。
 */
fun extractPlayerTracksSnapshot(player: Player): PlayerTracksSnapshot {
    val currentTracks = player.currentTracks
    val params = player.trackSelectionParameters
    val isTextDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val hasTextOverride = params.overrides.any { it.key.type == C.TRACK_TYPE_TEXT }

    val subtitleMode =
        when {
            isTextDisabled -> SubtitleMode.OFF
            hasTextOverride -> SubtitleMode.SPECIFIC
            else -> SubtitleMode.AUTO
        }

    val subtitleOptions = mutableListOf<PlayerTrackOption>()
    val audioOptions = mutableListOf<PlayerTrackOption>()

    for (group in currentTracks.groups) {
        val trackType = group.type
        if (trackType != C.TRACK_TYPE_TEXT && trackType != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val isSelected = group.isTrackSelected(i)
            val trackId = "${trackType}_${group.mediaTrackGroup.id}_$i"
            val defaultLabel = if (trackType == C.TRACK_TYPE_TEXT) "字幕" else "音轨"
            val name = formatTrackDisplayName(format, trackType, i, defaultLabel)

            val option =
                PlayerTrackOption(
                    id = trackId,
                    name = name,
                    isSelected = isSelected,
                    group = group,
                    trackIndex = i,
                )

            if (trackType == C.TRACK_TYPE_TEXT) {
                subtitleOptions.add(option)
            } else {
                audioOptions.add(option)
            }
        }
    }

    val hasSelectable = subtitleOptions.isNotEmpty() || audioOptions.size > 1

    return PlayerTracksSnapshot(
        subtitleTracks = subtitleOptions,
        audioTracks = audioOptions,
        subtitleMode = subtitleMode,
        hasSelectableTracks = hasSelectable,
    )
}

/**
 * 切换字幕为指定轨道。
 */
fun selectSubtitleTrack(
    player: Player,
    track: PlayerTrackOption,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
            .build()
}

/**
 * 关闭字幕。
 */
fun disableSubtitles(player: Player) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
}

/**
 * 自动选择字幕（遵循 ExoPlayer 与系统首选语言配置）。
 */
fun autoSelectSubtitles(player: Player) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
}

/**
 * 切换音轨为指定轨道。
 */
fun selectAudioTrack(
    player: Player,
    track: PlayerTrackOption,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
            .build()
}

/**
 * 播放器音视频与软字幕选择器。
 * 竖屏采用 Material 3 [BgmModalBottomSheet]，横屏采用右侧抽屉式滑入面板，避免横屏切入切出 Dialog 造成闪烁。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTrackSelectionSheet(
    isOpen: Boolean,
    isLandscape: Boolean,
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    if (isLandscape) {
        // 横屏：由于处于沉浸式全屏，在舞台右侧使用无缝抽屉，避免任何 Window 切换开销
        // 外部在 Box 作用域内调用渲染
    } else {
        BgmModalBottomSheet(
            onDismissRequest = onDismiss,
        ) {
            PlayerTrackSelectionContent(
                tracksSnapshot = tracksSnapshot,
                onSelectSubtitle = onSelectSubtitle,
                onDisableSubtitles = onDisableSubtitles,
                onAutoSubtitles = onAutoSubtitles,
                onSelectAudio = onSelectAudio,
                onClose = onDismiss,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/**
 * 横屏专用的右侧抽屉滑入组件（需置于播放器 Stage 的根 Box 中）。
 */
@Composable
internal fun BoxScope.PlayerTrackSelectionLandscapeDrawer(
    isOpen: Boolean,
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(durationMillis = 200)),
        exit = fadeOut(tween(durationMillis = 200)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        enter =
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 250),
            ),
        exit =
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 200),
            ),
        modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerTrackSelectionContent(
                tracksSnapshot = tracksSnapshot,
                onSelectSubtitle = onSelectSubtitle,
                onDisableSubtitles = onDisableSubtitles,
                onAutoSubtitles = onAutoSubtitles,
                onSelectAudio = onSelectAudio,
                onClose = onClose,
                isDarkThemed = true,
                modifier =
                    Modifier
                        .displayCutoutPadding()
                        .navigationBarsPadding()
                        .statusBarsPadding(),
            )
        }
    }
}

/**
 * 音轨与字幕选择内容面板。
 */
@Composable
private fun PlayerTrackSelectionContent(
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkThemed: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = BgmIcons.Subtitles,
                    contentDescription = null,
                    tint = if (isDarkThemed) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_tracks_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkThemed) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = BgmIcons.Close,
                    contentDescription = stringResource(R.string.feature_subject_player_close_drawer),
                    tint = if (isDarkThemed) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            color = if (isDarkThemed) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 1. 软字幕分组
            item {
                Text(
                    text = stringResource(R.string.feature_subject_player_subtitles_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }

            item {
                TrackItemRow(
                    title = stringResource(R.string.feature_subject_player_subtitles_off),
                    isSelected = tracksSnapshot.subtitleMode == SubtitleMode.OFF,
                    isDarkThemed = isDarkThemed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDisableSubtitles()
                    },
                )
            }

            item {
                TrackItemRow(
                    title = stringResource(R.string.feature_subject_player_subtitles_auto),
                    isSelected = tracksSnapshot.subtitleMode == SubtitleMode.AUTO,
                    isDarkThemed = isDarkThemed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAutoSubtitles()
                    },
                )
            }

            items(tracksSnapshot.subtitleTracks, key = { it.id }) { track ->
                TrackItemRow(
                    title = track.name,
                    isSelected = tracksSnapshot.subtitleMode == SubtitleMode.SPECIFIC && track.isSelected,
                    isDarkThemed = isDarkThemed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectSubtitle(track)
                    },
                )
            }

            // 2. 音频轨道分组
            item {
                Text(
                    text = stringResource(R.string.feature_subject_player_audio_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }

            items(tracksSnapshot.audioTracks, key = { it.id }) { track ->
                TrackItemRow(
                    title = track.name,
                    isSelected = track.isSelected,
                    isDarkThemed = isDarkThemed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectAudio(track)
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 轨道选项单行
 */
@Composable
private fun TrackItemRow(
    title: String,
    isSelected: Boolean,
    isDarkThemed: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = if (isDarkThemed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val activeBg = if (isDarkThemed) activeColor.copy(alpha = 0.22f) else activeColor.copy(alpha = 0.12f)
    val inactiveBg = if (isDarkThemed) Color.White.copy(alpha = 0.05f) else Color.Transparent

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeBg else inactiveBg,
        border = if (isSelected) BorderStroke(1.dp, activeColor.copy(alpha = 0.6f)) else null,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    when {
                        isSelected -> activeColor
                        isDarkThemed -> Color.White.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = BgmIcons.Check,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
