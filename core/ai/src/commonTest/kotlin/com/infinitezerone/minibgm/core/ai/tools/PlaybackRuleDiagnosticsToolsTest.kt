package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackRuleDiagnosticsToolsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `inspectPageStructure 返回页面多媒体结构`() =
        runTest {
            val fakeRepo =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun inspectPage(url: String): PageInspectionResult =
                        PageInspectionResult(
                            url = url,
                            isSuccess = true,
                            title = "测试页面",
                            hasVideoTag = true,
                            videoAttrs = mapOf("data-apireq" to "%7B%22c%22%3A1%7D"),
                            iframeUrls = listOf("https://embed.example.com/1"),
                            hasMacCmsPattern = false,
                        )
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.inspectPageStructure("https://example.com/play")

            val obj = json.parseToJsonElement(output).jsonObject
            assertTrue(obj["isSuccess"]?.jsonPrimitive?.content == "true")
            assertEquals("测试页面", obj["title"]?.jsonPrimitive?.content)
            assertEquals(
                "%7B%22c%22%3A1%7D",
                obj["videoAttrs"]
                    ?.jsonObject
                    ?.get("data-apireq")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `testPlaybackRule 沙箱执行成功返回直链`() =
        runTest {
            val fakeRepo =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveRule(
                        rule: PlaybackSourceRule,
                        title: String,
                        epNumber: Float,
                        subjectId: Long,
                        episodeId: Long,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/1.mp4",
                                kind = PlaylistEntryKind.DIRECT,
                                label = "第 1 话",
                                episodeSort = 1f,
                                siteName = rule.name,
                                pageUrl = "https://example.com/play",
                                headers = mapOf("Referer" to "https://example.com/"),
                            ),
                        )
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val testRule =
                PlaybackSourceRule(
                    id = "rule_test",
                    name = "测试源",
                    urlTemplate = "https://example.com/watch?q={title}",
                    kind = PlaybackRuleKind.SOURCE,
                )
            val ruleJson = Json.encodeToString(testRule)

            val output = tools.testPlaybackRule(ruleJson = ruleJson, sampleTitle = "芙莉莲", sampleEp = 1)
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("true", obj["success"]?.jsonPrimitive?.content)
            assertEquals("测试源", obj["ruleName"]?.jsonPrimitive?.content)
            assertEquals("1", obj["streamCount"]?.jsonPrimitive?.content)
        }

    @Test
    fun `testPlaybackRule 遇到非法 JSON 优雅返回错误`() =
        runTest {
            val fakeRepo =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                    ): List<PlayableSource> = emptyList()
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.testPlaybackRule("invalid json", "芙莉莲", 1)
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertTrue(obj["errorMessage"]?.jsonPrimitive?.content?.contains("Failed to parse ruleJson") == true)
        }
}
