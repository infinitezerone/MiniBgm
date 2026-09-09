package com.infinitezerone.minibgm.core.common

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppResultTest {
    @Test
    fun asAppResult_success_returnsSuccess() {
        val result = asAppResult { 42 }
        assertIs<AppResult.Success<Int>>(result)
        assertEquals(42, result.data)
    }

    @Test
    fun asAppResult_genericException_returnsError() {
        val result = asAppResult { throw IllegalStateException("Boom") }
        assertIs<AppResult.Error>(result)
        assertEquals("Boom", result.message)
    }

    @Test
    fun asAppResult_cancellationException_rethrows() {
        assertFailsWith<CancellationException> {
            asAppResult { throw CancellationException("Scope cancelled") }
        }
    }

    @Test
    fun runCatchingCancellable_success_returnsSuccess() {
        val result = runCatchingCancellable { "ok" }
        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun runCatchingCancellable_genericException_returnsFailure() {
        val result = runCatchingCancellable { error("err") }
        assertTrue(result.isFailure)
    }

    @Test
    fun runCatchingCancellable_cancellationException_rethrows() {
        assertFailsWith<CancellationException> {
            runCatchingCancellable { throw CancellationException("Cancelled") }
        }
    }
}
