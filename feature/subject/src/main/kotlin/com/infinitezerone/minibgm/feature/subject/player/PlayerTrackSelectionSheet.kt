package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.media3.common.util.UnstableApi
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuMatch
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.feature.subject.R
import java.util.Locale
import kotlin.math.abs

/**
 * 播放器设置抽屉分组 Tab：播放（画质/倍速/画幅/字幕/音轨/循环）与 弹幕（开关/分集/速度/透明度/时间轴）
 */
enum class PlayerSettingsTab {
    PLAYBACK,
    DANMAKU,
}

/** 弹幕滚动耗时候选档位（慢速 11s、适中 8.5s、较快 6.5s、快速 5s） */
internal val DANMAKU_DURATION_OPTIONS = listOf(11_000L, 8_500L, 6_500L, 5_000L)

internal fun formatDanmakuDurationLabel(durationMs: Long): String =
    when (durationMs) {
        11_000L -> "慢速 11s"
        8_500L -> "适中 8.5s"
        6_500L -> "较快 6.5s"
        5_000L -> "快速 5s"
        else -> "${durationMs / 1000f}s"
    }

/** 弹幕不透明度候选档位（100%、80%、60%、40%） */
internal val DANMAKU_ALPHA_OPTIONS = listOf(1.0f, 0.8f, 0.6f, 0.4f)

internal fun formatDanmakuAlphaLabel(alpha: Float): String = "${(alpha * 100).toInt()}%"

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
        clean in listOf("zh-cn", "zh-hans", "zh-sg", "chs") -> "中文 (简体)"
        clean in listOf("zh-tw", "zh-hk", "zh-mo", "zh-hant", "cht") -> "中文 (繁体)"
        clean in listOf("zh", "chi", "zho") -> "中文"
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

private fun isTraditionalChineseLabel(label: String): Boolean {
    val clean = label.lowercase()
    return clean.contains("繁") ||
        clean == "tc" ||
        clean == "cht" ||
        clean.contains("big5") ||
        clean.contains("繁体") ||
        clean.contains("繁體")
}

