package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AniListServiceTest {
    private fun clientWith(respondBlock: (body: String) -> Pair<HttpStatusCode, String>): HttpClient =
        HttpClient(
            MockEngine { request ->
                val bodyText =
                    (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                val (status, content) = respondBlock(bodyText)
                respond(
                    content = content,
                    status = status,
                    headers = headersOf(io.ktor.http.HttpHeaders.ContentType to listOf("application/json")),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    /** 从 GraphQL query 中提取请求的条目 ID，并构造对应的别名响应 */
    private fun respondWithAliases(bodyText: String): String {
        val ids = Regex("Media\\(id: (\\d+)\\)").findAll(bodyText).map { it.groupValues[1] }.toList()
        val aliasEntries =
            ids.mapIndexed { index, id ->
                """"s$index": {"airingSchedule": {"nodes": [{"episode": ${index + 1}, "airingAt": 1786501200}]}}"""
            }
        return """{"data": {${aliasEntries.joinToString(",")}}}"""
    }

    @Test
    fun getAiringSchedules_parsesAliasesIntoEpochEpisodes() =
        runTest {
            val client =
                clientWith { body ->
                    if (body.contains("Media(id:")) {
                        HttpStatusCode.OK to respondWithAliases(body)
                    } else {
                        HttpStatusCode.BadRequest to """{"error":"bad"}"""
                    }
                }

            val result = AniListServiceImpl(client).getAiringSchedules(listOf(189046L, 42L))

            assertEquals(2, result.size)
            val episodes = result[189046L].orEmpty()
            assertEquals(1, episodes.first().episode)
            assertEquals(1786501200L, episodes.first().airAtEpochSeconds)
            assertEquals(2, result[42L].orEmpty().first().episode)
        }

    @Test
    fun getAiringSchedules_skipsMissingSubjects() =
        runTest {
            val client =
                clientWith { body ->
                    // 第一个别名（未收录条目）返回 null，第二个正常
                    HttpStatusCode.OK to
                        """{"data": {"s0": null, "s1": {"airingSchedule": {"nodes": [{"episode": 3, "airingAt": 1786501200}]}}}}"""
                }

            val result = AniListServiceImpl(client).getAiringSchedules(listOf(111L, 222L))

            assertEquals(1, result.size)
            assertEquals(3, result[222L].orEmpty().first().episode)
        }

    @Test
    fun getAiringSchedules_chunksLargeBatches() =
        runTest {
            var requestCount = 0
            val client =
                clientWith { body ->
                    requestCount++
                    HttpStatusCode.OK to respondWithAliases(body)
                }

            val ids = (1L..5L).toList()
            val result = AniListServiceImpl(client, chunkSize = 2).getAiringSchedules(ids)

            assertEquals(3, requestCount) // 5 个条目、每批 2 个 → 3 次请求
            assertEquals(5, result.size)
            assertTrue(result.keys.all { it in ids.toSet() })
        }

    @Test
    fun getAiringSchedules_returnsEmpty_onServerError() =
        runTest {
            val client =
                clientWith { HttpStatusCode.InternalServerError to """{"errors":[{"message":"boom"}]}""" }

            val result = AniListServiceImpl(client).getAiringSchedules(listOf(189046L))

            assertTrue(result.isEmpty())
        }
}
