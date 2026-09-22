package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.data.repository.ApiResponseSample
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackRuleSampleReplayer
import com.infinitezerone.minibgm.core.data.repository.PlaybackSourceVerifier
import com.infinitezerone.minibgm.core.data.repository.StreamVerification
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
import kotlinx.serialization.json.jsonArray
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
                            title = "示例站 动画",
                            sampleEpisodeUrl = "https://example.tv/12345",
                            hasSearchBox = true,
                            searchUrlPattern = "https://example.tv/?s={title}",
                        )
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.probeSiteAndFindSample("https://example.tv/", "芙莉莲")
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("true", obj["isReachable"]?.jsonPrimitive?.content)
            assertEquals("示例站 动画", obj["title"]?.jsonPrimitive?.content)
            assertEquals("https://example.tv/12345", obj["sampleEpisodeUrl"]?.jsonPrimitive?.content)
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
                                        url = "https://v.example.tv/api",
                                        method = "POST",
                                        requestHeaders = mapOf("Referer" to "https://example.tv/"),
                                        isApi = true,
                                    ),
                                ),
                            mediaSources =
                                listOf(
                                    PlayableSource(
                                        url = "https://v.example.tv/123.mp4",
                                        kind = PlaylistEntryKind.DIRECT,
                                        label = "Direct Stream",
                                        headers = mapOf("Referer" to "https://example.tv/"),
                                    ),
                                ),
                        )
                }

            val tools = PlaybackRuleDiagnosticsTools(fakeRepo)
            val output = tools.traceNetworkTraffic("https://example.tv/12345", 5)
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("true", obj["isReachable"]?.jsonPrimitive?.content)
            assertEquals("https://example.tv/12345", obj["pageUrl"]?.jsonPrimitive?.content)
            val calls = obj["calls"]?.toString() ?: ""
            assertTrue(calls.contains("https://v.example.tv/api"))
            val media = obj["mediaSources"]?.toString() ?: ""
            assertTrue(media.contains("https://v.example.tv/123.mp4"))
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

            val output = tools.proposePlaybackRule(Json.encodeToString(rule), sampleTitle = "测试动画")
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

            val output = tools.proposePlaybackRule(Json.encodeToString(rule), sampleTitle = "测试动画")
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertTrue(store.actions.value.isEmpty(), "被拒绝的规则不该进待确认队列")
        }

    @Test
    fun `recordPlaybackRuleFromTrace 用真实审计归纳骨架并参数化片名话数`() =
        runTest {
            val tools = PlaybackRuleDiagnosticsTools(RecordingResolver())
            val title = "葬送的芙莉莲"
            val encoded = PlaybackSourceRule.encodeParam(title)
            val trace =
                NetworkAuditTrace(
                    pageUrl = "https://site.example.tv/search?q=$encoded",
                    calls =
                        listOf(
                            CapturedNetworkCall(
                                url = "https://api.example.tv/vod?wd=$encoded&ep=2",
                                isApi = true,
                                requestHeaders = mapOf("Referer" to "https://site.example.tv/"),
                            ),
                        ),
                )

            val output =
                tools.recordPlaybackRuleFromTrace(
                    Json.encodeToString(trace),
                    sampleTitle = title,
                    sampleEp = 2,
                )

            val obj = json.parseToJsonElement(output).jsonObject
            assertEquals("site.example.tv", obj["ruleName"]?.jsonPrimitive?.content, "站名只从本轮 URL 里取")
            val steps = obj["steps"]?.jsonArray.orEmpty()
            assertEquals(3, steps.size)
            val apiTemplate =
                steps[1]
                    .jsonObject["urlTemplate"]
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
            assertTrue(apiTemplate.contains("{title}"), "片名要参数化，否则规则绑死在这一部番上")
            assertTrue(
                obj["notes"]?.jsonArray?.any { it.jsonPrimitive.content.contains("{ep}") } == true,
                "集数不让代码猜，但必须明确要求调用方自行替换",
            )
            assertEquals(
                "https://site.example.tv/",
                steps[1]
                    .jsonObject["headers"]
                    ?.jsonObject
                    ?.get("Referer")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `recordPlaybackRuleFromTrace 对无法解析的审计输入返回可读错误`() =
        runTest {
            val tools = PlaybackRuleDiagnosticsTools(RecordingResolver())

            val output = tools.recordPlaybackRuleFromTrace("not-json", sampleTitle = "某番")
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
        }

    @Test
    fun `recordPlaybackRuleFromTrace 把重放到的响应样本带进草案`() =
        runTest {
            val apiUrl = "https://api.example.tv/vod?wd=x"
            val tools =
                PlaybackRuleDiagnosticsTools(
                    RecordingResolver(),
                    sampleReplayer =
                        object : PlaybackRuleSampleReplayer {
                            override suspend fun replayApiSamples(trace: NetworkAuditTrace) =
                                listOf(
                                    ApiResponseSample(
                                        url = apiUrl,
                                        ok = true,
                                        bodyExcerpt = """{"list":[{"vod_play_url":"第1集${'$'}http://cdn/x.m3u8"}]}""",
                                    ),
                                )
                        },
                )
            val trace =
                NetworkAuditTrace(
                    pageUrl = "https://site.example.tv/play/1",
                    calls = listOf(CapturedNetworkCall(url = apiUrl, isApi = true)),
                )

            val output = tools.recordPlaybackRuleFromTrace(Json.encodeToString(trace), sampleTitle = "某番", sampleEp = 1)

            val samples =
                json
                    .parseToJsonElement(output)
                    .jsonObject["apiSamples"]
                    ?.jsonArray
                    .orEmpty()
            assertEquals(1, samples.size)
            assertTrue(
                samples[0]
                    .jsonObject["bodyExcerpt"]
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
                    .contains("vod_play_url"),
                "样本要原样交给模型，正则才可能照着真实字段写而不是猜字段名",
            )
        }

    @Test
    fun `proposePlaybackRule 在重跑解析不到地址时不出提案`() =
        runTest {
            val store = PendingActionStore()
            val tools = PlaybackRuleDiagnosticsTools(EmptyResolver(), pendingActionStore = store)

            val output = tools.proposePlaybackRule(Json.encodeToString(pipelineRule()), sampleTitle = "测试动画")
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertTrue(
                obj["errorMessage"]
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
                    .contains("no playable stream"),
                "被拒时要说清是「重跑没解析出地址」，否则模型不知道该改哪儿",
            )
            assertTrue(store.actions.value.isEmpty(), "解析不到地址的规则不该进待确认队列")
        }

    @Test
    fun `proposePlaybackRule 在首包断言不通过时不出提案`() =
        runTest {
            val store = PendingActionStore()
            val tools =
                PlaybackRuleDiagnosticsTools(
                    RecordingResolver(),
                    pendingActionStore = store,
                    playbackSourceVerifier = NotPlayableVerifier(),
                )

            val output = tools.proposePlaybackRule(Json.encodeToString(pipelineRule()), sampleTitle = "测试动画")
            val obj = json.parseToJsonElement(output).jsonObject

            assertEquals("false", obj["success"]?.jsonPrimitive?.content)
            assertEquals("false", obj["playbackVerified"]?.jsonPrimitive?.content)
            assertTrue(
                obj["verificationNote"]
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
                    .contains("HTTP 403"),
                "断言失败的原因要原样回给模型，它才能针对性修规则",
            )
            assertTrue(store.actions.value.isEmpty(), "首包断言不通过的规则不该进待确认队列")
        }

    /** 提案重跑所用的可执行规则：形状合法，且 fake 能解析出候选 */
    private fun pipelineRule(): PlaybackSourceRule =
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

    /** 解析一律返回空，模拟"规则跑得通但抽不到地址" */
    private class EmptyResolver : PlaybackResolverRepository {
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
        ): List<PlayableSource> = emptyList()
    }

    /** 首包断言固定判死，模拟"地址抽得到但打不开" */
    private class NotPlayableVerifier : PlaybackSourceVerifier {
        override suspend fun verify(source: PlayableSource): StreamVerification = StreamVerification.NotPlayable("首包返回 HTTP 403")
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