private fun isSimplifiedChineseLabel(label: String): Boolean {
    val clean = label.lowercase()
    return clean.contains("简") ||
        clean == "sc" ||
        clean == "chs" ||
        clean.contains("gb") ||
        clean.contains("简体") ||
        clean.contains("簡體")
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
    val rawLabel = label?.trim()?.takeIf { it.isNotBlank() }
    val resolvedLang = resolveLanguageName(language)
    val langName =
        when {
            resolvedLang == "中文" && rawLabel != null -> {
                when {
                    isTraditionalChineseLabel(rawLabel) -> "中文 (繁体)"
                    isSimplifiedChineseLabel(rawLabel) -> "中文 (简体)"
                    else -> resolvedLang
                }
            }
            else -> resolvedLang
        }

    val baseName =
        when {
            rawLabel != null && langName != null -> {
                if (rawLabel.contains(langName, ignoreCase = true)) {
                    rawLabel
                } else if (rawLabel.equals("chs", ignoreCase = true) || rawLabel.equals("sc", ignoreCase = true)) {
                    "中文 (简体)"
                } else if (rawLabel.equals("cht", ignoreCase = true) || rawLabel.equals("tc", ignoreCase = true)) {
                    "中文 (繁体)"
                } else {
                    "$langName ($rawLabel)"
                }
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
 * 对轨道名称列表进行防重消歧：
 * 1. 若同片源下存在两条未标明变体的通用「中文」字幕/音轨，按番剧发布惯例（SC_TC）自动归一为「中文 (简体)」与「中文 (繁体)」；
 * 2. 对其他重复名称追加序号索引（如「日语 · 双声道 #1」、「日语 · 双声道 #2」），彻底杜绝同名混淆。
 */
fun disambiguateTrackNames(names: List<String>): List<String> {
    if (names.size <= 1) return names

    fun splitBaseAndSuffix(name: String): Pair<String, String> {
        val suffixes = listOf(" [默认] [强制]", " [强制] [默认]", " [默认]", " [强制]")
        for (s in suffixes) {
            if (name.endsWith(s)) {
                return name.removeSuffix(s) to s
            }
        }
        return name to ""
    }

    val pairs = names.map { splitBaseAndSuffix(it) }
    val baseCounts = pairs.groupingBy { it.first }.eachCount()
    val isDualGenericChinese = baseCounts["中文"] == 2

    val seen = mutableMapOf<String, Int>()
    return pairs.map { (base, suffix) ->
        val count = baseCounts[base] ?: 1
        if (count > 1) {
            val idx = (seen[base] ?: 0) + 1
            seen[base] = idx
            if (base == "中文" && isDualGenericChinese) {
                val newBase = if (idx == 1) "中文 (简体)" else "中文 (繁体)"
                "$newBase$suffix"
            } else {
                "$base #$idx$suffix"
            }
        } else {
            "$base$suffix"
        }
    }
}

/**
 * 对播放器轨道项列表进行防重消歧。
 */
fun disambiguateTrackOptions(options: List<PlayerTrackOption>): List<PlayerTrackOption> {
    if (options.size <= 1) return options
    val names = options.map { it.name }
    val disambiguatedNames = disambiguateTrackNames(names)
    return options.zip(disambiguatedNames) { opt, newName ->
        if (opt.name != newName) opt.copy(name = newName) else opt
    }
}

/**
 * 从 Media3 [Player] 动态提取画质、音轨与字幕轨状态快照。
 */
@androidx.annotation.OptIn(UnstableApi::class)
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
        subtitleTracks = disambiguateTrackOptions(subtitleOptions),
        audioTracks = disambiguateTrackOptions(audioOptions),
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
    danmakuMatch: DanmakuMatch? = null,
    onSelectDanmakuCandidate: (DanmakuEpisodeCandidate) -> Unit = {},
    danmakuAlpha: Float = 1.0f,
    onSelectDanmakuAlpha: (Float) -> Unit = {},
    danmakuDurationMs: Long = 8500L,
    onSelectDanmakuDurationMs: (Long) -> Unit = {},
    danmakuTimeOffsetMs: Long = 0L,
    onSelectDanmakuTimeOffset: (Long) -> Unit = {},
    isLooping: Boolean = false,
    onToggleLoop: () -> Unit = {},
    initialTab: PlayerSettingsTab = PlayerSettingsTab.PLAYBACK,
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
            danmakuMatch = danmakuMatch,
            onSelectDanmakuCandidate = onSelectDanmakuCandidate,
            danmakuAlpha = danmakuAlpha,
            onSelectDanmakuAlpha = onSelectDanmakuAlpha,
            danmakuDurationMs = danmakuDurationMs,
            onSelectDanmakuDurationMs = onSelectDanmakuDurationMs,
            danmakuTimeOffsetMs = danmakuTimeOffsetMs,
            onSelectDanmakuTimeOffset = onSelectDanmakuTimeOffset,
            isLooping = isLooping,
            onToggleLoop = onToggleLoop,
            initialTab = initialTab,
            onClose = onDismiss,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/**
 * 播放器「播放设置」横屏右侧抽屉：对照选集抽屉与播放源抽屉同源视觉结构，
 * 容器使用高对比度主题容器 [MaterialTheme.colorScheme.surfaceContainerHigh]，左上/左下 16dp 圆角，
 * 在宽屏环境下提供单手操控的优雅滑出体验，避免底部弹层横向拉伸变形与遮挡画面。
 */
@Composable
internal fun PlayerTrackSelectionDrawer(
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
    danmakuMatch: DanmakuMatch? = null,
    onSelectDanmakuCandidate: (DanmakuEpisodeCandidate) -> Unit = {},
    danmakuAlpha: Float = 1.0f,
    onSelectDanmakuAlpha: (Float) -> Unit = {},
    danmakuDurationMs: Long = 8500L,
    onSelectDanmakuDurationMs: (Long) -> Unit = {},
    danmakuTimeOffsetMs: Long = 0L,
    onSelectDanmakuTimeOffset: (Long) -> Unit = {},
    isLooping: Boolean = false,
    onToggleLoop: () -> Unit = {},
    initialTab: PlayerSettingsTab = PlayerSettingsTab.PLAYBACK,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier.width(360.dp).fillMaxHeight(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .padding(vertical = 8.dp),
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
                danmakuMatch = danmakuMatch,
                onSelectDanmakuCandidate = onSelectDanmakuCandidate,
                danmakuAlpha = danmakuAlpha,
                onSelectDanmakuAlpha = onSelectDanmakuAlpha,
                danmakuDurationMs = danmakuDurationMs,
                onSelectDanmakuDurationMs = onSelectDanmakuDurationMs,
                danmakuTimeOffsetMs = danmakuTimeOffsetMs,
                onSelectDanmakuTimeOffset = onSelectDanmakuTimeOffset,
                isLooping = isLooping,
                onToggleLoop = onToggleLoop,
                initialTab = initialTab,
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    danmakuMatch: DanmakuMatch? = null,
    onSelectDanmakuCandidate: (DanmakuEpisodeCandidate) -> Unit = {},
    danmakuAlpha: Float = 1.0f,
    onSelectDanmakuAlpha: (Float) -> Unit = {},
    danmakuDurationMs: Long = 8500L,
    onSelectDanmakuDurationMs: (Long) -> Unit = {},
    danmakuTimeOffsetMs: Long = 0L,
    onSelectDanmakuTimeOffset: (Long) -> Unit = {},
    isLooping: Boolean = false,
    onToggleLoop: () -> Unit = {},
    initialTab: PlayerSettingsTab = PlayerSettingsTab.PLAYBACK,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkThemed: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    var currentTab by remember(initialTab) { mutableStateOf(initialTab) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 顶部选项卡切换行：[ 播放 ] [ 弹幕 ] 与 右侧关闭按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpeedPill(
                    label = stringResource(R.string.feature_subject_player_tab_playback),
                    isSelected = currentTab == PlayerSettingsTab.PLAYBACK,
                    isDarkThemed = isDarkThemed,
                    modifier = Modifier.height(34.dp).width(72.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentTab = PlayerSettingsTab.PLAYBACK
                    },
                )
                SpeedPill(
                    label = stringResource(R.string.feature_subject_player_tab_danmaku),
                    isSelected = currentTab == PlayerSettingsTab.DANMAKU,
                    isDarkThemed = isDarkThemed,
                    modifier = Modifier.height(34.dp).width(72.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentTab = PlayerSettingsTab.DANMAKU
                    },
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
            if (currentTab == PlayerSettingsTab.PLAYBACK) {
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
                    val autoSubtitleSubtitle =
                        if (tracksSnapshot.subtitleMode == SubtitleMode.AUTO) {
                            tracksSnapshot.subtitleTracks.firstOrNull { it.isSelected }?.let {
                                stringResource(R.string.feature_subject_player_subtitles_auto_current, it.name)
                            }
                        } else {
                            null
                        }

                    TrackItemRow(
                        title = stringResource(R.string.feature_subject_player_subtitles_auto),
                        subtitle = autoSubtitleSubtitle,
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

                // 4. 单集循环
                item {
                    ToggleRow(
                        title = stringResource(R.string.feature_subject_player_loop),
                        isChecked = isLooping,
                        isDarkThemed = isDarkThemed,
                        onToggle = onToggleLoop,
                    )
                }
            } else {
                // 弹幕设置页面：开关 + 分集匹配与候选切换 + 速度 + 不透明度 + 时间轴
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

                if (isDanmakuEnabled) {
                    // 分集匹配信息与候选列表
                    if (danmakuMatch != null) {
                        val match = danmakuMatch
                        val chosen = match.chosen

                        item {
                            Text(
                                text = stringResource(R.string.feature_subject_player_danmaku_current_match),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }

                        item {
                            val stateBadgeText =
                                when (match.state) {
                                    DanmakuMatchState.USER_OVERRIDE ->
                                        stringResource(
                                            R.string.feature_subject_player_danmaku_pick_state_override,
                                        )
                                    DanmakuMatchState.AUTO_MATCHED ->
                                        stringResource(
                                            R.string.feature_subject_player_danmaku_pick_state_auto,
                                        )
                                    DanmakuMatchState.FALLBACK ->
                                        stringResource(
                                            R.string.feature_subject_player_danmaku_pick_state_fallback,
                                        )
                                }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color =
                                    if (isDarkThemed) {
                                        Color.White.copy(
                                            alpha = 0.05f,
                                        )
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                border =
                                    BorderStroke(
                                        1.dp,
                                        if (isDarkThemed) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text =
                                                chosen?.episodeTitle
                                                    ?: stringResource(R.string.feature_subject_player_danmaku_pick_state_fallback),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDarkThemed) Color.White else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (chosen?.animeTitle != null) {
                                            Text(
                                                text = chosen.animeTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color =
                                                    if (isDarkThemed) {
                                                        Color.White.copy(
                                                            alpha = 0.6f,
                                                        )
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = stateBadgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (match.candidates.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.feature_subject_player_danmaku_switch_candidate),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }

                            items(match.candidates, key = { "cand_${it.animeId}_${it.episodeId}" }) { candidate ->
                                val isSelected = candidate.episodeId == chosen?.episodeId && candidate.animeId == chosen.animeId
                                TrackItemRow(
                                    title = candidate.episodeTitle,
                                    subtitle = candidate.animeTitle,
                                    isSelected = isSelected,
                                    isDarkThemed = isDarkThemed,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectDanmakuCandidate(candidate)
                                    },
                                )
                            }
                        }
                    } else if (onOpenDanmakuPicker != null) {
                        item {
                            TrackItemRow(
                                title = stringResource(R.string.feature_subject_player_danmaku_pick_episode),
                                isSelected = false,
                                isDarkThemed = isDarkThemed,
                                onClick = onOpenDanmakuPicker,
                            )
                        }
                    }

                    // 弹幕速度调节
                    item {
                        Text(
                            text = stringResource(R.string.feature_subject_player_danmaku_speed_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DANMAKU_DURATION_OPTIONS.forEach { durationOption ->
                                SpeedPill(
                                    label = formatDanmakuDurationLabel(durationOption),
                                    isSelected = danmakuDurationMs == durationOption,
                                    isDarkThemed = isDarkThemed,
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectDanmakuDurationMs(durationOption)
                                    },
                                )
                            }
                        }
                    }

                    // 弹幕不透明度调节
                    item {
                        Text(
                            text = stringResource(R.string.feature_subject_player_danmaku_alpha_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDarkThemed) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DANMAKU_ALPHA_OPTIONS.forEach { alphaOption ->
                                SpeedPill(
                                    label = formatDanmakuAlphaLabel(alphaOption),
                                    isSelected = abs(danmakuAlpha - alphaOption) < 0.05f,
                                    isDarkThemed = isDarkThemed,
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectDanmakuAlpha(alphaOption)
                                    },
                                )
                            }
                        }
                    }

                    // 弹幕时间轴微调
                    item {
                        DanmakuOffsetRow(
                            offsetMs = danmakuTimeOffsetMs,
                            isDarkThemed = isDarkThemed,
                            onSelectOffset = onSelectDanmakuTimeOffset,
                        )
                    }
                }
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

/** 弹幕时间轴微调候选档位（-2s ~ +2s） */
internal val DANMAKU_OFFSET_OPTIONS = listOf(-2000L, -1000L, -500L, 0L, 500L, 1000L, 2000L)

/** 弹幕时间轴微调标签格式化（0s, +0.5s, -1s 等） */
internal fun formatDanmakuOffsetPillLabel(offsetMs: Long): String =
    when {
        offsetMs == 0L -> "0s"
        offsetMs % 1000L == 0L -> {
            val s = offsetMs / 1000L
            if (s > 0) "+${s}s" else "${s}s"
        }
        else -> {
            val s = offsetMs.toFloat() / 1000f
            if (s > 0) String.format(Locale.US, "+%.1fs", s) else String.format(Locale.US, "%.1fs", s)
        }
    }

/**
 * 药丸单选（倍速/画面比例共用）：选中态用主题色描边加粗，未选中弱化
 */
@Composable
private fun SpeedPill(
    label: String,
    isSelected: Boolean,
    isDarkThemed: Boolean,
    modifier: Modifier = Modifier.size(width = 52.dp, height = 36.dp),
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
        modifier = modifier,
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

/** 弹幕时间轴微调行 */
@Composable
private fun DanmakuOffsetRow(
    offsetMs: Long,
    isDarkThemed: Boolean,
    onSelectOffset: (Long) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_danmaku_offset_title),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDarkThemed) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            val offsetText =
                if (offsetMs == 0L) {
                    stringResource(R.string.feature_subject_player_danmaku_offset_reset)
                } else {
                    stringResource(
                        R.string.feature_subject_player_danmaku_offset_seconds,
                        offsetMs.toFloat() / 1000f,
                    )
                }
            Text(
                text = offsetText,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum",
                    ),
                color =
                    if (offsetMs != 0L) {
                        MaterialTheme.colorScheme.primary
                    } else if (isDarkThemed) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DANMAKU_OFFSET_OPTIONS.forEach { optionMs ->
                SpeedPill(
                    label = formatDanmakuOffsetPillLabel(optionMs),
                    isSelected = offsetMs == optionMs,
                    isDarkThemed = isDarkThemed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectOffset(optionMs)
                    },
                )
            }
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
