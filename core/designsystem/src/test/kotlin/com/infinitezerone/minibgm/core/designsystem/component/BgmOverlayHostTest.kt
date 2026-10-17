package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BgmOverlayHostTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private data object TestDialogRequest : OverlayRequest<Unit>

    @Test
    fun bgmOverlayHost_rendersNothingWhenNoActiveEntry() {
        composeTestRule.setContent {
            val hostState = rememberOverlayHostState()
            BgmOverlayHost(hostState = hostState) {
                overlay<TestDialogRequest, Unit> { _, _ ->
                    Text(text = "Overlay Visible")
                }
            }
        }

        composeTestRule.onNodeWithText("Overlay Visible").assertDoesNotExist()
    }

    @Test
    fun bgmOverlayHost_dynamicStateProviderUpdatesContent() {
        var dynamicValue by mutableStateOf("Initial")
        lateinit var coroutineScope: CoroutineScope
        val hostState = OverlayHostState()

        composeTestRule.setContent {
            coroutineScope = rememberCoroutineScope()
            BgmOverlayHost(hostState = hostState) {
                overlay<TestDialogRequest, Unit> { _, _ ->
                    Text(text = "Dynamic: $dynamicValue")
                }
            }
        }

        composeTestRule.onNodeWithText("Dynamic: Initial").assertDoesNotExist()

        // Trigger overlay via coroutine
        composeTestRule.runOnUiThread {
            coroutineScope.launch {
                hostState.await(TestDialogRequest)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Dynamic: Initial").assertIsDisplayed()

        // Mutate dynamic state while overlay is visible
        composeTestRule.runOnUiThread {
            dynamicValue = "Updated"
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Dynamic: Updated").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dynamic: Initial").assertDoesNotExist()
    }

    @Test
    fun bgmOverlayHost_lambdaProviderReadsLiveState() {
        var counter by mutableIntStateOf(1)
        lateinit var coroutineScope: CoroutineScope
        val hostState = OverlayHostState()

        fun OverlayProviderScope.testPanel(counterProvider: () -> Int) {
            overlay<TestDialogRequest, Unit> { _, _ ->
                Text(text = "Count: ${counterProvider()}")
            }
        }

        composeTestRule.setContent {
            coroutineScope = rememberCoroutineScope()
            BgmOverlayHost(hostState = hostState) {
                testPanel(counterProvider = { counter })
            }
        }

        composeTestRule.runOnUiThread {
            coroutineScope.launch {
                hostState.await(TestDialogRequest)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Count: 1").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            counter = 42
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Count: 42").assertIsDisplayed()
        composeTestRule.onNodeWithText("Count: 1").assertDoesNotExist()
    }
}
