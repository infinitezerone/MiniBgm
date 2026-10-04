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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ScheduleSnapshotServiceTest {
    private val sampleSnapshotJson =
        """
        {
            "schema": "minibgm-schedule-snapshot/1",
            "generatedAt": "2026-10-04T00:00:00Z",
            "items": [
                {
                    "anilistId": 12345,
                    "bgmId": 67890,
                    "title": "测试番剧",
                    "titleCn": "测试番剧中文名",
                    "countryOfOrigin": "JP",
                    "format": "TV",
                    "status": "RELEASING",
                    "coverUrl": "https://example.com/cover.jpg",
                    "isAdult": false,
                    "startYear": 2026,
                    "startMonth": 10,
                    "airDate": "2026-10-04",
                    "sites": [
                        {"site": "bangumi", "id": "67890", "url": "https://bgm.tv/subject/67890"}
                    ],
                    "episodes": [
                        {"n": 1, "t": 1791000000}
                    ]
                }
            ]
        }
        """.trimIndent()

    private fun createClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(BgmHttpClient.jsonConfig)
            }
        }

    @Test
    fun getSnapshot_200OK_returnsModifiedWithSnapshotAndEtag() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = sampleSnapshotJson,
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ETag to listOf(""""snapshot-etag-v1""""),
                            ),
                    )
                }

            val service = ScheduleSnapshotServiceImpl(createClient(engine), listOf("https://cdn.example.com/snapshot.json"))
            val result = service.getSnapshot(ifNoneMatchEtag = null)

            assertIs<ScheduleSnapshotResult.Modified>(result)
            assertEquals("minibgm-schedule-snapshot/1", result.snapshot.schema)
            assertEquals(1, result.snapshot.items.size)
            assertEquals(
                12345L,
                result.snapshot.items
                    .first()
                    .anilistId,
            )
            assertEquals(""""snapshot-etag-v1"""", result.etag)
        }

    @Test
    fun getSnapshot_304NotModified_returnsNotModified() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                    )
                }

            val service = ScheduleSnapshotServiceImpl(createClient(engine), listOf("https://cdn.example.com/snapshot.json"))
            val result = service.getSnapshot(ifNoneMatchEtag = """"snapshot-etag-v1"""")

            assertIs<ScheduleSnapshotResult.NotModified>(result)
        }

    @Test
    fun getSnapshot_firstCdnFails_fallbackToSecondCdn_returnsModified() =
        runTest {
            val cdns = listOf("https://cdn1.example.com/snapshot.json", "https://cdn2.example.com/snapshot.json")
            val engine =
                MockEngine { request ->
                    if (request.url.toString() == cdns[0]) {
                        respond(
                            content = "Internal Server Error",
                            status = HttpStatusCode.InternalServerError,
                        )
                    } else {
                        respond(
                            content = sampleSnapshotJson,
                            status = HttpStatusCode.OK,
                            headers =
                                headersOf(
                                    HttpHeaders.ContentType to listOf("application/json"),
                                    HttpHeaders.ETag to listOf(""""second-etag""""),
                                ),
                        )
                    }
                }

            val service = ScheduleSnapshotServiceImpl(createClient(engine), cdns)
            val result = service.getSnapshot(ifNoneMatchEtag = null)

            assertIs<ScheduleSnapshotResult.Modified>(result)
            assertEquals(
                12345L,
                result.snapshot.items
                    .first()
                    .anilistId,
            )
            assertEquals(""""second-etag"""", result.etag)
        }

    @Test
    fun getSnapshot_allCdnsFail_throwsException() =
        runTest {
            val cdns = listOf("https://cdn1.example.com/snapshot.json", "https://cdn2.example.com/snapshot.json")
            val engine =
                MockEngine { _ ->
                    throw RuntimeException("Network timeout")
                }

            val service = ScheduleSnapshotServiceImpl(createClient(engine), cdns)
            val e =
                assertFailsWith<RuntimeException> {
                    service.getSnapshot(ifNoneMatchEtag = null)
                }
            assertEquals("Network timeout", e.message)
        }

    @Test
    fun getSnapshot_noArgOverload_unwrapsModifiedSnapshot() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = sampleSnapshotJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            val service = ScheduleSnapshotServiceImpl(createClient(engine), listOf("https://cdn.example.com/snapshot.json"))
            val snapshot = service.getSnapshot()

            assertEquals("minibgm-schedule-snapshot/1", snapshot.schema)
            assertEquals(1, snapshot.items.size)
            assertEquals(67890L, snapshot.items.first().bgmId)
        }

    @Test
    fun getSnapshot_noArgOverload_throwsOnUnexpectedNotModified() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(content = "", status = HttpStatusCode.NotModified)
                }

            val service = ScheduleSnapshotServiceImpl(createClient(engine), listOf("https://cdn.example.com/snapshot.json"))
            assertFailsWith<IllegalStateException> {
                service.getSnapshot()
            }
        }
}
