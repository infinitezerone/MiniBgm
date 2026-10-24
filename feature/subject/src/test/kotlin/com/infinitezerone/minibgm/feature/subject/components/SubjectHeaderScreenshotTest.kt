package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.feature.subject.SubjectDetailTab
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
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                ) {
                    SubjectHeaderCard(
                        subject = sampleSubject,
                        subjectType = SubjectType.ANIME,
                        totalEpisodes = 28,
                        collection = sampleUserCollection,
                        isLoggedIn = true,
                        onPlayNext = {},
                        onOpenSources = {},
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
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                ) {
                    SubjectHeaderCard(
                        subject = sampleSubject,
                        subjectType = SubjectType.ANIME,
                        totalEpisodes = 28,
                        collection = sampleUserCollection,
                        isLoggedIn = true,
                        onPlayNext = {},
                        onOpenSources = {},
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
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                ) {
                    SubjectHeaderCard(
                        subject = sampleSubject,
                        subjectType = SubjectType.ANIME,
                        totalEpisodes = 28,
                        collection = sampleUserCollection,
                        isLoggedIn = true,
                        onPlayNext = {},
                        onOpenSources = {},
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
                val pagerState = rememberPagerState(initialPage = 1) { 3 }
                SubjectDetailTabBar(
                    selectedTab = SubjectDetailTab.EPISODES,
                    pagerState = pagerState,
                    collapseRatio = 0.5f,
                    subjectType = SubjectType.ANIME,
                    totalEpisodes = 28,
                    currentEpisodeCount = 28,
                    commentTotal = 150,
                    onSelectTab = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/subject_detail_tab_bar_centered.png")
    }
}
