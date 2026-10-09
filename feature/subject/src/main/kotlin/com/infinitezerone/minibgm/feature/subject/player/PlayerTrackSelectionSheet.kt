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
    val group: Tracks.Group,
    val trackIndex: Int,
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
    val subtitleTracks: List<PlayerTrackOption> = emptyList(),
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val subtitleMode: SubtitleMode = SubtitleMode.AUTO,
    val hasSelectableTracks: Boolean = false,
) {
    /**
     * 控制栏清晰度小药丸显示的文案（如 "1080P" 或 "自动"）。
     */
    val currentQualityDisplayLabel: String
        get() =
            when (qualityMode) {
                QualityMode.SPECIFIC -> qualityTracks.firstOrNull { it.isSelected }?.name ?: "画质"
                QualityMode.AUTO -> currentAutoQualityName?.let { "自动 ($it)" } ?: "自动"
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
    val hasSelectable = qualityOptions.size > 1 || subtitleOptions.isNotEmpty() || audioOptions.size > 1

    return PlayerTracksSnapshot(
        qualityTracks = qualityOptions,
        qualityMode = qualityMode,
        currentAutoQualityName = currentAutoQualityName,
        subtitleTracks = subtitleOptions,
        audioTracks = audioOptions,
        subtitleMode = subtitleMode,
        hasSelectableTracks = hasSelectable,
    )
}

/**
 * 切换清晰度为指定档位。
 */
fun selectVideoQuality(
    player: Player,
    quality: PlayerQualityOption,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(quality.group.mediaTrackGroup, quality.trackIndex))
            .build()
}

/**
 * 切换清晰度为自适应模式（ABR）。
 */
fun selectAutoVideoQuality(player: Player) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
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
 * 播放器画质、音轨与软字幕选择器。
 * 竖屏采用 Material 3 [BgmModalBottomSheet]，横屏采用右侧抽屉式滑入面板，避免横屏切入切出 Dialog 造成闪烁。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTrackSelectionSheet(
    isOpen: Boolean,
    isLandscape: Boolean,
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAutoSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    if (isLandscape) {
        // 横屏：在舞台右侧使用无缝抽屉
    } else {
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
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
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
                onSelectQuality = onSelectQuality,
                onAutoQuality = onAutoQuality,
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
 * 画质、音轨与字幕选择内容面板。
 */
@Composable
private fun PlayerTrackSelectionContent(
    tracksSnapshot: PlayerTracksSnapshot,
    onSelectQuality: (PlayerQualityOption) -> Unit,
    onAutoQuality: () -> Unit,
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
