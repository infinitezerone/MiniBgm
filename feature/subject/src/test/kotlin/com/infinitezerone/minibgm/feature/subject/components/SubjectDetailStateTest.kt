package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w392dp-h850dp")
class SubjectDetailStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun community_empty_states_switch_between_reviews_and_forum() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                isCommunityLoading = false,
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                CommunityTabPage(
                    uiState = uiState,
                    onLoadMoreComments = {},
                    onLinkClick = {},
                    onTopicClick = { _, _ -> },
                    onCopyComment = null,
                )
            }
        }

        composeTestRule.onNodeWithText("暂无短评", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("讨论版").performClick()
        composeTestRule.onNodeWithText("暂无相关讨论帖").assertIsDisplayed()
    }

    @Test
    fun community_loading_state_must_not_flash_empty_text() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                isCommunityLoading = true,
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                CommunityTabPage(
                    uiState = uiState,
                    onLoadMoreComments = {},
                    onLinkClick = {},
                    onTopicClick = { _, _ -> },
                    onCopyComment = null,
                )
            }
        }

        // 加载在途时禁止出现空态文案：切 Tab 首帧若未同步置在途标记，会误显「暂无短评」
        composeTestRule.onNodeWithText("暂无短评", substring = true).assertDoesNotExist()
    }

    @Test
    fun episode_error_state_exposes_retry_action() {
        var retried = false
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                episodesError = "网络异常",
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    EpisodesTabPage(
                        uiState = uiState,
                        subjectType = SubjectType.ANIME,
                        totalEpisodes = 0,
                        currentEpisodes = emptyList(),
                        availableGroups = emptyList(),
                        groupedEpisodes = emptyMap(),
                        activeGroup = EpisodeGroup.MAIN,
                        onSelectGroup = {},
                        isGridView = true,
                        onToggleGridView = {},
                        episodeSortDescending = false,
                        hasMoreEpisodes = false,
                        isLoadingMoreEpisodes = false,
                        onToggleEpisodeSort = {},
                        onLoadMoreEpisodes = {},
                        onRetryEpisodes = { retried = true },
                        onToggleEpisodeWatched = { _, _ -> },
                        onSelectEpisodeForDetail = {},
                        onEpisodeClick = null,
                        onBatchMarkEpisode = {},
                        onPlayEpisode = null,
                        onOpenSources = null,
                        onOpenEpisodeSources = null,
                        onPlayNextEpisode = null,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("分集加载失败").assertIsDisplayed()
        composeTestRule.onNodeWithText("重新加载").assertHasClickAction().performClick()
        assertTrue(retried)
    }
}
