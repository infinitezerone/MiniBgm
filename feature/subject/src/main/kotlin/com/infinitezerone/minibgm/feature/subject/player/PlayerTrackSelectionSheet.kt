package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.media3.common.Tracks
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import java.util.Locale

/**
 * 画面清晰度选择模式：自动自适应（ABR）或手动锁定指定分辨率。
 */
enum class QualityMode {
    AUTO,
    SPECIFIC,
}

/**
 * 字幕选择模式：关闭、自动根据系统语言选择、或手动选定指定字幕轨。
 */
enum class SubtitleMode {
    OFF,
    AUTO,
    SPECIFIC,
}

/**
 * 视频清晰度选项视图模型。
 */
data class PlayerQualityOption(
    val id: String,
    val name: String,
    val detail: String?,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val isSelected: Boolean,
    val group: Tracks.Group? = null,
    val trackIndex: Int = 0,
)

/**
 * 播放器轨道项视图模型（字幕/音轨）。
 */
data class PlayerTrackOption(
    val id: String,
    val name: String,
    val isSelected: Boolean,
    val group: Tracks.Group,
    val trackIndex: Int,
)

/**
 * 播放器当前音视频与清晰度轨道状态快照。
 */
data class PlayerTracksSnapshot(
    val qualityTracks: List<PlayerQualityOption> = emptyList(),
    val qualityMode: QualityMode = QualityMode.AUTO,
    val currentAutoQualityName: String? = null,
    val currentVideoSizeQualityName: String? = null,
    val subtitleTracks: List<PlayerTrackOption> = emptyList(),
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val subtitleMode: SubtitleMode = SubtitleMode.AUTO,
    val hasSelectableTracks: Boolean = false,
) {
    /**
     * 控制栏清晰度小药丸显示的文案（如 "1080P" 或 "自动 (1080P)"）。
     */
    val currentQualityDisplayLabel: String?
        get() =
            when {
                qualityTracks.size > 1 -> {
                    when (qualityMode) {
                        QualityMode.SPECIFIC -> qualityTracks.firstOrNull { it.isSelected }?.name ?: "画质"
                        QualityMode.AUTO -> currentAutoQualityName?.let { "自动 ($it)" } ?: "自动"
                    }
                }
                qualityTracks.isNotEmpty() -> qualityTracks.first().name
                currentVideoSizeQualityName != null -> currentVideoSizeQualityName
                else -> null
            }
}

/**
 * 格式化视频分辨率标签（如 "1080P"、"720P"）。
 */
fun formatQualityLabel(
    width: Int,
    height: Int,
): String =
    when {
        height >= 2160 || width >= 3840 -> "4K"
        height >= 1440 || width >= 2560 -> "2K"
        height >= 1080 || width >= 1920 -> "1080P"
        height >= 720 || width >= 1280 -> "720P"
        height >= 480 || width >= 848 -> "480P"
        height >= 360 -> "360P"
        height > 0 -> "${height}P"
        else -> "未知画质"
    }

/**
 * 格式化视频清晰度详细信息（如 "1920×1080 · 3.5 Mbps"）。
 */
fun formatQualityDetail(
    width: Int,
    height: Int,
    bitrate: Int,
): String? {
    val parts = mutableListOf<String>()
    if (width > 0 && height > 0) {
        parts.add("$width×$height")
    }
    if (bitrate > 0) {
        val mbps = bitrate.toFloat() / 1_000_000f
        if (mbps >= 1f) {
            parts.add(String.format(Locale.US, "%.1f Mbps", mbps))
        } else {
            parts.add("${bitrate / 1000} kbps")
        }
    }
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else null
}

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
 * 从 Media3 [Player] 动态提取画质、音轨与字幕轨状态快照。
 */
