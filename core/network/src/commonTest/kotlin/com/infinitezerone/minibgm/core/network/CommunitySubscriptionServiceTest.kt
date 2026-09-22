package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.RuleParserType
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
    fun validateAndTestSubscription_supportsDirectJsonRules() =
        runTest {
            val engine =
                MockEngine { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr.contains("test-a.org") -> respond(content = "OK", status = HttpStatusCode.OK)
                        urlStr.contains("test-b.org") -> respondError(HttpStatusCode.ServiceUnavailable)
                        else -> respond(content = "OK", status = HttpStatusCode.OK)
                    }
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = CommunitySubscriptionServiceImpl(client = client)

            val report =
                service.validateAndTestSubscription(
                    """
                    [
                        {"name": "测试动漫站A", "urlTemplate": "https://test-a.org/search?q={title}"},
                        {"name": "测试动漫站B", "urlTemplate": "https://test-b.org/play/{subjectId}"}
                    ]
                    """.trimIndent(),
                )

            assertTrue(report.isHealthy)
            assertEquals(2, report.totalRules)
            assertEquals(1, report.aliveRules)
            val sourceA = report.sources.first { it.name == "测试动漫站A" }
            val sourceB = report.sources.first { it.name == "测试动漫站B" }
            assertTrue(sourceA.isAlive)
            assertFalse(sourceB.isAlive)
        }

    @Test
    fun validateAndTestSubscription_supportsTvBoxConfiguration() =
        runTest {
            val tvBoxJson =
                """
                {
                    "sites": [
                        {
                            "key": "age_anime",
                            "name": "示例采集站",
                            "type": 0,
                            "api": "https://api.example.tv/search?query={title}",
                            "searchable": 1
                        },
                        {
                            "key": "maccms_demo",
                            "name": "第二采集站",
                            "type": 1,
                            "api": "https://api.example2.tv/provide/vod",
                            "searchable": 1
                        }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr.contains("example.tv") -> respond(content = "OK", status = HttpStatusCode.OK)
                        urlStr.contains("example2.tv") -> respond(content = "OK", status = HttpStatusCode.OK)
                        else -> respond(content = "OK", status = HttpStatusCode.OK)
                    }
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = CommunitySubscriptionServiceImpl(client = client)
            val report = service.validateAndTestSubscription(tvBoxJson)

            assertTrue(report.isHealthy)
            assertEquals(2, report.totalRules)
            assertEquals(2, report.aliveRules)
            val age = report.sources.first { it.name == "示例采集站" }
            assertEquals("https://api.example.tv/search?query={title}", age.urlTemplate)
            val maccms = report.sources.first { it.name == "第二采集站" }
            assertEquals("https://api.example2.tv/provide/vod?ac=detail&wd={title}", maccms.urlTemplate)
            // 接口端点必须落成取源规则，否则播放时专用解析器会被丢掉
            assertEquals(RuleParserType.MACCMS, maccms.parserType)
            assertEquals(PlaybackRuleKind.SOURCE, maccms.kind)
            assertEquals(PlaybackRuleKind.SOURCE, age.kind)
            assertEquals(RuleParserType.AUTO, age.parserType)
            // 没有跳过的条目
            assertEquals(0, report.skippedUnsupportedSites)
            assertEquals(0, report.skippedMalformedSites)
        }

    @Test
    fun validateAndTestSubscription_reportsSkippedSitesInsteadOfSilentlyDropping() =
        runTest {
            val tvBoxJson =
                """
                {
                    "sites": [
                        {
                            "name": "可用采集站",
                            "type": 1,
                            "api": "https://good.example.tv/provide/vod"
                        },
                        {
                            "name": "爬虫源",
                            "type": 3,
                            "ext": "https://spider.example.tv/js/decrypt.js"
                        },
                        {
                            "name": "脚本端点",
                            "type": 0,
                            "api": "https://bad.example.tv/js/spider.js"
                        },
                        {
                            "name": "无接口地址",
                            "type": 1
                        }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { _ -> respond(content = "OK", status = HttpStatusCode.OK) }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = CommunitySubscriptionServiceImpl(client = client)
            val report = service.validateAndTestSubscription(tvBoxJson)

            assertEquals(1, report.totalRules)
            assertEquals("可用采集站", report.sources.single().name)
            // 收下 1 条、跳过 3 条，且分类如实带回
            assertEquals(2, report.skippedUnsupportedSites)
            assertEquals(1, report.skippedMalformedSites)
        }

    @Test
    fun validateAndTestSubscription_explainsWhenEverySiteIsUnusable() =
        runTest {
            val tvBoxJson =
                """
                {
                    "sites": [
                        { "name": "爬虫源", "type": 3, "ext": "https://spider.example.tv/js/x.js" }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { _ -> respond(content = "OK", status = HttpStatusCode.OK) }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = CommunitySubscriptionServiceImpl(client = client)
            val report = service.validateAndTestSubscription(tvBoxJson)

            assertFalse(report.isHealthy)
            assertTrue(report.errorMessage.orEmpty().contains("爬虫/扩展源"), report.errorMessage.orEmpty())
            assertEquals(1, report.skippedUnsupportedSites)
        }

    @Test
    fun validateAndTestSubscription_supportsRuntimeHtmlWebpageSniffing() =
        runTest {
            val htmlContent =
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>示例站 动画线上看 - 官方网站</title>
                </head>
                <body>
                    <form action="/" method="get">
                        <input type="text" name="s" placeholder="搜索动画..." />
                    </form>
                </body>
                </html>
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr == "https://example.tv/" || urlStr == "https://example.tv" -> {
                            respond(
                                content = htmlContent,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                            )
                        }
                        else -> respond(content = "OK", status = HttpStatusCode.OK)
                    }
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val service = CommunitySubscriptionServiceImpl(client = client)
            val report = service.validateAndTestSubscription("https://example.tv")

            assertTrue(report.isHealthy)
            assertEquals(1, report.totalRules)
            val source = report.sources.first()
            assertEquals("示例站 动画线上看", source.name)
            assertEquals("https://example.tv/?s={title}", source.urlTemplate)
            assertTrue(source.isAlive)
        }
}
