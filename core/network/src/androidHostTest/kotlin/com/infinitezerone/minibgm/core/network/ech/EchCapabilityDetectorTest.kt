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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertTrue(EchCapabilityDetector.isSeedHost("cloudflare-dns.com"))

        assertFalse(EchCapabilityDetector.isSeedHost("i0.hdslb.com"))
        assertFalse(EchCapabilityDetector.isSeedHost("sinaimg.cn"))

        assertTrue(EchCapabilityDetector.isEchEligible("p.sda1.dev"))
        assertFalse(EchCapabilityDetector.isEchEligible("i0.hdslb.com"))
    }

    @Test
    fun probeAsyncIfUnknown_registers_unknown_host_and_skips_seeds_ips_and_cached() =
        runTest {
            val dohJson =
                """
                {
                  "Status": 0,
                  "Answer": [
                    {
                      "name": "custom-ech-api.org.",
                      "TTL": 1,
                      "type": 65,
                      "data": "1 . alpn=\"h3,h2\" ech=\"AEX+DQBBVgAgACDGzeHda0pFVlXOa8RxU1peLnfQk0KiY+dzz2ytUPRoAwAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=\""
                    }
                  ]
                }
                """.trimIndent()
            val fakeClient =
                HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            respond(
                                content = dohJson,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/dns-json"),
                            )
                        }
                    }
                }

            // 未知域名：真正发起探测并注册动态路由
            val job = EchCapabilityDetector.probeAsyncIfUnknown("custom-ech-api.org", scope = backgroundScope, client = fakeClient)
            assertNotNull(job)
            job.join()
            assertTrue(AdaptiveDnsResolver.isEchEligible("custom-ech-api.org"))

            // 种子域名：跳过
            assertNull(EchCapabilityDetector.probeAsyncIfUnknown("bgm.tv", scope = backgroundScope, client = fakeClient))
            // IP 字面量：跳过
            assertNull(EchCapabilityDetector.probeAsyncIfUnknown("1.2.3.4", scope = backgroundScope, client = fakeClient))
            // 已有结论（含不支持结论）：跳过
            assertNull(EchCapabilityDetector.probeAsyncIfUnknown("custom-ech-api.org", scope = backgroundScope, client = fakeClient))
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

            // 3. 不支持 ECH 的域名（AliDNS 未命中 + Cloudflare DoH 未命中，共 2 次请求）
            val unsupported = EchCapabilityDetector.isEchSupported("normal-cdn.cn", client)
            assertFalse(unsupported)
            assertFalse(AdaptiveDnsResolver.isEchEligible("normal-cdn.cn"))
            kotlin.test.assertEquals(3, requestCount)
        }

    @Test
    fun isEchSupported_falls_back_to_cloudflare_doh_when_alidns_lacks_ech() =
        runTest {
            val queriedUrls = mutableListOf<String>()
            val mockEngine =
                MockEngine { request ->
                    val url = request.url.toString()
                    queriedUrls.add(url)
                    if (url.startsWith("https://cloudflare-dns.com/dns-query")) {
                        respond(
                            content =
                                """
                                {
                                  "Status": 0,
                                  "Answer": [
                                    {"name": "poisoned-domain.org.", "type": 65, "data": "1 . ech=\"clean_ech_from_cf\""}
                                  ]
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        // AliDNS 模拟被投毒：抹除 ech 参数
                        respond(
                            content = """{"Status": 0, "Answer": [{"name": "poisoned-domain.org.", "type": 65, "data": "1 . alpn=h2"}]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = HttpClient(mockEngine)

            val supported = EchCapabilityDetector.isEchSupported("poisoned-domain.org", client)
            assertTrue(supported)
            assertTrue(AdaptiveDnsResolver.isEchEligible("poisoned-domain.org"))
            kotlin.test.assertEquals(2, queriedUrls.size)
            assertTrue(queriedUrls[0].startsWith("https://223.5.5.5/resolve"))
            assertTrue(queriedUrls[1].startsWith("https://cloudflare-dns.com/dns-query"))
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