fun extractPlayerTracksSnapshot(player: Player): PlayerTracksSnapshot {
    val currentTracks = player.currentTracks
    val params = player.trackSelectionParameters
    val isTextDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val hasTextOverride = params.overrides.any { it.key.type == C.TRACK_TYPE_TEXT }
    val hasVideoOverride = params.overrides.any { it.key.type == C.TRACK_TYPE_VIDEO }

    val subtitleMode =
        when {
            isTextDisabled -> SubtitleMode.OFF
            hasTextOverride -> SubtitleMode.SPECIFIC
            else -> SubtitleMode.AUTO
        }

    val qualityMode = if (hasVideoOverride) QualityMode.SPECIFIC else QualityMode.AUTO

    val qualityOptions = mutableListOf<PlayerQualityOption>()
    val subtitleOptions = mutableListOf<PlayerTrackOption>()
    val audioOptions = mutableListOf<PlayerTrackOption>()

    for (group in currentTracks.groups) {
        val trackType = group.type
        when (trackType) {
            C.TRACK_TYPE_VIDEO -> {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val trackId = "video_${group.mediaTrackGroup.id}_$i"
                    val name = formatQualityLabel(format.width, format.height)
                    val detail = formatQualityDetail(format.width, format.height, format.bitrate)

                    qualityOptions.add(
                        PlayerQualityOption(
                            id = trackId,
                            name = name,
                            detail = detail,
                            width = format.width,
                            height = format.height,
                            bitrate = format.bitrate,
                            isSelected = isSelected,
                            group = group,
                            trackIndex = i,
                        ),
                    )
                }
            }
            C.TRACK_TYPE_TEXT -> {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val trackId = "text_${group.mediaTrackGroup.id}_$i"
                    val name = formatTrackDisplayName(format, trackType, i, "字幕")

                    subtitleOptions.add(
                        PlayerTrackOption(
                            id = trackId,
                            name = name,
                            isSelected = isSelected,
                            group = group,
                            trackIndex = i,
                        ),
                    )
                }
            }
            C.TRACK_TYPE_AUDIO -> {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val trackId = "audio_${group.mediaTrackGroup.id}_$i"
                    val name = formatTrackDisplayName(format, trackType, i, "音轨")

                    audioOptions.add(
                        PlayerTrackOption(
                            id = trackId,
                            name = name,
                            isSelected = isSelected,
                            group = group,
                            trackIndex = i,
                        ),
                    )
                }
            }
        }
    }

    // 画质选项按分辨率与码率从高到低排列
    qualityOptions.sortWith(
        compareByDescending<PlayerQualityOption> { it.height }
            .thenByDescending { it.bitrate },
    )

    val currentAutoQualityName = qualityOptions.firstOrNull { it.isSelected }?.name
    val currentVideoSizeQualityName =
        if (player.videoSize.height > 0) {
            formatQualityLabel(player.videoSize.width, player.videoSize.height)
        } else {
            null
        }
    val hasSelectable = qualityOptions.isNotEmpty() || subtitleOptions.isNotEmpty() || audioOptions.size > 1

    return PlayerTracksSnapshot(
        qualityTracks = qualityOptions,
        qualityMode = qualityMode,
        currentAutoQualityName = currentAutoQualityName,
        currentVideoSizeQualityName = currentVideoSizeQualityName,
        subtitleTracks = subtitleOptions,
        audioTracks = audioOptions,
        subtitleMode = subtitleMode,
        hasSelectableTracks = hasSelectable,
    )
}

/**
 * 播放器「播放设置」面板：对照成熟视频 App（B 站/YouTube）的设置抽屉，
 * 在轨道三组（清晰度/字幕/音轨）之外收拢倍速、画面比例、弹幕与单集循环——
 * 这些能力此前散落在控制栏单键循环或完全没有入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTrackSelectionSheet(
    isOpen: Boolean,
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    playbackSpeed: Float = 1f,
    onSelectSpeed: (Float) -> Unit = {},
    resizeMode: PlayerResizeMode = PlayerResizeMode.FIT,
    onSelectResizeMode: (PlayerResizeMode) -> Unit = {},
    isDanmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    onOpenDanmakuPicker: (() -> Unit)? = null,
    isLooping: Boolean = false,
    onToggleLoop: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    // 横竖屏统一走底部弹层（面板经 BgmOverlayHost 承接，不依赖舞台层级）
    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        PlayerTrackSelectionContent(
            tracksSnapshot = tracksSnapshot,
            onSelectQuality = onSelectQuality,
            onAutoQuality = onAutoQuality,
            onSelectSubtitle = onSelectSubtitle,
            onDisableSubtitles = onDisableSubtitles,
            onAutoSubtitles = onAutoSubtitles,
            onSelectAudio = onSelectAudio,
            playbackSpeed = playbackSpeed,
            onSelectSpeed = onSelectSpeed,
            resizeMode = resizeMode,
            onSelectResizeMode = onSelectResizeMode,
            isDanmakuEnabled = isDanmakuEnabled,
            onToggleDanmaku = onToggleDanmaku,
            onOpenDanmakuPicker = onOpenDanmakuPicker,
            isLooping = isLooping,
            onToggleLoop = onToggleLoop,
            onClose = onDismiss,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/**
 * 画质、音轨与字幕选择内容面板。
 */
