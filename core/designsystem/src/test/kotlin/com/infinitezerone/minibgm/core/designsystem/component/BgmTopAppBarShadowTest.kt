package com.infinitezerone.minibgm.core.designsystem.component

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 顶栏的 48dp 高度只约束 chrome 布局与天幕范围，不得成为图标阴影的裁剪边界。
 *
 * 这条像素断言直接守住「TopBar 底边下方仍有按钮阴影」的视觉契约：
 * 若有人把 [BgmTopAppBar] 的外层改回 `Surface` / `clipToBounds`，阴影会在 48dp 处
 * 突然消失，测试读取的阴影像素会退化成纯背景色。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w200dp-h200dp-mdpi", application = Application::class)
@OptIn(ExperimentalMaterial3Api::class)
class BgmTopAppBarShadowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun topBarHeightMustNotClipActionButtonShadow() {
        composeTestRule.setContent {
            MiniBgmTheme(uiStyle = AppUiStyle.Luminous) {
                BgmFloatingPageScaffold(
                    modifier = Modifier.size(200.dp),
                    topBarMode = BgmTopBarMode.Immersive,
                    topScrimHeight = 0.dp,
                    topBar = {
                        BgmTopAppBar(
                            title = { Text("时间表") },
                            actions = {
                                BgmCircleActionButton(
                                    onClick = {},
                                    icon = BgmIcons.Search,
                                    contentDescription = "search",
                                    modifier = Modifier.padding(end = 24.dp).testTag("action"),
                                )
                            },
                        )
                    },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val actionBounds = composeTestRule.onNodeWithTag("action").getBoundsInRoot()
        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()

        val density = composeTestRule.density.density
        val x = (((actionBounds.left.value + actionBounds.right.value) / 2f) * density).roundToInt()
        val shadowY = ((actionBounds.bottom.value + 8f) * density).roundToInt()
        val backgroundY = ((actionBounds.bottom.value + 48f) * density).roundToInt()

        val shadowLuminance = pixels.luminanceAt(x, shadowY)
        val backgroundLuminance = pixels.luminanceAt(x, backgroundY)
        assertTrue(
            "顶栏下缘的按钮阴影被裁剪：shadow=$shadowLuminance background=$backgroundLuminance",
            shadowLuminance < backgroundLuminance - 1.5,
        )
    }

    @Test
    fun collapsedTopBarMustConstrainActionButtonsToZeroHeight() {
        lateinit var scrollBehavior: TopAppBarScrollBehavior
        composeTestRule.setContent {
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
            MiniBgmTheme(uiStyle = AppUiStyle.Luminous) {
                BgmTopAppBar(
                    title = { Text("时间表") },
                    actions = {
                        BgmCircleActionButton(
                            onClick = {},
                            icon = BgmIcons.Search,
                            contentDescription = "search",
                            modifier = Modifier.testTag("action"),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        }

        composeTestRule.onNodeWithTag("action").assertHeightIsEqualTo(40.dp)
        composeTestRule.runOnIdle {
            scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action").assertHeightIsEqualTo(0.dp)
    }
}

private fun PixelMap.luminanceAt(
    x: Int,
    y: Int,
): Double {
    val color = this[x, y]
    return (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue) * 255.0
}
