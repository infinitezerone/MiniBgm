package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.CapturedNetworkCall
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.PipelineStep
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.ProbeSiteOutput
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.model.StepAction
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
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
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
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
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
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
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.testPlaybackRule("invalid json", "芙莉莲", 1)
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertTrue(obj["errorMessage"]?.jsonPrimitive?.content?.contains("Failed to parse ruleJson") == true)
        }

    @Test
    fun `probeSiteAndFindSample 返回站点可用性与样本页面`() =
        runTest {
            val fakeRepo =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun probeSite(
                        siteUrl: String,
                        sampleAnime: String,
                    ): ProbeSiteOutput =
                        ProbeSiteOutput(
                            siteUrl = siteUrl,
                            isReachable = true,
                            isAdParking = false,
                            title = "Anime1 动画",
                            sampleEpisodeUrl = "https://anime1.me/12345",
                            hasSearchBox = true,
                            searchUrlPattern = "https://anime1.me/?s={title}",
                        )
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.probeSiteAndFindSample("https://anime1.me/", "芙莉莲")
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("true", obj["isReachable"]?.jsonPrimitive?.content)
            assertEquals("Anime1 动画", obj["title"]?.jsonPrimitive?.content)
            assertEquals("https://anime1.me/12345", obj["sampleEpisodeUrl"]?.jsonPrimitive?.content)
            assertEquals("true", obj["hasSearchBox"]?.jsonPrimitive?.content)
        }

    @Test
    fun `traceNetworkTraffic 返回捕获的网络流量与媒体流`() =
        runTest {
            val fakeRepo =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun auditPageTraffic(
                        pageUrl: String,
                        durationMs: Long,
                    ): NetworkAuditTrace =
                        NetworkAuditTrace(
                            pageUrl = pageUrl,
                            finalUrl = pageUrl,
                            isReachable = true,
                            title = "播放详情",
                            calls =
                                listOf(
                                    CapturedNetworkCall(
                                        url = "https://v.anime1.me/api",
                                        method = "POST",
                                        requestHeaders = mapOf("Referer" to "https://anime1.me/"),
                                        isApi = true,
                                    ),
                                ),
                            mediaSources =
                                listOf(
                                    PlayableSource(
                                        url = "https://v.anime1.me/123.mp4",
                                        kind = PlaylistEntryKind.DIRECT,
                                        label = "Direct Stream",
                                        headers = mapOf("Referer" to "https://anime1.me/"),
                                    ),
                                ),
                        )
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.traceNetworkTraffic("https://anime1.me/12345", 5)
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("true", obj["isReachable"]?.jsonPrimitive?.content)
            assertEquals("https://anime1.me/12345", obj["pageUrl"]?.jsonPrimitive?.content)
            val calls = obj["calls"]?.toString() ?: ""
            assertTrue(calls.contains("https://v.anime1.me/api"))
            val media = obj["mediaSources"]?.toString() ?: ""
            assertTrue(media.contains("https://v.anime1.me/123.mp4"))
        }

    @Test
    fun `testPlaybackRule 拒绝没有执行路径的规则组合`() =
        runTest {
            val resolver = RecordingResolver()
            val tools = PlaybackRuleDiagnosticsTools(resolver)
            val rule =
                PlaybackSourceRule(
                    id = "rule_page_pipeline",
                    name = "跳转页配流水线",
                    urlTemplate = "https://example.com/search?q={title}",
                    kind = PlaybackRuleKind.PAGE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline = listOf(PipelineStep(action = StepAction.FETCH)),
                )

            val output = tools.testPlaybackRule(Json.encodeToString(rule), "芙莉莲", 1)
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertEquals(0, resolver.resolveRuleCalls, "无执行路径的规则不该进沙箱")
        }

    @Test
    fun `proposePlaybackRule 生成待确认提案并原样保留流水线`() =
        runTest {
            val store = PendingActionStore()
            val tools = PlaybackRuleDiagnosticsTools(RecordingResolver(), pendingActionStore = store)
            val rule =
                PlaybackSourceRule(
                    id = "ai-draft",
                    name = "示例站流水线",
                    urlTemplate = "https://search.example.tv/?q={title}",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline =
                        listOf(
                            PipelineStep(action = StepAction.FETCH),
                            PipelineStep(
                                action = StepAction.EXTRACT_VARIABLE,
                                regex = "data-player-req=\"([^\"]+)\"",
                                variableName = "playerReq",
                            ),
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = "\"src\"\\s*:\\s*\"([^\"]+)\"",
                            ),
                        ),
                )

            val output = tools.proposePlaybackRule(Json.encodeToString(rule))
            val proposal = json.decodeFromString<ActionProposal>(output)
            val action = proposal.action as PendingAction.ImportPlaybackRules
            val stored = action.rules.single()

            assertEquals("PENDING_CONFIRMATION", proposal.status)
            assertEquals(rule.pipeline, stored.pipeline)
            assertEquals(PlaybackRuleKind.SOURCE, stored.kind)
            assertTrue(stored.id != "ai-draft", "提案必须换发新 ID，避免与草稿撞号")
            assertEquals(listOf<PendingAction>(action), store.actions.value)
        }

    @Test
    fun `proposePlaybackRule 拒绝对不可执行的规则`() =
        runTest {
            val store = PendingActionStore()
            val tools = PlaybackRuleDiagnosticsTools(RecordingResolver(), pendingActionStore = store)
            val rule =
                PlaybackSourceRule(
                    id = "bad",
                    name = "坏组合",
                    urlTemplate = "https://example.com/s?q={title}",
                    kind = PlaybackRuleKind.PAGE,
                    parserType = RuleParserType.MACCMS,
                )

            val output = tools.proposePlaybackRule(Json.encodeToString(rule))
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertTrue(store.actions.value.isEmpty(), "被拒绝的规则不该进待确认队列")
        }

    /** 只记录取源分发是否发生，其余解析路径留空 */
    private class RecordingResolver : PlaybackResolverRepository {
        var resolveRuleCalls: Int = 0
            private set

        override suspend fun resolvePages(
            pageUrls: List<String>,
            epNumber: Float,
            siteName: String,
            title: String,
        ): List<PlayableSource> = emptyList()

        override suspend fun resolveTemplate(
            url: String,
            headers: Map<String, String>,
            epNumber: Float,
            siteName: String,
            title: String,
        ): List<PlayableSource> = emptyList()

        override suspend fun resolveRule(
            rule: PlaybackSourceRule,
            title: String,
            epNumber: Float,
            subjectId: Long,
            episodeId: Long,
        ): List<PlayableSource> {
            resolveRuleCalls++
            return listOf(
                PlayableSource(
                    url = "https://cdn.example.com/1.mp4",
                    kind = PlaylistEntryKind.DIRECT,
                    label = "第 1 话",
                    siteName = rule.name,
                ),
            )
        }
    }
}
