package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
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
import org.robolectric.annotation.GraphicsMode

/**
 * 条目详情头部与 Tab 栏 Roborazzi 截图验证：
 * 覆盖全宽操作区（杜绝截断）、评分金徽章、Tab 指示胶囊绝对居中对齐、窄屏自适应。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SubjectHeaderScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_header_card_portrait_light() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_header_card_portrait_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_header_card_portrait_dark() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = true) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_header_card_portrait_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun subject_header_card_narrow_360dp() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_header_card_narrow_360dp.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_detail_tab_bar_centered() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                val pagerState = rememberPagerState(initialPage = 0) { SubjectDetailTab.entries.size }
                SubjectDetailTabBar(
                    pagerState = pagerState,
                    collapseRatio = 0.5f,
                    onSelectTab = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_detail_tab_bar_centered.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_episodes_tab_light() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
            )
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    EpisodesTabPage(
                        uiState = uiState,
                        subjectType = SubjectType.ANIME,
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
                        onSelectEpisodeForDetail = {},
                        onEpisodeClick = {},
                        onBatchMarkEpisode = {},
                        onPlayEpisode = {},
                        onOpenSources = {},
                        onOpenEpisodeSources = {},
                        onPlayNextEpisode = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_episodes_tab_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_episode_picker_sheet_light() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
            )
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
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
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_episode_picker_sheet_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_overview_tab_light() {
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
            MiniBgmTheme(darkTheme = false) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    OverviewTabPage(
                        subject = sampleSubject,
                        displaySubject = sampleSubject,
                        uiState = uiState,
                        onTagClick = {},
                        onCharacterClick = {},
                        onActorClick = {},
                        onPreviewCharacter = {},
                        onPersonClick = {},
                        onSubjectClick = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_overview_tab_light.png")
    }

    /**
     * 收起态参考信息快照：夹具为 6 位角色 + 6 条混序关联（边角类别在主线之前），
     * 覆盖「角色纵向摘要列表 + 关联主线优先截断」的默认态——防止横向卡片长廊与裸 API 顺序回潮。
     */
    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h850dp")
    fun subject_overview_tab_collapsed_reference_light() {
        val uiState =
            SubjectDetailUiState(
                isLoading = false,
                isEpisodesLoading = false,
                collection = sampleUserCollection,
                episodes = sampleEpisodeList,
                characters = sampleLargeCharacterList,
                persons = samplePersonList,
                relations = sampleMixedRelationList,
            )
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    OverviewTabPage(
                        subject = sampleSubject,
                        displaySubject = sampleSubject,
                        uiState = uiState,
                        onTagClick = {},
                        onCharacterClick = {},
                        onActorClick = {},
                        onPreviewCharacter = {},
                        onPersonClick = {},
                        onSubjectClick = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_overview_tab_collapsed_reference_light.png")
    }
}
