package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.SubjectDownloadGroup
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 下载管理两级页面（合集总览 + 分集明细）全状态 UI 截图与语义断言测试（Roborazzi + Robolectric）。
 * 覆盖：已完成、高速下载中（含节点数）、正在连接做种节点（0 节点/0 速度）、解析种子元数据、
 * 排队等待、已暂停、连接做种节点超时（ERROR 态及重试入口）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DownloadScreensScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val mb = 1024L * 1024L

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h1100dp")
    fun subjectDownloadDetail_allEpisodeStates_portrait() {
        val group =
            SubjectDownloadGroup(
                subjectId = 400_001L,
                subjectName = "黑色五叶草 Season 2",
                subjectCoverUrl = "",
                tasks =
                    listOf(
                        TorrentTask(
                            id = "ep-170",
                            title = "[黒ネズミたち] Black Clover - 170",
                            magnetUrl = "magnet:?xt=urn:btih:170",
                            status = TorrentTaskStatus.COMPLETED,
                            totalBytes = 450 * mb,
                            downloadedBytes = 450 * mb,
                            savePath = "/storage/emulated/0/downloads/170.mkv",
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 170f,
                            episodeName = "遥远的未来",
                        ),
                        TorrentTask(
                            id = "ep-171",
                            title = "[黒ネズミたち] Black Clover - 171",
                            magnetUrl = "magnet:?xt=urn:btih:171",
                            status = TorrentTaskStatus.ERROR,
                            totalBytes = 715_700_000L,
                            downloadedBytes = 0L,
                            errorMessage = "连接做种节点超时（暂无可用源），请点击重试或更换其他资源",
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 171f,
                            episodeName = "[黒ネズミたち] 黑色五叶草 Season 2 - 171 (ABEMA 1080p MKV)",
                        ),
                        TorrentTask(
                            id = "ep-172",
                            title = "[黒ネズミたち] Black Clover - 172",
                            magnetUrl = "magnet:?xt=urn:btih:172",
                            status = TorrentTaskStatus.DOWNLOADING,
                            totalBytes = 480 * mb,
                            downloadedBytes = 240 * mb,
                            downloadSpeedBytesPerSec = (4.2 * mb).toLong(),
                            connectedPeers = 12,
                            streamUrl = "http://127.0.0.1:36215/torrents/0/stream/0",
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 172f,
                            episodeName = "绝望与希望之战",
                        ),
                        TorrentTask(
                            id = "ep-173",
                            title = "[黒ネズミたち] Black Clover - 173",
                            magnetUrl = "magnet:?xt=urn:btih:173",
                            status = TorrentTaskStatus.DOWNLOADING,
                            totalBytes = 500 * mb,
                            downloadedBytes = 0L,
                            downloadSpeedBytesPerSec = 0L,
                            connectedPeers = 0,
                            streamUrl = "http://127.0.0.1:36215/torrents/1/stream/0",
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 173f,
                            episodeName = "魔法帝的觉醒",
                        ),
                        TorrentTask(
                            id = "ep-174",
                            title = "[黒ネズミたち] Black Clover - 174",
                            magnetUrl = "magnet:?xt=urn:btih:174",
                            status = TorrentTaskStatus.RESOLVING,
                            totalBytes = 480 * mb,
                            downloadedBytes = 0L,
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 174f,
                            episodeName = "黑暴牛全员集结",
                        ),
                        TorrentTask(
                            id = "ep-175",
                            title = "[黒ネズミたち] Black Clover - 175",
                            magnetUrl = "magnet:?xt=urn:btih:175",
                            status = TorrentTaskStatus.QUEUED,
                            totalBytes = 480 * mb,
                            downloadedBytes = 0L,
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 175f,
                            episodeName = "超越极限的斩击",
                        ),
                        TorrentTask(
                            id = "ep-176",
                            title = "[黒ネズミたち] Black Clover - 176",
                            magnetUrl = "magnet:?xt=urn:btih:176",
                            status = TorrentTaskStatus.PAUSED,
                            totalBytes = 480 * mb,
                            downloadedBytes = 120 * mb,
                            subjectId = 400_001L,
                            subjectName = "黑色五叶草 Season 2",
                            episodeSort = 176f,
                            episodeName = "四叶草的誓言",
                        ),
                    ),
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SubjectDownloadDetailContent(
                        subjectId = 400_001L,
                        subjectName = "黑色五叶草 Season 2",
                        group = group,
                        onBackClick = {},
                        onPlayEpisode = {},
                        onPauseTask = {},
                        onResumeTask = {},
                        onDeleteTask = {},
                        onPauseAll = {},
                        onResumeAll = {},
                        onDeleteAll = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("连接做种节点超时（暂无可用源），请点击重试或更换其他资源").assertIsDisplayed()
        composeTestRule.onNodeWithText("50% · 4.2 MB/s · 12 节点").assertIsDisplayed()
        composeTestRule.onNodeWithText("0% · 正在连接做种节点…").assertIsDisplayed()
        composeTestRule.onNodeWithText("正在解析种子元数据…").assertIsDisplayed()
        composeTestRule.onNodeWithText("排队等待下载… · 0.0 MB / 480.0 MB").assertIsDisplayed()
        composeTestRule.onNodeWithText("已暂停 · 120.0 MB / 480.0 MB").assertIsDisplayed()

        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/download_detail_all_states.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h960dp")
    fun downloadScreen_overviewStates_portrait() {
        val groups =
            listOf(
                SubjectDownloadGroup(
                    subjectId = 1L,
                    subjectName = "黑色五叶草 Season 2",
                    subjectCoverUrl = "",
                    tasks =
                        listOf(
                            TorrentTask(
                                id = "t1",
                                title = "EP 171",
                                magnetUrl = "magnet:?xt=urn:btih:171",
                                status = TorrentTaskStatus.ERROR,
                                totalBytes = 682 * mb,
                                errorMessage = "连接做种节点超时（暂无可用源），请点击重试或更换其他资源",
                            ),
                        ),
                ),
                SubjectDownloadGroup(
                    subjectId = 2L,
                    subjectName = "JOJO的奇妙冒险 飙马野郎",
                    subjectCoverUrl = "",
                    tasks =
                        listOf(
                            TorrentTask(
                                id = "t2",
                                title = "EP 04",
                                magnetUrl = "magnet:?xt=urn:btih:04",
                                status = TorrentTaskStatus.DOWNLOADING,
                                totalBytes = 600 * mb,
                                downloadedBytes = 0L,
                                downloadSpeedBytesPerSec = 0L,
                                connectedPeers = 0,
                            ),
                        ),
                ),
                SubjectDownloadGroup(
                    subjectId = 3L,
                    subjectName = "葬送的芙莉莲",
                    subjectCoverUrl = "",
                    tasks =
                        listOf(
                            TorrentTask(
                                id = "t3",
                                title = "EP 01",
                                magnetUrl = "magnet:?xt=urn:btih:01",
                                status = TorrentTaskStatus.COMPLETED,
                                totalBytes = 500 * mb,
                                downloadedBytes = 500 * mb,
                            ),
                        ),
                ),
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DownloadScreenContent(
                        downloadGroups = groups,
                        onBackClick = {},
                        onSubjectClick = { _, _ -> },
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText("1 集异常 · 连接做种节点超时（暂无可用源），请点击重试或更换其他资源")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("正在下载 1 集 · 正在连接做种节点…")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("已离线 1 集 · 500.0 MB")
            .assertIsDisplayed()

        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/download_overview_states.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h720dp")
    fun downloadScreen_errorBannerWhenIdleWithTimeout_portrait() {
        val groups =
            listOf(
                SubjectDownloadGroup(
                    subjectId = 1L,
                    subjectName = "黑色五叶草 Season 2",
                    subjectCoverUrl = "",
                    tasks =
                        listOf(
                            TorrentTask(
                                id = "t1",
                                title = "EP 171",
                                magnetUrl = "magnet:?xt=urn:btih:171",
                                status = TorrentTaskStatus.ERROR,
                                totalBytes = 682 * mb,
                                errorMessage = "连接做种节点超时（暂无可用源），请点击重试或更换其他资源",
                            ),
                        ),
                ),
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DownloadScreenContent(
                        downloadGroups = groups,
                        onBackClick = {},
                        onSubjectClick = { _, _ -> },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("1 个任务连接超时或异常，点击查看重试").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/download_overview_error_banner_dark.png")
    }
}
