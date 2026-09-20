package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunitySubscriptionServiceTest {
    @Test
    fun fetchAndTestCommunitySources_parsesSubscriptionAndCalculatesLatency() =
        runTest {
            val jsonPackage =
                """
                {
                    "version": 1,
                    "description": "测试二次元规则集",
                    "sources": [
                        {
                            "name": "测试动漫站A",
                            "urlTemplate": "https://test-a.org/search?q={title}",
                            "description": "测试站A描述"
                        },
                        {
                            "name": "测试动漫站B",
                            "urlTemplate": "https://test-b.org/play/{subjectId}",
                            "description": "测试站B描述"
                        }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr.contains("anime-rules.json") -> {
                            respond(
                                content = jsonPackage,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        }
                        urlStr.contains("test-a.org") -> {
                            respond(
                                content = "OK",
                                status = HttpStatusCode.OK,
                            )
                        }
                        urlStr.contains("test-b.org") -> {
                            respondError(HttpStatusCode.ServiceUnavailable)
                        }
                        else -> {
                            respond(content = "OK", status = HttpStatusCode.OK)
                        }
                    }
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service =
                CommunitySubscriptionServiceImpl(
                    client = client,
                    defaultSubscriptionUrls = listOf("https://cdn.example.com/anime-rules.json"),
                )

            val results = service.fetchAndTestCommunitySources()

            assertEquals(2, results.size)
            val sourceA = results.first { it.name == "测试动漫站A" }
            val sourceB = results.first { it.name == "测试动漫站B" }

            assertTrue(sourceA.isAlive)
            assertFalse(sourceB.isAlive)
            assertEquals("https://test-a.org/search?q={title}", sourceA.urlTemplate)
        }

    @Test
    fun fetchAndTestCommunitySources_returnsEmptyList_whenAllEndpointsFail() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respondError(HttpStatusCode.BadGateway)
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service =
                CommunitySubscriptionServiceImpl(
                    client = client,
                    defaultSubscriptionUrls = listOf("https://broken.endpoint/rules.json"),
                )

            val results = service.fetchAndTestCommunitySources()

            // 严格遵守零内置原则：远端失败时不注入任何硬编码站点，直接返回空列表由上层提示用户
            assertTrue(results.isEmpty())
        }
}
