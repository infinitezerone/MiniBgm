package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BilibiliServiceTest {
    private fun clientWith(handler: (url: String) -> Pair<HttpStatusCode, String>): HttpClient =
        HttpClient(
            MockEngine { request ->
                val (status, content) = handler(request.url.toString())
                respond(
                    content = content,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    @Test
    fun getAiringEpisodes_withMediaId_resolvesSeasonAndReturnsEpisodes() =
        runTest {
            val client =
                clientWith { url ->
                    when {
                        url.contains("pgc/review/user") && url.contains("media_id=23352451") -> {
                            HttpStatusCode.OK to """{"code":0,"result":{"media":{"season_id":68585}}}"""
                        }
                        url.contains("pgc/view/web/season") && url.contains("season_id=68585") -> {
                            HttpStatusCode.OK to
                                """{
                                "code":0,
                                "result":{
                                    "episodes":[
                                        {"title":"1","pub_time":1732276800,"pv":0},
                                        {"title":"2","pub_time":1732881600,"pv":0},
                                        {"title":"PV1","pub_time":1732000000,"pv":1}
                                    ]
                                }
                            }"""
                        }
                        else -> HttpStatusCode.NotFound to "{}"
                    }
                }

            val service = BilibiliServiceImpl(client)
            val episodes = service.getAiringEpisodes("23352451")

            assertEquals(2, episodes.size)
            assertEquals(1, episodes[0].episode)
            assertEquals(1732276800L, episodes[0].airAtEpochSeconds)
            assertEquals(2, episodes[1].episode)
            assertEquals(1732881600L, episodes[1].airAtEpochSeconds)
        }

    @Test
    fun getAiringEpisodes_withSeasonIdDirectly_returnsEpisodes() =
        runTest {
            val client =
                clientWith { url ->
                    if (url.contains("season_id=68585")) {
                        HttpStatusCode.OK to
                            """{
                            "code":0,
                            "result":{
                                "episodes":[
                                    {"title":"12","pub_time":1738900000,"pv":0}
                                ]
                            }
                        }"""
                    } else {
                        HttpStatusCode.NotFound to "{}"
                    }
                }

            val service = BilibiliServiceImpl(client)
            val episodes = service.getAiringEpisodes("ss68585")

            assertEquals(1, episodes.size)
            assertEquals(12, episodes[0].episode)
            assertEquals(1738900000L, episodes[0].airAtEpochSeconds)
        }

    @Test
    fun getAiringEpisodes_withEpIdDirectly_returnsEpisodes() =
        runTest {
            val client =
                clientWith { url ->
                    if (url.contains("ep_id=12345")) {
                        HttpStatusCode.OK to
                            """{
                            "code":0,
                            "result":{
                                "episodes":[
                                    {"title":"3","pub_time":1738905000,"pv":0}
                                ]
                            }
                        }"""
                    } else {
                        HttpStatusCode.NotFound to "{}"
                    }
                }

            val service = BilibiliServiceImpl(client)
            val episodes = service.getAiringEpisodes("ep12345")

            assertEquals(1, episodes.size)
            assertEquals(3, episodes[0].episode)
            assertEquals(1738905000L, episodes[0].airAtEpochSeconds)

            // 大小写不敏感校验
            val episodesUpper = service.getAiringEpisodes("EP12345")
            assertEquals(1, episodesUpper.size)
            assertEquals(3, episodesUpper[0].episode)
        }

    @Test
    fun getAiringEpisodes_withInvalidId_returnsEmptyList() =
        runTest {
            val client = clientWith { HttpStatusCode.OK to "{}" }
            val service = BilibiliServiceImpl(client)

            assertTrue(service.getAiringEpisodes("").isEmpty())
            assertTrue(service.getAiringEpisodes("   ").isEmpty())
            assertTrue(service.getAiringEpisodes("invalid_format").isEmpty())
            assertTrue(service.getAiringEpisodes("ep_not_number").isEmpty())
            assertTrue(service.getAiringEpisodes("ss_not_number").isEmpty())
            assertTrue(service.getAiringEpisodes("md_not_number").isEmpty())
        }

    @Test
    fun getAiringEpisodes_onNetworkError_returnsEmptyList() =
        runTest {
            val client =
                clientWith { HttpStatusCode.InternalServerError to """{"code":-500}""" }

            val service = BilibiliServiceImpl(client)
            val episodes = service.getAiringEpisodes("23352451")

            assertTrue(episodes.isEmpty())
        }
}
