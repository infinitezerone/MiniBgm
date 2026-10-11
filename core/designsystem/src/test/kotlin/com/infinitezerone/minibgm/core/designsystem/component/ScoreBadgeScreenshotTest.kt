package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScoreBadgeScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun scoreBadge_light() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Box(modifier = Modifier.padding(16.dp)) {
                    ScoreBadge(score = 8.6)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/score_badge_light.png")
    }

    @Test
    fun scoreBadge_dark() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = true) {
                Box(modifier = Modifier.padding(16.dp)) {
                    ScoreBadge(score = 9.2)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/score_badge_dark.png")
    }
}
