package com.infinitezerone.minibgm.core.designsystem.component

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 实体顶栏不得使用 sheet / surface 的白色底，必须与页面 background 同一颜色；
 * 底边也不得额外绘制分割线。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w200dp-h200dp-mdpi", application = Application::class)
@OptIn(ExperimentalMaterial3Api::class)
class BgmTopAppBarSolidColorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun solidTopBarMustUsePageBackgroundColorWithoutHairline() {
        composeTestRule.setContent {
            MiniBgmTheme(uiStyle = AppUiStyle.Luminous) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BgmTopAppBar(
                        mode = BgmTopBarMode.Solid,
                        title = { Text("设置") },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()
        val density = composeTestRule.density.density
        val x = (180f * density).roundToInt()
        val topBarColor = pixels[x, (28f * density).roundToInt()]
        val topBarBottomColor = pixels[x, (55f * density).roundToInt()]
        val pageColor = pixels[x, (150f * density).roundToInt()]

        assertEquals(pageColor, topBarColor)
        assertEquals(pageColor, topBarBottomColor)
    }
}
