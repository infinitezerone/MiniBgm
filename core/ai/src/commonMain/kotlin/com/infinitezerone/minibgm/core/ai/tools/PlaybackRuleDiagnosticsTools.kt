package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.ai.aiJson
import com.infinitezerone.minibgm.core.ai.tool.BgmTool
import com.infinitezerone.minibgm.core.ai.tool.bgmTool
import com.infinitezerone.minibgm.core.ai.tool.int
import com.infinitezerone.minibgm.core.ai.tool.schemaObject
import com.infinitezerone.minibgm.core.ai.tool.schemaProperty
import com.infinitezerone.minibgm.core.ai.tool.string
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackRuleRecorder
import com.infinitezerone.minibgm.core.data.repository.PlaybackRuleSampleReplayer
import com.infinitezerone.minibgm.core.data.repository.PlaybackSourceVerifier
import com.infinitezerone.minibgm.core.data.repository.StreamVerification
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleApi
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
internal data class RuleTestOutput(
    val success: Boolean,
    val ruleName: String,
    val streamCount: Int = 0,
    val streams: List<StreamOutput> = emptyList(),
    val errorMessage: String? = null,
    /** 首包断言结论——"解析跑通"不等于"地址能播"，这一项才是能播的证据 */
    val playbackVerified: Boolean = false,
    val verificationNote: String? = null,
)

@Serializable
internal data class StreamOutput(
    val url: String,
    val label: String,
    val headers: Map<String, String>,
)

/**
 * 播放源规则诊断与探查工具集，供 AI Assistant 分析网页结构与测试声明式播放规则。
 */
