package com.infinitezerone.minibgm.core.designsystem.component

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmOverlayTest {
    // 纯数据类直接作为事件契约！
    private data class CustomPromptRequest(
        val message: String,
    ) : OverlayRequest<Int>

    private data class CustomNullableRequest(
        val tag: String,
    ) : OverlayRequest<String?>

    @Test
    fun request_suspendsUntilResponded() =
        runBlocking {
            val hostState = OverlayHostState()
            val request = CustomPromptRequest("hello")
            var result: Int? = null

            val job =
                launch {
                    result = hostState.request(request)
                }

            kotlinx.coroutines.yield()

            assertEquals(request, hostState.currentRequest)
            assertNull(result)

            val entry = hostState.currentEntry
            @Suppress("UNCHECKED_CAST")
            (entry as OverlayHostState.ActiveEntry<Int>).respond(100)
            job.join()

            assertEquals(100, result)
            assertNull(hostState.currentRequest)
        }

    @Test
    fun requestOrNull_returnsNullOnCancel() =
        runBlocking {
            val hostState = OverlayHostState()
            val request = CustomNullableRequest("test")
            var result: String? = "initial"

            val job =
                launch {
                    result = hostState.requestOrNull(request)
                }

            kotlinx.coroutines.yield()

            assertEquals(request, hostState.currentRequest)

            hostState.currentEntry?.cancel()
            job.join()

            assertNull(result)
            assertNull(hostState.currentRequest)
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

            assertTrue(hostState.currentRequest is ConfirmDialogAction)

            val entry = hostState.currentEntry
            @Suppress("UNCHECKED_CAST")
            (entry as OverlayHostState.ActiveEntry<Boolean>).respond(true)
            job.join()

            assertEquals(true, result)
            assertNull(hostState.currentRequest)
        }

    @Test
    fun overlayProviderScope_registersHandlersByType() {
        val scope = OverlayProviderScope()
        scope.overlay<CustomPromptRequest, Int> { req, onRespond ->
            onRespond(req.message.length)
        }
        scope.confirmDialog()

        assertTrue(scope.handlers.containsKey(CustomPromptRequest::class))
        assertTrue(scope.handlers.containsKey(ConfirmDialogAction::class))
        assertFalse(scope.handlers.containsKey(OverlayRequest::class))
    }
}
