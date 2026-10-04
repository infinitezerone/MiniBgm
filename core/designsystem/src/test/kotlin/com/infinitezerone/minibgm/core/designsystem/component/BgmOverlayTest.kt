package com.infinitezerone.minibgm.core.designsystem.component

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmOverlayTest {
    private class TestAction(
        val value: String,
    ) : OverlayAction<Int>()

    @Test
    fun await_suspendsUntilCompleteIsCalled() =
        runBlocking {
            val hostState = OverlayHostState()
            val action = TestAction("hello")
            var result: Int? = null

            val job =
                launch {
                    result = hostState.await(action)
                }

            // Yield so coroutine starts and enters await
            kotlinx.coroutines.yield()

            assertEquals(action, hostState.currentAction)
            assertNull(result)

            action.complete(42)
            job.join()

            assertEquals(42, result)
            assertNull(hostState.currentAction)
        }

    @Test
    fun awaitOrNull_returnsNullOnCancel() =
        runBlocking {
            val hostState = OverlayHostState()
            val action = TestAction("test")
            var result: Int? = -1

            val job =
                launch {
                    result = hostState.awaitOrNull(action)
                }

            kotlinx.coroutines.yield()

            assertEquals(action, hostState.currentAction)

            action.cancel()
            job.join()

            assertNull(result)
            assertNull(hostState.currentAction)
        }

    @Test
    fun confirmDialogAction_completesWithBoolean() =
        runBlocking {
            val hostState = OverlayHostState()
            val action = ConfirmDialogAction(title = "确认", message = "内容")
            var result: Boolean? = null

            val job =
                launch {
                    result = hostState.await(action)
                }

            kotlinx.coroutines.yield()

            assertTrue(hostState.currentAction is ConfirmDialogAction)

            action.complete(true)
            job.join()

            assertEquals(true, result)
            assertNull(hostState.currentAction)
        }

    @Test
    fun overlayProviderScope_registersHandlersByType() {
        val scope = OverlayProviderScope()
        scope.overlay<TestAction> { }
        scope.confirmDialog()

        assertTrue(scope.handlers.containsKey(TestAction::class))
        assertTrue(scope.handlers.containsKey(ConfirmDialogAction::class))
        assertFalse(scope.handlers.containsKey(OverlayAction::class))
    }
}