class PlaybackRuleDiagnosticsTools(
    private val playbackResolverRepository: PlaybackResolverRepository,
    private val json: Json = aiJson,
    private val pendingActionStore: PendingActionStore? = null,
    private val playbackSourceVerifier: PlaybackSourceVerifier? = null,
    private val sampleReplayer: PlaybackRuleSampleReplayer? = null,
) {
    fun tools(): List<BgmTool> =
        listOf(
            bgmTool(
                name = "probeSiteAndFindSample",
                description =
                    "Probe a website's health, title, search parameters, and automatically find a sample playable episode page URL. " +
                        "Use this as the FIRST STEP when adapting or reversing a new playback website.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "siteUrl",
                                    schemaProperty(
                                        "string",
                                        "The base HTTP/HTTPS URL of the target anime website, e.g. https://example.com/",
                                    ),
                                )
                                put(
                                    "sampleAnime",
                                    schemaProperty(
                                        "string",
                                        "An anime title KNOWN to be listed on that site — pass the title the user is " +
                                            "actually looking for (its aliases work too). Leave empty only when nothing is known; " +
                                            "the probe then picks a real entry from the site's own home page as the sample.",
                                    ),
                                )
                            },
                        required = listOf("siteUrl"),
                    ),
            ) { args ->
                probeSiteAndFindSample(
                    siteUrl = args.string("siteUrl"),
                    sampleAnime = args.string("sampleAnime"),
                )
            },
            bgmTool(
                name = "traceNetworkTraffic",
                description =
                    "Execute dynamic headless network traffic auditing on a playback page URL. " +
                        "Renders the page in an isolated environment, triggers video playback, " +
                        "and captures all video media streams (.m3u8, .mp4) " +
                        "and intermediate XHR/Fetch API calls with their request headers and cookies. " +
                        "Use this to observe what requests the site makes to resolve the actual video stream.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "playbackPageUrl",
                                    schemaProperty("string", "The absolute URL of the playback episode page to audit"),
                                )
                                put(
                                    "durationSeconds",
                                    schemaProperty("integer", "Audit duration in seconds (defaults to 8, min 3, max 15)"),
                                )
                            },
                        required = listOf("playbackPageUrl"),
                    ),
            ) { args ->
                traceNetworkTraffic(
                    playbackPageUrl = args.string("playbackPageUrl"),
                    durationSeconds = args.int("durationSeconds", 8),
                )
            },
            bgmTool(
                name = "inspectPageStructure",
                description =
                    "Inspect the multimedia structure of a web page (e.g. video tags, custom attributes like data-apireq, " +
                        "iframes, MacCMS patterns, response headers). Use this to analyze a website when creating or debugging playback rules.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put("url", schemaProperty("string", "The absolute HTTP/HTTPS URL of the target web page to inspect"))
                            },
                        required = listOf("url"),
                    ),
            ) { args ->
                inspectPageStructure(args.string("url"))
            },
            bgmTool(
                name = "recordPlaybackRuleFromTrace",
                description =
                    "Turn a network audit trace (what traceNetworkTraffic just returned) into a rule skeleton. " +
                        "It extracts the REAL request sequence (page -> API calls -> media) with the exact URLs, methods " +
                        "and replayable headers that were observed, substituting {title}/{ep} into them. " +
                        "Call this INSTEAD OF inventing an interface URL from scratch. " +
                        "It also REPLAYS the audited GET API calls (same site only) and returns their real response " +
                        "bodies in apiSamples — write the EXTRACT_STREAM regex against that sample, do not invent field " +
                        "names. EXTRACT_STREAM is list-aware: capture the stream URL in group 1 and optionally the " +
                        "episode label text in group 2; the engine picks the requested episode from all candidates — " +
                        "do not anchor {ep} into the regex. POST calls and requests that only fire after a click cannot " +
                        "be replayed; the notes say so. After filling the regex, run testPlaybackRule.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "traceJson",
                                    schemaProperty("string", "JSON string previously returned by traceNetworkTraffic"),
                                )
                                put(
                                    "sampleTitle",
                                    schemaProperty(
                                        "string",
                                        "The anime title used during tracing; substituted as {title} in the skeleton",
                                    ),
                                )
                                put(
                                    "sampleEp",
                                    schemaProperty("integer", "Episode number substituted as {ep}; pass 0 to omit"),
                                )
                            },
                        required = listOf("traceJson", "sampleTitle"),
                    ),
            ) { args ->
                recordPlaybackRuleFromTrace(
                    traceJson = args.string("traceJson"),
                    sampleTitle = args.string("sampleTitle"),
                    sampleEp = args.int("sampleEp", 1),
                )
            },
            bgmTool(
                name = "recordPlaybackRuleFromStaticPage",
                description =
                    "Induce a playback rule skeleton by reading the STATIC page source of a playback page URL " +
                        "(one plain HTTP fetch, no browser). If the media URL (.m3u8/.mp4/...) is present in the " +
                        "HTML, the draft carries the exact source-code context around it — write the EXTRACT_STREAM " +
                        "regex against that context, do not invent field names. If the page carries multiple media " +
                        "URLs (episodes or lines), capture the stream URL in regex group 1 and the episode label in " +
                        "group 2 — the engine picks the requested episode from all candidates. TRY THIS FIRST for ordinary sites: " +
                        "it is much cheaper and faster than traceNetworkTraffic. Fall back to traceNetworkTraffic " +
                        "only when the draft notes say the page is JS-rendered or the stream only appears at runtime. " +
                        "After filling the regex, run testPlaybackRule.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "playbackPageUrl",
                                    schemaProperty("string", "The absolute URL of the playback episode page"),
                                )
                                put(
                                    "sampleTitle",
                                    schemaProperty(
                                        "string",
                                        "The anime title used during recording; substituted as {title} in the skeleton",
                                    ),
                                )
                                put(
                                    "sampleEp",
                                    schemaProperty("integer", "Episode number substituted as {ep}; pass 0 to omit"),
                                )
                            },
                        required = listOf("playbackPageUrl", "sampleTitle"),
                    ),
            ) { args ->
                recordPlaybackRuleFromStaticPage(
                    playbackPageUrl = args.string("playbackPageUrl"),
                    sampleTitle = args.string("sampleTitle"),
                    sampleEp = args.int("sampleEp", 1),
                )
            },
            bgmTool(
                name = "testPlaybackRule",
                description =
                    "Dry-run and test a PlaybackSourceRule in a sandboxed execution environment. " +
                        "Pass the complete JSON string of PlaybackSourceRule together with a sample anime title and episode number. " +
                        "Returns whether a valid stream URL and headers were successfully resolved.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put("ruleJson", schemaProperty("string", "Serialized JSON string of the PlaybackSourceRule to test"))
                                put("sampleTitle", schemaProperty("string", "Sample anime title to substitute into {title}"))
                                put("sampleEp", schemaProperty("integer", "Sample episode number to substitute into {ep}, e.g. 1"))
                            },
                        required = listOf("ruleJson", "sampleTitle"),
                    ),
            ) { args ->
                testPlaybackRule(
                    ruleJson = args.string("ruleJson"),
                    sampleTitle = args.string("sampleTitle"),
                    sampleEp = args.int("sampleEp", 1),
                )
            },
            bgmTool(
                name = "proposePlaybackRule",
                description =
                    "Propose a PlaybackSourceRule for the user to import into their local playback rules. " +
                        "REQUIRES sampleTitle/sampleEp: this tool re-runs the rule itself and probes the resolved " +
                        "stream's first response, so the proposal carries real evidence rather than your claim. " +
                        "If it resolves nothing, or the address turns out not to be playable, NO proposal is created — " +
                        "fix the rule and try again. This does NOT store anything: it returns a PENDING_CONFIRMATION " +
                        "proposal, and the rule reaches the user's settings only if they approve the card.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "ruleJson",
                                    schemaProperty(
                                        "string",
                                        "Serialized JSON string of the PlaybackSourceRule to propose for import",
                                    ),
                                )
                                put(
                                    "sampleTitle",
                                    schemaProperty(
                                        "string",
                                        "Sample anime title to substitute into {title} while re-running the rule",
                                    ),
                                )
                                put(
                                    "sampleEp",
                                    schemaProperty("integer", "Sample episode number to substitute into {ep}, e.g. 1"),
                                )
                            },
                        required = listOf("ruleJson", "sampleTitle"),
                    ),
            ) { args ->
                proposePlaybackRule(
                    ruleJson = args.string("ruleJson"),
                    sampleTitle = args.string("sampleTitle"),
                    sampleEp = args.int("sampleEp", 1),
                )
            },
        )

    suspend fun probeSiteAndFindSample(
        siteUrl: String,
        sampleAnime: String = "",
    ): String {
        val result = playbackResolverRepository.probeSite(siteUrl, sampleAnime)
        return json.encodeToString(result)
    }

    suspend fun traceNetworkTraffic(
        playbackPageUrl: String,
        durationSeconds: Int = 8,
    ): String {
        val boundedDuration = durationSeconds.coerceIn(3, 15) * 1000L
        val result = playbackResolverRepository.auditPageTraffic(playbackPageUrl, boundedDuration)
        return json.encodeToString(result)
    }

    suspend fun inspectPageStructure(url: String): String {
        val result: PageInspectionResult = playbackResolverRepository.inspectPage(url)
        return json.encodeToString(result)
    }

    suspend fun recordPlaybackRuleFromTrace(
        traceJson: String,
        sampleTitle: String,
        sampleEp: Int = 1,
    ): String {
        AiToolActivity.report("录制规则骨架", "归纳审计到的真实请求序列")
        val trace =
            try {
                json.decodeFromString<NetworkAuditTrace>(traceJson)
            } catch (e: Exception) {
                return json.encodeToString(
                    RuleTestOutput(
                        success = false,
                        ruleName = "Unknown",
                        errorMessage = "Failed to parse traceJson: ${e.message}",
                    ),
                )
            }
        // 先重放取样、再归纳：录制器保持纯函数，IO 全部发生在这一层
        val samples = sampleReplayer?.replayApiSamples(trace).orEmpty()
        val draft =
            PlaybackRuleRecorder.recordFromTrace(
                trace = trace,
                ruleName = ruleNameFromTrace(trace),
                title = sampleTitle,
                ep = if (sampleEp > 0) sampleEp.toString() else "",
                apiSamples = samples,
            )
        return json.encodeToString(draft)
    }

    private fun ruleNameFromTrace(trace: NetworkAuditTrace): String = ruleNameFromUrl(trace.finalUrl.ifBlank { trace.pageUrl })

    private fun ruleNameFromUrl(url: String): String =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .ifBlank { "录制规则" }

    suspend fun recordPlaybackRuleFromStaticPage(
        playbackPageUrl: String,
        sampleTitle: String,
        sampleEp: Int = 1,
    ): String {
        AiToolActivity.report("静态页录制", "直读播放页源码归纳规则")
        val draft =
            playbackResolverRepository.recordRuleFromStaticPage(
                pageUrl = playbackPageUrl,
                ruleName = ruleNameFromUrl(playbackPageUrl),
                sampleTitle = sampleTitle,
                sampleEp = if (sampleEp > 0) sampleEp.toString() else "",
            ) ?: return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = ruleNameFromUrl(playbackPageUrl),
                    errorMessage =
                        "Static fetch failed (unreachable, rejected, or empty body). " +
                            "Fall back to traceNetworkTraffic.",
                ),
            )
        return json.encodeToString(draft)
    }

    suspend fun testPlaybackRule(
        ruleJson: String,
        sampleTitle: String,
        sampleEp: Int = 1,
    ): String {
        val rule =
            try {
                json.decodeFromString<PlaybackSourceRule>(ruleJson)
            } catch (e: Exception) {
                return json.encodeToString(
                    RuleTestOutput(
                        success = false,
                        ruleName = "Unknown",
                        errorMessage = "Failed to parse ruleJson: ${e.message}",
                    ),
                )
            }

        if (!rule.isImportable) {
            return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    errorMessage =
                        "Rule shape is not executable: parserType=${rule.parserType} 与 pipeline 只在 " +
                            "kind=SOURCE 的规则上生效，请修正 kind/parserType 组合后重试。" +
                            "若 minClientApi=${rule.minClientApi} 超出本客户端能力级别（${PlaybackRuleApi.SUPPORTED_RULE_API}），" +
                            "本条规则无法被导入或执行，请降级要求或改写为当前版本语义。",
                ),
            )
        }

        return try {
            val sources =
                playbackResolverRepository.resolveRule(
                    rule = rule,
                    title = sampleTitle,
                    epNumber = if (sampleEp > 0) sampleEp.toFloat() else 0f,
                )
            if (sources.isNotEmpty()) {
                val verification = verifyFirstPlayable(sources)
                json.encodeToString(
                    RuleTestOutput(
                        success = true,
                        ruleName = rule.name,
                        streamCount = sources.size,
                        streams =
                            sources.map {
                                StreamOutput(
                                    url = it.url,
                                    label = it.label,
                                    headers = it.headers,
                                )
                            },
                        playbackVerified = verification is StreamVerification.Playable,
                        verificationNote = verification.note(),
                    ),
                )
            } else {
                json.encodeToString(
                    RuleTestOutput(
                        success = false,
                        ruleName = rule.name,
                        errorMessage = "Rule executed but no playable stream URL was resolved.",
                    ),
                )
            }
        } catch (e: Exception) {
            json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    errorMessage = "Execution error: ${e.message}",
                ),
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun proposePlaybackRule(
        ruleJson: String,
        sampleTitle: String,
        sampleEp: Int = 1,
    ): String {
        AiToolActivity.report("生成导入提案", "重跑验证后等待用户确认")
        val rule =
            try {
                json.decodeFromString<PlaybackSourceRule>(ruleJson)
            } catch (e: Exception) {
                return json.encodeToString(
                    RuleTestOutput(
                        success = false,
                        ruleName = "Unknown",
                        errorMessage = "Failed to parse ruleJson: ${e.message}",
                    ),
                )
            }

        if (!rule.isImportable) {
            return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    errorMessage =
                        "Rule is not importable (kind=${rule.kind}, parserType=${rule.parserType}, " +
                            "minClientApi=${rule.minClientApi}, supported=${PlaybackRuleApi.SUPPORTED_RULE_API}); " +
                            "fix the shape or lower the required API level, then re-run testPlaybackRule before proposing.",
                ),
            )
        }

        // 提案必须自带证据：不采信"我刚才测过了"这种说法，这里自己重跑一遍
        val sources =
            runCatching {
                playbackResolverRepository.resolveRule(
                    rule = rule,
                    title = sampleTitle,
                    epNumber = if (sampleEp > 0) sampleEp.toFloat() else 0f,
                )
            }.getOrElse { e ->
                return json.encodeToString(
                    RuleTestOutput(
                        success = false,
                        ruleName = rule.name,
                        errorMessage = "Re-running the rule failed: ${e.message}",
                    ),
                )
            }

        if (sources.isEmpty()) {
            return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    errorMessage =
                        "Re-run resolved no playable stream for sample \"$sampleTitle\" ep $sampleEp. " +
                            "Fix the rule, or pick a sample that actually exists on the site, then retry.",
                ),
            )
        }

        val verification = verifyFirstPlayable(sources)
        if (verification is StreamVerification.NotPlayable) {
            return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    streamCount = sources.size,
                    playbackVerified = false,
                    verificationNote = verification.reason,
                    errorMessage =
                        "The rule does resolve an address, but the first response says it is not playable: " +
                            "${verification.reason}. Fix the rule and re-run testPlaybackRule.",
                ),
            )
        }

        val action =
            PendingAction.ImportPlaybackRules(
                actionId = "act_rules_${TimeUtils.nowEpochMillis()}",
                sourceName = rule.name,
                rules =
                    listOf(
                        rule.copy(
                            id = Uuid.random().toString(),
                            isEnabled = true,
                        ),
                    ),
                description = "导入规则【${rule.name}】（解析器 ${rule.parserType}）",
            )
        pendingActionStore?.add(action)
        val evidence =
            if (verification is StreamVerification.Playable) {
                "规则已重跑，首包断言通过（响应正常且类型为媒体）"
            } else {
                "规则已重跑并解析出 ${sources.size} 个候选；首包未能探测（当前环境不支持），未做可播性断言"
            }
        return json.encodeToString(
            ActionProposal(
                message = "$evidence，等待用户确认后写入本地播放源。",
                action = action,
            ),
        )
    }

    private suspend fun verifyFirstPlayable(sources: List<PlayableSource>): StreamVerification? {
        val verifier = playbackSourceVerifier ?: return null
        val first = sources.firstOrNull { it.kind == PlaylistEntryKind.DIRECT } ?: return null
        return verifier.verify(first)
    }
}

private fun StreamVerification?.note(): String? =
    when (this) {
        null, StreamVerification.Unverified ->
            "首包未探测（当前环境不支持），导入前建议先手动试播一次"
        StreamVerification.Playable -> null
        is StreamVerification.NotPlayable -> reason
    }
