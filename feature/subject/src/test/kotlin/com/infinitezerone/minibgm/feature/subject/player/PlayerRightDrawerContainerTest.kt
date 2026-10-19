package com.infinitezerone.minibgm.feature.subject.player

import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerRightDrawerContainerTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playerRightDrawer_animatingOut_triggersOnRespond() {
        val visibleState =
            MutableTransitionState(initialState = false).apply {
                targetState = true
            }
        var respondedCount = 0
        var dismissedCount = 0

        composeTestRule.setContent {
            PlayerRightDrawerContainer(
                visibleState = visibleState,
                onDismissRequest = { dismissedCount++ },
                onRespond = { respondedCount++ },
            ) {
                Text(text = "Drawer Content")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Drawer Content").assertIsDisplayed()
        assertEquals("初始打开状态下不应该触发 onRespond", 0, respondedCount)

        // 触发关闭
        composeTestRule.runOnUiThread {
            visibleState.targetState = false
        }
        composeTestRule.waitForIdle()

        // 退出动效结束后应自动触发 onRespond
        assertTrue("退出动画结束后 visibleState 必须是 idle 且 false", visibleState.isIdle && !visibleState.currentState)
        assertEquals("退出动效结束后必须触发 onRespond", 1, respondedCount)
    }

    @Test
    fun playerRightDrawer_backHandlerDisabledWhenTargetStateFalse() {
        val visibleState =
            MutableTransitionState(initialState = false).apply {
                targetState = true
            }
        var dismissedCount = 0
        var parentBackCount = 0

        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        dispatcher.addCallback(composeTestRule.activity) {
            parentBackCount++
        }

        composeTestRule.setContent {
            PlayerRightDrawerContainer(
                visibleState = visibleState,
                onDismissRequest = {
                    dismissedCount++
                    visibleState.targetState = false
                },
                onRespond = {},
            ) {
                Text(text = "Drawer Content")
            }
        }

        composeTestRule.waitForIdle()

        // 第一次按返回键：抽屉处于打开态 (targetState = true)，抽屉的 BackHandler 应拦截并触发 onDismissRequest
        composeTestRule.runOnUiThread {
            dispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals("第一次返回键应该触发抽屉的 dismiss", 1, dismissedCount)
        assertEquals("第一次返回键不应该穿透到底层页面", 0, parentBackCount)
        assertFalse("抽屉应该进入 targetState = false", visibleState.targetState)

        // 第二次按返回键：抽屉 targetState = false，BackHandler 必须立即禁用，返回键穿透到底层父级
        composeTestRule.runOnUiThread {
            dispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals("第二次返回键不应该再触发抽屉 dismiss", 1, dismissedCount)
        assertEquals("第二次返回键必须穿透到底层页面（退出全屏或退出页面）", 1, parentBackCount)
    }

    @Test
    fun playerRightDrawer_disposalBeforeCompletion_triggersOnRespond() {
        val visibleState =
            MutableTransitionState(initialState = false).apply {
                targetState = true
            }
        var respondedCount = 0
        var showDrawer by mutableStateOf(true)

        composeTestRule.setContent {
            if (showDrawer) {
                PlayerRightDrawerContainer(
                    visibleState = visibleState,
                    onDismissRequest = {},
                    onRespond = { respondedCount++ },
                ) {
                    Text(text = "Drawer Content")
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals("初始状态下不应触发 onRespond", 0, respondedCount)

        // 外部强制下树（如横竖屏切换/页面退出/PiP）
        composeTestRule.runOnUiThread {
            showDrawer = false
        }
        composeTestRule.waitForIdle()

        assertEquals("抽屉下树时必须触发 onRespond，避免挂起协程泄漏", 1, respondedCount)
    }
}
