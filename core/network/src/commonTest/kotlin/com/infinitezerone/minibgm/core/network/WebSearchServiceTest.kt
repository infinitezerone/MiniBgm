package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSearchServiceTest {
    private fun createService(
        jsonContent: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): WebSearchService =
        WebSearchServiceImpl(
            HttpClient(
                MockEngine { _ ->
                    respond(
                        content = jsonContent,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                },
            ),
        )

    @Test
    fun search_emptyQuery_returnsEmptyList() =
        runTest {
            val service = createService("{}")
            val results = service.search("")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_success_parsesGitHubRepositoriesJson() =
        runTest {
            val ghJson =
                """
                {
                    "total_count": 2,
                    "items": [
                        {
                            "name": "minibgm",
                            "full_name": "infinitezerone/minibgm",
                            "html_url": "https://github.com/infinitezerone/minibgm",
                            "description": "Modern Bangumi Android Client",
                            "stargazers_count": 520
                        },
                        {
                            "name": "bangumi-data",
                            "full_name": "bangumi-data/bangumi-data",
                            "html_url": "https://github.com/bangumi-data/bangumi-data",
                            "description": "Anime metadata collection",
                            "stargazers_count": 3200
                        }
                    ]
                }
                """.trimIndent()

            val service = createService(ghJson)
            val results = service.search("bangumi", limit = 5)

            assertEquals(2, results.size)
            assertEquals("infinitezerone/minibgm (⭐ 520)", results[0].title)
            assertEquals("https://github.com/infinitezerone/minibgm", results[0].url)
            assertTrue(results[0].snippet.contains("Modern Bangumi Android Client"))
            assertTrue(results[0].snippet.contains("520"))

            assertEquals("bangumi-data/bangumi-data (⭐ 3200)", results[1].title)
            assertEquals("https://github.com/bangumi-data/bangumi-data", results[1].url)
        }

    @Test
    fun search_httpError_returnsEmptyList() =
        runTest {
            val service = createService("""{"message": "rate limit"}""", status = HttpStatusCode.Forbidden)
            val results = service.search("test")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_malformedJson_returnsEmptyList() =
        runTest {
            val service = createService("not valid json")
            val results = service.search("test")
            assertTrue(results.isEmpty())
        }

    @Test
    fun extractGitHubQuery_cleansQueryDirectives() {
        val service = WebSearchServiceImpl(HttpClient(MockEngine { respond("") }))

        assertEquals("bangumi-data", service.extractGitHubQuery("site:github.com bangumi-data"))
        assertEquals("anime project", service.extractGitHubQuery("github anime project"))
        assertEquals("", service.extractGitHubQuery("site:github.com"))
        assertEquals("tool", service.extractGitHubQuery("github.com tool"))
        assertEquals("media-player", service.extractGitHubQuery("media-player"))
    }
}
