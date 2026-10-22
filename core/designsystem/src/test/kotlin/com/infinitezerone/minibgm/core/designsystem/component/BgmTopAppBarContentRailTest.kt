package com.infinitezerone.minibgm.core.designsystem.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 顶栏高度与背景保持全幅，但标题/动作必须落在可选的内容列内。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h100dp-mdpi", application = Application::class)
@OptIn(ExperimentalMaterial3Api::class)
class BgmTopAppBarContentRailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun contentMaxWidthMustCenterTitleAndActions() {
        composeTestRule.setContent {
            MiniBgmTheme(uiStyle = AppUiStyle.Luminous) {
                Box(modifier = Modifier.width(320.dp), contentAlignment = Alignment.TopCenter) {
                    BgmTopAppBar(
                        contentMaxWidth = 160.dp,
                        title = {
                            Text(text = "时间表", modifier = Modifier.testTag("title"))
                        },
                        actions = {
                            Box(modifier = Modifier.size(40.dp).testTag("action"))
                        },
                    )
                }
            }
        }

        val titleBounds = composeTestRule.onNodeWithTag("title").getBoundsInRoot()
        val actionBounds = composeTestRule.onNodeWithTag("action").getBoundsInRoot()

        assertTrue("标题未进入居中内容列：${titleBounds.left}", titleBounds.left.value >= 80f)
        assertTrue("动作未进入居中内容列：${actionBounds.right}", actionBounds.right.value <= 240f)
    }
}
