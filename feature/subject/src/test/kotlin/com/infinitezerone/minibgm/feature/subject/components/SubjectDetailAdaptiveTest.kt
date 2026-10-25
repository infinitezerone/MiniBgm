package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.testing.data.sampleCharacterList
import com.infinitezerone.minibgm.core.testing.data.sampleEpisodeList
import com.infinitezerone.minibgm.core.testing.data.sampleLargeCharacterList
import com.infinitezerone.minibgm.core.testing.data.sampleMixedRelationList
import com.infinitezerone.minibgm.core.testing.data.samplePersonList
import com.infinitezerone.minibgm.core.testing.data.sampleRelationList
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.feature.subject.SubjectDetailTab
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubjectDetailAdaptiveTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w640dp-h360dp-land")
    fun landscape_collapsing_header_reveals_overview_content() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
                characters = sampleCharacterList,
                persons = samplePersonList,
                relations = sampleRelationList,
            )

        val headerCollapse = SubjectHeaderCollapseState()
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                var selectedTab by remember { mutableStateOf(SubjectDetailTab.OVERVIEW) }
                SubjectDetailContent(
                    displaySubject = sampleSubject,
                    fullSubject = sampleSubject,
                    subjectType = SubjectType.ANIME,
                    uiState = uiState,
                    source = "",
                    totalEpisodes = sampleEpisodeList.size,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    onOpenCollectionSheet = {},
                    onSubjectClick = {},
                    onTagClick = {},
                    onCharacterClick = {},
                    onPersonClick = {},
                    onPreviewCharacter = {},
                    onLinkClick = {},
                    onTopicClick = { _, _ -> },
                    onLoadMoreComments = {},
                    isTransitionStabilizing = false,
                    headerCollapse = headerCollapse,
                )
            }
        }

        composeTestRule.onNodeWithText("概览").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("讨论").assertIsNotSelected()
        repeat(3) {
            composeTestRule.onRoot().performTouchInput { swipeUp() }
            composeTestRule.waitForIdle()
        }
        assertEquals(headerCollapse.heightPx, headerCollapse.offsetPx, absoluteTolerance = 1f)
        composeTestRule.onNodeWithTag(SUBJECT_OVERVIEW_LIST_TEST_TAG).performScrollToNode(hasText("简介"))
        composeTestRule.onNodeWithText("简介").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun large_font_keeps_primary_actions_visible() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.6f),
            ) {
                MiniBgmTheme(darkTheme = false) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        SubjectHeaderCard(
                            subject = sampleSubject,
                            subjectType = SubjectType.ANIME,
                            totalEpisodes = 28,
                            collection = sampleUserCollection,
                            isLoggedIn = true,
                            onPlayNext = {},
                            onOpenEpisodePicker = {},
                            onIncrementWatched = {},
                            onShare = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("继续观看").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("选集").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("分享").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun large_font_full_detail_keeps_content_reachable() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
                characters = sampleCharacterList,
                persons = samplePersonList,
                relations = sampleRelationList,
            )

        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.6f),
            ) {
                MiniBgmTheme(darkTheme = false) {
                    var selectedTab by remember { mutableStateOf(SubjectDetailTab.OVERVIEW) }
                    val headerCollapse = remember { SubjectHeaderCollapseState() }
                    SubjectDetailContent(
                        displaySubject = sampleSubject,
                        fullSubject = sampleSubject,
                        subjectType = SubjectType.ANIME,
                        uiState = uiState,
                        source = "",
                        totalEpisodes = sampleEpisodeList.size,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        onOpenCollectionSheet = {},
                        onSubjectClick = {},
                        onTagClick = {},
                        onCharacterClick = {},
                        onPersonClick = {},
                        onPreviewCharacter = {},
                        onLinkClick = {},
                        onTopicClick = { _, _ -> },
                        onLoadMoreComments = {},
                        isTransitionStabilizing = false,
                        headerCollapse = headerCollapse,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("概览").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(SUBJECT_OVERVIEW_LIST_TEST_TAG)
            .performScrollToNode(hasText("关联作品", substring = true))
        composeTestRule.onNodeWithText("关联作品", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun discussion_tab_switches_to_community_content() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                var selectedTab by remember { mutableStateOf(SubjectDetailTab.OVERVIEW) }
                val headerCollapse = remember { SubjectHeaderCollapseState() }
                SubjectDetailContent(
                    displaySubject = sampleSubject,
                    fullSubject = sampleSubject,
                    subjectType = SubjectType.ANIME,
                    uiState = uiState,
                    source = "",
                    totalEpisodes = sampleEpisodeList.size,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    onOpenCollectionSheet = {},
                    onSubjectClick = {},
                    onTagClick = {},
                    onCharacterClick = {},
                    onPersonClick = {},
                    onPreviewCharacter = {},
                    onLinkClick = {},
                    onTopicClick = { _, _ -> },
                    onLoadMoreComments = {},
                    isTransitionStabilizing = false,
                    headerCollapse = headerCollapse,
                )
            }
        }

        composeTestRule.onNodeWithText("讨论").performClick()
        composeTestRule.onNodeWithText("讨论").assertIsSelected()
        composeTestRule.onNodeWithText("短评").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h700dp")
    fun tablet_episode_picker_remains_usable() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SubjectEpisodesBottomSheet(
                        subject = sampleSubject,
                        subjectType = SubjectType.ANIME,
                        uiState = uiState,
                        totalEpisodes = sampleEpisodeList.size,
                        currentEpisodes = sampleEpisodeList,
                        availableGroups = listOf(EpisodeGroup.MAIN),
                        groupedEpisodes = mapOf(EpisodeGroup.MAIN to sampleEpisodeList),
                        activeGroup = EpisodeGroup.MAIN,
                        onSelectGroup = {},
                        isGridView = true,
                        onToggleGridView = {},
                        episodeSortDescending = false,
                        hasMoreEpisodes = false,
                        isLoadingMoreEpisodes = false,
                        onToggleEpisodeSort = {},
                        onLoadMoreEpisodes = {},
                        onRetryEpisodes = {},
                        onToggleEpisodeWatched = { _, _ -> },
                        onSelectEpisode = {},
                        onOpenEpisodeDetails = {},
                        onBatchMarkEpisode = {},
                        onDismissRequest = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(sampleSubject.displayName).assertIsDisplayed()
        composeTestRule.onNodeWithText("分集列表").assertIsDisplayed()
    }

    /**
     * 收起态行为断言：混序关联（边角类别在前）必须主线优先截断，角色收起态必须
     * 纵向摘要且只显示前 4 位——锁住「API 顺序不可信」与「有限集合不做横向滚动」两条决策。
     */
    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun collapsed_reference_prioritizes_main_story_and_lists_characters() {
        // 直接渲染单个 Section，避免 LazyColumn 视口外不组合导致位置断言失效；
        // 两个 Section 同屏纵排（一次 setContent）
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        RelationsSection(
                            relations = sampleMixedRelationList,
                            onSubjectClick = {},
                            currentSubjectId = sampleSubject.id,
                            currentSubjectName = sampleSubject.displayName,
                            currentSubjectCover = sampleSubject.images?.bestImage,
                            currentSubjectScore = sampleSubject.rating?.score ?: 0.0,
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        CharactersSection(
                            characters = sampleLargeCharacterList,
                            onCharacterClick = {},
                            onActorClick = {},
                            onPreviewCharacter = {},
                        )
                    }
                }
            }
        }

        fun firstYOf(text: String): Float =
            composeTestRule
                .onAllNodesWithText(text)[0]
                .fetchSemanticsNode()
                .positionInRoot.y

        // 主线（续作/前传）必须排在边角类别（画集/原声集）之前
        val sequelY = firstYOf("续·葬送的芙莉莲")
        val prequelY = firstYOf("葬送的芙莉莲 先行篇")
        val artBookY = firstYOf("芙莉莲官方画集")
        val soundtrackY = firstYOf("芙莉莲原声集")
        assertTrue(sequelY < artBookY, "续作必须排在画集之前（主线优先）")
        assertTrue(sequelY < soundtrackY, "续作必须排在原声集之前（主线优先）")
        assertTrue(prequelY < artBookY, "前传必须排在画集之前（主线优先）")

        // 角色收起态：纵向摘要列表只显示前 4 位，第 5、6 位必须不可见
        composeTestRule.onNodeWithText("ヒンメル").assertIsDisplayed()
        composeTestRule.onNodeWithText("ザイン").assertDoesNotExist()
        composeTestRule.onNodeWithText("シュタルク").assertDoesNotExist()
    }
}
