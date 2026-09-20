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
    private val jsonPackage =
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

    private val gitHubSearchResponse =
        """
        {
            "items": [
                {
                    "name": "bangumi-rules",
                    "full_name": "example-user/bangumi-rules",
                    "description": "社区动漫源规则维护",
                    "default_branch": "main"
                }
            ]
        }
        """.trimIndent()

    @Test
    fun validateAndTestSubscription_probesSourcesAndCalculatesReport() =
        runTest {
            val engine =
                MockEngine { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr.contains("rules.json") -> {
                            respond(
                                content = jsonPackage,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        }
                        urlStr.contains("test-a.org") -> {
                            respond(content = "OK", status = HttpStatusCode.OK)
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

            val service = CommunitySubscriptionServiceImpl(client = client)

            val report = service.validateAndTestSubscription("https://cdn.example.com/rules.json")

            assertTrue(report.isHealthy)
            assertEquals(2, report.totalRules)
            assertEquals(1, report.aliveRules)
            val sourceA = report.sources.first { it.name == "测试动漫站A" }
            val sourceB = report.sources.first { it.name == "测试动漫站B" }
            assertTrue(sourceA.isAlive)
            assertFalse(sourceB.isAlive)
        }

    @Test
    fun searchSubscriptions_findsRepoAndValidatesCandidate() =
        runTest {
            val engine =
                MockEngine { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr.contains("api.github.com/search/repositories") -> {
                            respond(
                                content = gitHubSearchResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        }
                        urlStr.contains("example-user/bangumi-rules") -> {
                            respond(
                                content = jsonPackage,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        }
                        urlStr.contains("test-a.org") -> {
                            respond(content = "OK", status = HttpStatusCode.OK)
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

            val service = CommunitySubscriptionServiceImpl(client = client)

            val candidates = service.searchSubscriptions("bangumi-rules")

            assertEquals(1, candidates.size)
            val candidate = candidates.first()
            assertEquals("bangumi-rules", candidate.name)
            assertEquals(2, candidate.sourceCount)
            assertEquals(1, candidate.aliveCount)
            assertTrue(candidate.sampleSources.contains("测试动漫站A"))
        }

    @Test
    fun fetchAndTestCommunitySources_returnsEmptyList_whenEndpointsFail() =
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

            val service = CommunitySubscriptionServiceImpl(client = client)

            val results = service.fetchAndTestCommunitySources("https://broken.endpoint/rules.json")

            // 严格遵守零内置原则：远端失败时不注入任何硬编码站点，直接返回空列表
            assertTrue(results.isEmpty())
        }
}
