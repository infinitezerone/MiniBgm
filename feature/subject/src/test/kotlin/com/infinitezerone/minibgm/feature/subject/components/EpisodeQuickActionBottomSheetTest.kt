package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.Episode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EpisodeQuickActionBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleEpisode =
        Episode(
            id = 404724L,
            type = 0,
            sort = 3f,
            ep = 3f,
            name = "殺し屋",
            nameCn = "杀手",
            duration = "24分",
            airdate = "2023-10-13",
            comment = 88,
            desc = "芙莉莲与菲伦在行进旅途中抵达了曾遭受魔族袭扰的村庄，在那里遇见了……",
        )

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h900dp")
    fun episode_quick_action_sheet_unwatched_portrait() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                EpisodeQuickActionBottomSheet(
                    episode = sampleEpisode,
                    isWatched = false,
                    isNextToWatch = true,
                    onDismiss = {},
                    onToggleWatched = {},
                    onSelectEpisodeForDetail = {},
                    onPlayClick = {},
                    onOpenSources = {},
                    onBatchMark = {},
                    onNavigateToPrevious = {},
                    onNavigateToNext = {},
                    currentIndex = 2,
                    totalCount = 28,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/episode_quick_action_sheet.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h900dp")
    fun episode_quick_action_sheet_watched_portrait() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                EpisodeQuickActionBottomSheet(
                    episode = sampleEpisode,
                    isWatched = true,
                    isNextToWatch = false,
                    onDismiss = {},
                    onToggleWatched = {},
                    onSelectEpisodeForDetail = {},
                    onPlayClick = {},
                    onOpenSources = {},
                    onBatchMark = {},
                    onNavigateToPrevious = {},
                    onNavigateToNext = {},
                    currentIndex = 2,
                    totalCount = 28,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/episode_quick_action_sheet_watched.png")
    }
}
