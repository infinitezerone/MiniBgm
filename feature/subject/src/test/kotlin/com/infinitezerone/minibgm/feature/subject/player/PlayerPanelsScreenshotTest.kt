package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMatch
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.core.model.DanmakuMode
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 播放器面板 UI 截图自查（Roborazzi + Robolectric，纯 JVM 无需设备）：
 * 覆盖播放设置面板、控制栏（含齿轮入口与弹幕纠正入口）、横屏选集抽屉。
 * 输出到 src/test/snapshots/images/，随录随看。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayerPanelsScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleTracks =
        PlayerTracksSnapshot(
            qualityTracks =
                listOf(
                    PlayerQualityOption(
                        id = "1080",
                        name = "1080P",
                        detail = "1920×1080 · 4.2 Mbps",
                        width = 1920,
                        height = 1080,
                        bitrate = 4_200_000,
                        isSelected = true,
                    ),
                    PlayerQualityOption(
                        id = "720",
                        name = "720P",
                        detail = "1280×720 · 2.1 Mbps",
                        width = 1280,
                        height = 720,
                        bitrate = 2_100_000,
                        isSelected = false,
                    ),
                    PlayerQualityOption(
                        id = "480",
                        name = "480P",
                        detail = "854×480 · 900 Kbps",
                        width = 854,
                        height = 480,
                        bitrate = 900_000,
                        isSelected = false,
                    ),
                ),
            qualityMode = QualityMode.SPECIFIC,
            currentAutoQualityName = "1080P",
            currentVideoSizeQualityName = "1080P",
            subtitleTracks =
                listOf(
                    PlayerTrackOption(id = "text_zh", name = "中文（简体）", isSelected = true, group = textTrackGroup("zh"), trackIndex = 0),
                ),
            audioTracks =
                listOf(
                    PlayerTrackOption(id = "audio_ja", name = "日语", isSelected = true, group = textTrackGroup("ja"), trackIndex = 0),
                    PlayerTrackOption(id = "audio_zh", name = "汉语", isSelected = false, group = textTrackGroup("zh"), trackIndex = 0),
                ),
            subtitleMode = SubtitleMode.SPECIFIC,
            hasSelectableTracks = true,
        )

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h1600dp")
    fun settings_panel_portrait() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PlayerTrackSelectionContent(
                        tracksSnapshot = sampleTracks,
                        onSelectQuality = {},
                        onAutoQuality = {},
                        onSelectSubtitle = {},
                        onDisableSubtitles = {},
                        onAutoSubtitles = {},
                        onSelectAudio = {},
                        playbackSpeed = 1.25f,
                        onSelectSpeed = {},
                        resizeMode = PlayerResizeMode.FIT,
                        onSelectResizeMode = {},
                        isDanmakuEnabled = true,
                        onToggleDanmaku = {},
                        onOpenDanmakuPicker = {},
                        isLooping = false,
                        onToggleLoop = {},
                        onClose = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_settings_panel.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h760dp-land")
    fun controls_overlay_landscape_with_gear_and_danmaku_entry() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                // 深色底模拟视频画面：控制栏白色图标在浅底上不可见
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202124))) {
                    PlayerControlsOverlay(
                        subjectName = "葬送的芙莉莲",
                        epLabel = "第 3 话",
                        episodeName = "杀人魔",
                        isPlaying = true,
                        isBuffering = false,
                        isEnded = false,
                        position = MutableStateFlow(65_000L),
                        bufferedPosition = MutableStateFlow(120_000L),
                        totalDuration = 24 * 60_000L,
                        isScrubbing = false,
                        scrubProgress = 0.45f,
                        isLandscape = true,
                        resizeMode = PlayerResizeMode.FIT,
                        errorMessage = null,
                        onBackClick = {},
                        onPlayPauseToggle = {},
                        onRewind10 = {},
                        onForward10 = {},
                        onScrubStart = {},
                        onScrubbing = {},
                        onScrubEnd = {},
                        onToggleFullscreen = {},
                        onCycleResizeMode = {},
                        onEnterPip = {},
                        onRetry = {},
                        onNextSource = {},
                        onRequestOpenSources = {},
                        playbackSpeed = 1.25f,
                        onCyclePlaybackSpeed = {},
                        showEpisodeQueue = true,
                        onOpenEpisodeQueue = {},
                        currentQualityLabel = "1080P",
                        onOpenTrackSelection = {},
                        isDanmakuEnabled = true,
                        onToggleDanmaku = {},
                        danmakuMatch =
                            DanmakuMatch(
                                chosen = DanmakuEpisodeCandidate(2002, "葬送的芙莉莲", 9876, "第03话 杀人魔"),
                                candidates = listOf(DanmakuEpisodeCandidate(2002, "葬送的芙莉莲", 9876, "第03话 杀人魔")),
                                state = DanmakuMatchState.FALLBACK,
                            ),
                        onOpenDanmakuPicker = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_controls_landscape.png")
    }

    @Test
    fun episode_queue_drawer_landscape() {
        val episodes =
            (1..12).map { ep ->
                PlayerEpisodeItem(id = ep.toLong(), sort = ep.toFloat(), name = "第${ep}话", nameCn = "第 $ep 话", isWatched = ep < 3)
            }
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize().width(880.dp)) {
                    PlayerEpisodeQueueDrawer(
                        queue =
                            episodes.map {
                                PlayerQueueEntry(
                                    streamUrl = "https://cdn.example.com/e${it.sort}.m3u8",
                                    label = it.displayName,
                                    episodeSort = it.sort,
                                )
                            },
                        currentIndex = 2,
                        episodes = episodes,
                        selectedEpisodeSort = 3f,
                        sources =
                            listOf(
                                PlayerSourceTab(id = "direct", name = "默认直链", isDirect = true),
                                PlayerSourceTab(id = "rule1", name = "备用规则源"),
                            ),
                        sourceFailureCounts = emptyMap(),
                        selectedSourceIndex = 0,
                        autoNextEnabled = true,
                        selectedEpisodeId = 3L,
                        totalEpisodes = 12,
                        hasMoreEpisodes = false,
                        isLoadingMoreEpisodes = false,
                        onLoadMoreEpisodes = {},
                        onToggleAutoNext = {},
                        onSelectSource = {},
                        onSelectEpisode = {},
                        onSelectQueueIndex = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_episode_drawer.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h900dp")
    fun controls_overlay_portrait() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202124))) {
                    PlayerControlsOverlay(
                        subjectName = "葬送的芙莉莲",
                        epLabel = "第 3 话",
                        episodeName = "杀人魔",
                        isPlaying = true,
                        isBuffering = false,
                        isEnded = false,
                        position = MutableStateFlow(65_000L),
                        bufferedPosition = MutableStateFlow(120_000L),
                        totalDuration = 24 * 60_000L,
                        isScrubbing = false,
                        scrubProgress = 0.45f,
                        isLandscape = false,
                        resizeMode = PlayerResizeMode.FIT,
                        errorMessage = null,
                        onBackClick = {},
                        onPlayPauseToggle = {},
                        onRewind10 = {},
                        onForward10 = {},
                        onScrubStart = {},
                        onScrubbing = {},
                        onScrubEnd = {},
                        onToggleFullscreen = {},
                        onCycleResizeMode = {},
                        onEnterPip = {},
                        onRetry = {},
                        onNextSource = {},
                        onRequestOpenSources = {},
                        playbackSpeed = 1f,
                        onCyclePlaybackSpeed = {},
                        showEpisodeQueue = false,
                        onOpenEpisodeQueue = {},
                        currentQualityLabel = "1080P",
                        onOpenTrackSelection = {},
                        isDanmakuEnabled = true,
                        onToggleDanmaku = {},
                        danmakuMatch =
                            DanmakuMatch(
                                chosen = DanmakuEpisodeCandidate(2002, "葬送的芙莉莲", 9876, "第03话 杀人魔"),
                                candidates = listOf(DanmakuEpisodeCandidate(2002, "葬送的芙莉莲", 9876, "第03话 杀人魔")),
                                state = DanmakuMatchState.AUTO_MATCHED,
                            ),
                        onOpenDanmakuPicker = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_controls_portrait.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w640dp-h360dp")
    fun danmaku_view_overlay() {
        val items =
            listOf(
                DanmakuItem(timeMs = 10_000L, text = "前方高能预警！", color = 0xFFFFFF),
                DanmakuItem(timeMs = 10_100L, text = "名场面打卡", color = 0xFF66CCFF.toInt()),
                DanmakuItem(timeMs = 10_200L, text = "这里循环播放过三遍", color = 0xFFFF6666.toInt()),
                DanmakuItem(timeMs = 10_300L, text = "顶部固定弹幕", color = 0xFFFFFF, mode = DanmakuMode.TOP),
                DanmakuItem(timeMs = 10_400L, text = "底部固定弹幕", color = 0xFF66FF66.toInt(), mode = DanmakuMode.BOTTOM),
                DanmakuItem(timeMs = 30_000L, text = "时间窗之外的弹幕不应出现", color = 0xFFFFFF),
            )
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101014))) {
                    DanmakuView(
                        danmakus = items,
                        currentPositionMs = 13_500L,
                        isEnabled = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_danmaku_overlay.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w640dp-h360dp")
    fun resolving_view() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                PlayerResolvingView(
                    sourceName = "备用规则源",
                    attempt = 2,
                    attemptTotal = 4,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_resolving.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h760dp")
    fun empty_view_portrait() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                // 空态落在黑色视频舞台内（白字），浅色主题底会"隐形"
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerEmptyView(
                        subjectName = "葬送的芙莉莲",
                        epLabel = "第 3 话",
                        errorMessage = null,
                        onBackClick = {},
                        onRetry = {},
                        onNextSource = {},
                        onRequestOpenSources = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_empty.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w640dp-h360dp")
    fun controls_error_state_landscape() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202124))) {
                    PlayerControlsOverlay(
                        subjectName = "葬送的芙莉莲",
                        epLabel = "第 3 话",
                        episodeName = "杀人魔",
                        isPlaying = false,
                        isBuffering = false,
                        isEnded = false,
                        position = MutableStateFlow(0L),
                        bufferedPosition = MutableStateFlow(0L),
                        totalDuration = 0L,
                        isScrubbing = false,
                        scrubProgress = 0f,
                        isLandscape = true,
                        resizeMode = PlayerResizeMode.FIT,
                        errorMessage = "视频流无法解析，可能已失效",
                        onBackClick = {},
                        onPlayPauseToggle = {},
                        onRewind10 = {},
                        onForward10 = {},
                        onScrubStart = {},
                        onScrubbing = {},
                        onScrubEnd = {},
                        onToggleFullscreen = {},
                        onCycleResizeMode = {},
                        onEnterPip = {},
                        onRetry = {},
                        onNextSource = {},
                        onRequestOpenSources = {},
                        playbackSpeed = 1f,
                        onCyclePlaybackSpeed = {},
                        showEpisodeQueue = false,
                        onOpenEpisodeQueue = {},
                        onOpenTrackSelection = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_error_state.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h1400dp")
    fun intro_content_portrait() {
        val uiState =
            PlayerUiState(
                subjectId = 404724L,
                episodeId = 3L,
                episodeSort = 3f,
                episodeName = "杀人魔",
                subjectName = "葬送的芙莉莲",
                subjectCoverUrl = "",
                subjectScore = 9.1,
                subjectDate = "2023-09-29",
                subjectSummary = "能干的精灵芙莉莲在勇者一行里活了五十年……",
                subjectCollectionType = 2,
                episodes =
                    (1..12).map { ep ->
                        PlayerEpisodeItem(id = ep.toLong(), sort = ep.toFloat(), name = "第${ep}话", nameCn = "第 $ep 话", isWatched = ep < 3)
                    },
                totalEpisodes = 12,
                sources = listOf(PlayerSourceTab(id = "direct", name = "默认直链", isDirect = true)),
                isWatched = false,
            )
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                PlayerIntroContent(
                    uiState = uiState,
                    epLabel = "第 3 话",
                    onToggleWatched = {},
                    onToggleFollow = {},
                    onSelectEpisode = {},
                    onOpenAllEpisodes = {},
                    onSelectSource = {},
                    onRequestOpenSources = null,
                    onManageRules = null,
                    onSubjectClick = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/player_intro.png")
    }

    private companion object {
        /** Robolectric 提供真实 android.* 实现，TrackGroup 可直接构造 */
        fun textTrackGroup(language: String): Tracks.Group =
            Tracks.Group(
                TrackGroup(
                    Format
                        .Builder()
                        .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(language)
                        .build(),
                ),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(false),
            )
    }
}
