package com.infinitezerone.minibgm.core.network.ech

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EchCapabilityDetectorTest {
    @BeforeTest
    fun setUp() {
        EchCapabilityDetector.resetForTest()
        AdaptiveDnsResolver.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        EchCapabilityDetector.resetForTest()
        AdaptiveDnsResolver.resetForTest()
    }

    @Test
    fun seed_hosts_are_immediately_recognized() {
        assertTrue(EchCapabilityDetector.isSeedHost("bgm.tv"))
        assertTrue(EchCapabilityDetector.isSeedHost("lain.bgm.tv"))
        assertTrue(EchCapabilityDetector.isSeedHost("api.bgm.tv"))
        assertTrue(EchCapabilityDetector.isSeedHost("anilist.co"))
        assertTrue(EchCapabilityDetector.isSeedHost("graphql.anilist.co"))
        assertTrue(EchCapabilityDetector.isSeedHost("sda1.dev"))
        assertTrue(EchCapabilityDetector.isSeedHost("p.sda1.dev"))

        assertFalse(EchCapabilityDetector.isSeedHost("i0.hdslb.com"))
        assertFalse(EchCapabilityDetector.isSeedHost("sinaimg.cn"))
    }

    @Test
    fun parseDohResponseHasEch_correctly_detects_ech_attribute() {
        val jsonWithEch =
            """
            {
              "Status": 0,
              "Question": [{"name": "p.sda1.dev.", "type": 65}],
              "Answer": [
                {
                  "name": "p.sda1.dev.",
                  "TTL": 1,
                  "type": 65,
                  "data": "1 . alpn=\"h3,h2\" ech=\"AEX+DQBBVgAgACDGzeHda0pFVlXOa8RxU1peLnfQk0KiY+dzz2ytUPRoAwAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=\""
                }
              ]
            }
            """.trimIndent()

        val jsonWithoutEch =
            """
            {
              "Status": 0,
              "Question": [{"name": "i0.hdslb.com.", "type": 65}],
              "Answer": [
                {
                  "name": "i0.hdslb.com.",
                  "TTL": 189,
                  "type": 5,
                  "data": "i0.hdslb.com.w.kunlunno.com."
                }
              ]
            }
            """.trimIndent()

        val invalidJson = "{ invalid json }"

        assertTrue(EchCapabilityDetector.parseDohResponseHasEch(jsonWithEch))
        assertFalse(EchCapabilityDetector.parseDohResponseHasEch(jsonWithoutEch))
        assertFalse(EchCapabilityDetector.parseDohResponseHasEch(invalidJson))
    }

    @Test
    fun isEchSupported_queries_doh_and_caches_result() =
        runTest {
            var requestCount = 0
            val mockEngine =
                MockEngine { request ->
                    requestCount++
                    val host = request.url.parameters["name"]
                    if (host == "custom-ech-image.org") {
                        respond(
                            content =
                                """
                                {
                                  "Status": 0,
                                  "Answer": [
                                    {"name": "custom-ech-image.org.", "type": 65, "data": "1 . ech=\"valid_ech_bytes\""}
                                  ]
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content = """{"Status": 0, "Answer": []}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = HttpClient(mockEngine)

            // 1. 初次查询：支持 ECH
            val supported = EchCapabilityDetector.isEchSupported("custom-ech-image.org", client)
            assertTrue(supported)
            assertTrue(AdaptiveDnsResolver.isEchEligible("custom-ech-image.org"))
            assertTrue(AdaptiveDnsResolver.isCloudflareHosted("custom-ech-image.org"))
            kotlin.test.assertEquals(1, requestCount)

            // 2. 二次查询：命中缓存，不发起新网络请求
            val cachedSupported = EchCapabilityDetector.isEchSupported("custom-ech-image.org", client)
            assertTrue(cachedSupported)
            kotlin.test.assertEquals(1, requestCount)

            // 3. 不支持 ECH 的域名
            val unsupported = EchCapabilityDetector.isEchSupported("normal-cdn.cn", client)
            assertFalse(unsupported)
            assertFalse(AdaptiveDnsResolver.isEchEligible("normal-cdn.cn"))
            kotlin.test.assertEquals(2, requestCount)
        }

    @Test
    fun isEchSupported_fails_open_on_network_error() =
        runTest {
            val failingMockEngine =
                MockEngine {
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val client = HttpClient(failingMockEngine)

            val result = EchCapabilityDetector.isEchSupported("flaky-cdn.net", client)
            assertFalse(result)
            assertFalse(AdaptiveDnsResolver.isEchEligible("flaky-cdn.net"))
        }
}
