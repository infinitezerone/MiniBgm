package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpResponseLimitsTest {
    @Test
    fun readLimitedText_truncates_at_limit() =
        runTest {
            val client = HttpClient(MockEngine { respond("abcdef") })

            assertEquals("abcd", client.get("https://example.com/body").readLimitedText(4))
        }

    @Test
    fun readLimitedText_keeps_whole_body_when_under_limit() =
        runTest {
            val client = HttpClient(MockEngine { respond("abcdef") })

            assertEquals("abcdef", client.get("https://example.com/body").readLimitedText(64))
        }
}
