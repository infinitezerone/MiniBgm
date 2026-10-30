package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 观看记录页（聚合卡片 + 展开分集 + 空态同构）UI 截图测试（Roborazzi + Robolectric）。
 * 覆盖：多集展开、单集已看完、未知番剧 ID 的独立记录。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreensScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val minute = 60_000L

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h960dp")
    fun historyList_allStates_portrait() {
        val entries =
            listOf(
                PlaybackHistory(
                    episodeId = 1201L,
                    subjectId = 1L,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "北部高原之战",
                    episodeSort = 12f,
                    sourceName = "线路二",
                    positionMs = 9 * 60_000L + 30_000L,
                    durationMs = 24 * 60_000L,
                    updatedAt = now - 8 * minute,
                ),
                PlaybackHistory(
                    episodeId = 1101L,
                    subjectId = 1L,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "勇者之剑",
                    episodeSort = 11f,
                    positionMs = 23 * 60_000L,
                    durationMs = 24 * 60_000L,
                    updatedAt = now - 2 * 24 * 60 * minute,
                ),
                PlaybackHistory(
                    episodeId = 201L,
                    subjectId = 2L,
                    subjectName = "JOJO的奇妙冒险 飙马野郎",
                    episodeName = "赛道上的决意",
                    episodeSort = 4f,
                    positionMs = 1_420_000L,
                    durationMs = 1_440_000L,
                    updatedAt = now - 3 * 60 * minute,
                ),
                PlaybackHistory(
                    episodeId = 301L,
                    subjectId = 0L,
                    subjectName = "",
                    episodeName = "本地合集 EP01",
                    positionMs = 60_000L,
                    updatedAt = now - 26 * 60 * minute,
                ),
            )
        val groups = buildHistoryGroups(entries)

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HistoryContent(
                        uiState =
                            HistoryUiState(
                                history = entries,
                                groups = groups,
                                expandedSubjectIds = setOf(1L),
                            ),
                        onBackClick = {},
                        onClearAll = {},
                        onResume = {},
                        onSubjectClick = {},
                        onToggleExpanded = {},
                        onDeleteEpisode = {},
                        onDeleteGroup = {},
                        snackbarHost = {},
                        overlayHost = {},
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/history_list_states.png")
    }
}