@Composable
internal fun PlayerTrackSelectionContent(
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    playbackSpeed: Float = 1f,
    onSelectSpeed: (Float) -> Unit = {},
    resizeMode: PlayerResizeMode = PlayerResizeMode.FIT,
    onSelectResizeMode: (PlayerResizeMode) -> Unit = {},
    isDanmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    onOpenDanmakuPicker: (() -> Unit)? = null,
    isLooping: Boolean = false,
    onToggleLoop: () -> Unit = {},
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
            // 1. 画面清晰度分组（多码率流可用时）
            if (tracksSnapshot.qualityTracks.size > 1) {
                item {
                    Text(
                        text = stringResource(R.string.feature_subject_player_quality_group),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }

                item {
                    val autoSubtitle =
                        tracksSnapshot.currentAutoQualityName?.let {
                            stringResource(R.string.feature_subject_player_quality_auto_current, it)
                        }
                    TrackItemRow(
                        title = stringResource(R.string.feature_subject_player_quality_auto),
                        subtitle = autoSubtitle,
                        isSelected = tracksSnapshot.qualityMode == QualityMode.AUTO,
                        isDarkThemed = isDarkThemed,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAutoQuality()
                        },
                    )
                }

                items(tracksSnapshot.qualityTracks, key = { it.id }) { quality ->
                    TrackItemRow(
                        title = quality.name,
                        subtitle = quality.detail,
                        isSelected = tracksSnapshot.qualityMode == QualityMode.SPECIFIC && quality.isSelected,
                        isDarkThemed = isDarkThemed,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectQuality(quality)
                        },
                    )
                }
            } else if (tracksSnapshot.qualityTracks.size == 1 || tracksSnapshot.currentVideoSizeQualityName != null) {
                item {
                    Text(
                        text = stringResource(R.string.feature_subject_player_quality_group),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                item {
                    val single = tracksSnapshot.qualityTracks.firstOrNull()
                    val title = single?.name ?: tracksSnapshot.currentVideoSizeQualityName.orEmpty()
                    val subtitle = single?.detail
                    TrackItemRow(
                        title = title,
                        subtitle = subtitle,
                        isSelected = true,
                        isDarkThemed = isDarkThemed,
                        onClick = {},
                    )
                }
            }

            // 1.5 播放速度分组（原控制栏单键循环入口在此收拢为可选网格）
            item {
                Text(
                    text = stringResource(R.string.feature_subject_player_speed_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SPEED_OPTIONS.forEach { speed ->
                        SpeedPill(
                            label = formatSpeedLabel(speed),
                            isSelected = playbackSpeed == speed,
                            isDarkThemed = isDarkThemed,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectSpeed(speed)
                            },
                        )
                    }
                }
            }

            // 1.75 画面比例分组（原控制栏单键循环入口在此收拢为可选行）
            item {
                Text(
                    text = stringResource(R.string.feature_subject_player_resize_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerResizeMode.entries.forEach { mode ->
                        SpeedPill(
                            label = stringResource(mode.labelRes),
                            isSelected = resizeMode == mode,
                            isDarkThemed = isDarkThemed,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectResizeMode(mode)
                            },
                        )
                    }
                }
            }

            // 2. 软字幕分组
            item {
                Text(
                    text = stringResource(R.string.feature_subject_player_subtitles_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
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

            // 3. 音频轨道分组
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

            // 3. 弹幕分组（开关 + 分集纠正入口，入口即关闭本面板转而挂起候选浮层）
            item {
                Text(
                    text = stringResource(R.string.feature_subject_player_danmaku_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            item {
                ToggleRow(
                    title = stringResource(R.string.feature_subject_player_danmaku_show),
                    isChecked = isDanmakuEnabled,
                    isDarkThemed = isDarkThemed,
                    onToggle = onToggleDanmaku,
                )
            }
            if (onOpenDanmakuPicker != null) {
                item {
                    TrackItemRow(
                        title = stringResource(R.string.feature_subject_player_danmaku_pick_episode),
                        isSelected = false,
                        isDarkThemed = isDarkThemed,
                        onClick = onOpenDanmakuPicker,
                    )
                }
            }

            // 4. 单集循环
            item {
                ToggleRow(
                    title = stringResource(R.string.feature_subject_player_loop),
                    isChecked = isLooping,
                    isDarkThemed = isDarkThemed,
                    onToggle = onToggleLoop,
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** 播放速度候选档位（与 B 站一致：0.5x–2.0x） */
internal val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 倍速展示：整档保留一位小数（1.0x），非整档去尾零（0.75x、1.25x） */
internal fun formatSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) {
        String.format(Locale.US, "%.1fx", speed)
    } else {
        // %.2f 去尾零再拼 x：0.5x / 0.75x / 1.25x（%.2g 的两位有效数字会把 1.25 变 1.3）
        String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "x"
    }

/**
 * 药丸单选（倍速/画面比例共用）：选中态用主题色描边加粗，未选中弱化
 */
@Composable
private fun SpeedPill(
    label: String,
    isSelected: Boolean,
    isDarkThemed: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val activeBg = if (isDarkThemed) activeColor.copy(alpha = 0.22f) else activeColor.copy(alpha = 0.12f)
    val inactiveBg = if (isDarkThemed) Color.White.copy(alpha = 0.05f) else Color.Transparent
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeBg else inactiveBg,
        border = if (isSelected) BorderStroke(1.dp, activeColor.copy(alpha = 0.6f)) else null,
        onClick = onClick,
        modifier = Modifier.size(width = 52.dp, height = 36.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (isSelected) {
                        if (isDarkThemed) Color.White else MaterialTheme.colorScheme.primary
                    } else if (isDarkThemed) {
                        Color.White.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
            )
        }
    }
}

/** 开关行（弹幕/单集循环共用） */
@Composable
private fun ToggleRow(
    title: String,
    isChecked: Boolean,
    isDarkThemed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDarkThemed) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = isChecked, onCheckedChange = { onToggle() })
    }
}

/**
 * 轨道选项单行
 */
@Composable
private fun TrackItemRow(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    isDarkThemed: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
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
            Column(modifier = Modifier.weight(1f)) {
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
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isDarkThemed) {
                                Color.White.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
