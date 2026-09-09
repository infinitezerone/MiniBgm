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
import kotlin.test.assertIs

class BangumiDataServiceTest {
    @Test
    fun getBangumiData_returnsSuccessAndEtag_on200() =
        runTest {
            val jsonBody =
                """
                {
                    "items": [
                        {
                            "title": "测试动画",
                            "sites": [{"site": "bangumi", "id": "1001"}]
                        }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { _ ->
                    respond(
                        content = jsonBody,
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ETag to listOf(""""test-etag-123""""),
                            ),
                    )
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = BangumiDataServiceImpl(client)
            val result = service.getBangumiData(etag = null)

            assertIs<BangumiDataResult.Success>(result)
            assertEquals(1, result.items.size)
            assertEquals("测试动画", result.items.first().title)
            assertEquals(""""test-etag-123"""", result.etag)
        }

    @Test
    fun getBangumiData_returnsNotModified_on304() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(""""test-etag-123"""", request.headers[HttpHeaders.IfNoneMatch])
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                    )
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = BangumiDataServiceImpl(client)
            val result = service.getBangumiData(etag = """"test-etag-123"""")

            assertIs<BangumiDataResult.NotModified>(result)
        }

    @Test
    fun getBangumiData_fallsBackToSecondaryCdn_whenPrimaryFails() =
        runTest {
            val jsonBody =
                """
                {
                    "items": [
                        {
                            "title": "备用节点动画",
                            "sites": [{"site": "bangumi", "id": "2002"}]
                        }
                    ]
                }
                """.trimIndent()

            var requestIndex = 0
            val engine =
                MockEngine { _ ->
                    requestIndex++
                    if (requestIndex == 1) {
                        throw IllegalStateException("Connection refused on primary CDN")
                    } else {
                        respond(
                            content = jsonBody,
                            status = HttpStatusCode.OK,
                            headers =
                                headersOf(
                                    HttpHeaders.ContentType to listOf("application/json"),
                                    HttpHeaders.ETag to listOf(""""secondary-etag-456""""),
                                ),
                        )
                    }
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service =
                BangumiDataServiceImpl(
                    client = client,
                    cdnUrls = listOf("https://primary.cdn/data.json", "https://secondary.cdn/data.json"),
                )
            val result = service.getBangumiData(etag = null)

            assertIs<BangumiDataResult.Success>(result)
            assertEquals(2, requestIndex)
            assertEquals(1, result.items.size)
            assertEquals("备用节点动画", result.items.first().title)
            assertEquals(""""secondary-etag-456"""", result.etag)
        }

    @Test
    fun getRecentBangumiData_fetchesMonthlySlicesAndCombines() =
        runTest {
            val monthlyJson =
                """
                [
                    {
                        "title": "一月新番",
                        "begin": "2026-01-08T15:00:00.000Z",
                        "broadcast": "R/2026-01-08T15:00:00.000Z/P7D",
                        "sites": [{"site": "bangumi", "id": "3001"}]
                    }
                ]
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.contains("2026/01.json")) {
                        respond(
                            content = monthlyJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                        )
                    } else {
                        respond(
                            content = "Not Found",
                            status = HttpStatusCode.NotFound,
                        )
                    }
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service =
                BangumiDataServiceImpl(
                    client = client,
                    cdnBases = listOf("https://test.cdn/"),
                )

            val result = service.getRecentBangumiData(year = 2026, month = 1, lookbackMonths = 1, aheadMonths = 0)

            assertIs<BangumiDataResult.Success>(result)
            assertEquals(1, result.items.size)
            assertEquals("一月新番", result.items.first().title)
            assertEquals(3001L, result.items.first().bgmSubjectId)
        }
}
