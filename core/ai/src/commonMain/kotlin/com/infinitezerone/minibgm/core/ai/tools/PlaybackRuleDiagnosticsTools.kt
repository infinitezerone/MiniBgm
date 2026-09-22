package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.PendingActionStore
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
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val json: Json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
    private val pendingActionStore: PendingActionStore? = null,
    private val playbackSourceVerifier: PlaybackSourceVerifier? = null,
    private val sampleReplayer: PlaybackRuleSampleReplayer? = null,
) : ToolSet {
    @Tool
    @LLMDescription(
        "Probe a website's health, title, search parameters, and automatically find a sample playable episode page URL. " +
            "Use this as the FIRST STEP when adapting or reversing a new playback website.",
    )
    suspend fun probeSiteAndFindSample(
        @LLMDescription("The base HTTP/HTTPS URL of the target anime website, e.g. https://example.com/")
        siteUrl: String,
        @LLMDescription("Sample anime title to search or match, defaults to '芙莉莲'")
        sampleAnime: String = "芙莉莲",
    ): String {
        val result = playbackResolverRepository.probeSite(siteUrl, sampleAnime)
        return json.encodeToString(result)
    }

    @Tool
    @LLMDescription(
        "Execute dynamic headless network traffic auditing on a playback page URL. " +
            "Renders the page in an isolated environment, triggers video playback, and captures all video media streams (.m3u8, .mp4) " +
            "and intermediate XHR/Fetch API calls with their request headers and cookies. " +
            "Use this to observe what requests the site makes to resolve the actual video stream.",
    )
    suspend fun traceNetworkTraffic(
        @LLMDescription("The absolute URL of the playback episode page to audit")
        playbackPageUrl: String,
        @LLMDescription("Audit duration in seconds (defaults to 8, min 3, max 15)")
        durationSeconds: Int = 8,
    ): String {
        val boundedDuration = durationSeconds.coerceIn(3, 15) * 1000L
        val result = playbackResolverRepository.auditPageTraffic(playbackPageUrl, boundedDuration)
        return json.encodeToString(result)
    }

    @Tool
    @LLMDescription(
        "Inspect the multimedia structure of a web page (e.g. video tags, custom attributes like data-apireq, " +
            "iframes, MacCMS patterns, response headers). Use this to analyze a website when creating or debugging playback rules.",
    )
    suspend fun inspectPageStructure(
        @LLMDescription("The absolute HTTP/HTTPS URL of the target web page to inspect")
        url: String,
    ): String {
        val result: PageInspectionResult = playbackResolverRepository.inspectPage(url)
        return json.encodeToString(result)
    }

    @Tool
    @LLMDescription(
        "Turn a network audit trace (what traceNetworkTraffic just returned) into a rule skeleton. " +
            "It extracts the REAL request sequence (page -> API calls -> media) with the exact URLs, methods " +
            "and replayable headers that were observed, substituting {title}/{ep} into them. " +
            "Call this INSTEAD OF inventing an interface URL from scratch. " +
            "It also REPLAYS the audited GET API calls (same site only) and returns their real response " +
            "bodies in apiSamples — write the EXTRACT_STREAM regex against that sample, do not invent field " +
            "names. POST calls and requests that only fire after a click cannot be replayed; the notes say so. " +
            "After filling the regex, run testPlaybackRule.",
    )
    suspend fun recordPlaybackRuleFromTrace(
        @LLMDescription("JSON string previously returned by traceNetworkTraffic")
        traceJson: String,
        @LLMDescription("The anime title used during tracing; substituted as {title} in the skeleton")
        sampleTitle: String,
        @LLMDescription("Episode number substituted as {ep}; pass 0 to omit")
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

    /** 站名只从本轮观测到的 URL 里取，不内置任何站点清单 */
    private fun ruleNameFromTrace(trace: NetworkAuditTrace): String = ruleNameFromUrl(trace.finalUrl.ifBlank { trace.pageUrl })

    private fun ruleNameFromUrl(url: String): String =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .ifBlank { "录制规则" }

    @Tool
    @LLMDescription(
        "Induce a playback rule skeleton by reading the STATIC page source of a playback page URL " +
            "(one plain HTTP fetch, no browser). If the media URL (.m3u8/.mp4/...) is present in the " +
            "HTML, the draft carries the exact source-code context around it — write the EXTRACT_STREAM " +
            "regex against that context, do not invent field names. TRY THIS FIRST for ordinary sites: " +
            "it is much cheaper and faster than traceNetworkTraffic. Fall back to traceNetworkTraffic " +
            "only when the draft notes say the page is JS-rendered or the stream only appears at runtime. " +
            "After filling the regex, run testPlaybackRule.",
    )
    suspend fun recordPlaybackRuleFromStaticPage(
        @LLMDescription("The absolute URL of the playback episode page")
        playbackPageUrl: String,
        @LLMDescription("The anime title used during recording; substituted as {title} in the skeleton")
        sampleTitle: String,
        @LLMDescription("Episode number substituted as {ep}; pass 0 to omit")
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

    @Tool
    @LLMDescription(
        "Dry-run and test a PlaybackSourceRule in a sandboxed execution environment. " +
            "Pass the complete JSON string of PlaybackSourceRule together with a sample anime title and episode number. " +
            "Returns whether a valid stream URL and headers were successfully resolved.",
    )
    suspend fun testPlaybackRule(
        @LLMDescription("Serialized JSON string of the PlaybackSourceRule to test")
        ruleJson: String,
        @LLMDescription("Sample anime title to substitute into {title}")
        sampleTitle: String,
        @LLMDescription("Sample episode number to substitute into {ep}, e.g. 1")
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

        if (!rule.isResolvable) {
            return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    errorMessage =
                        "Rule shape is not executable: parserType=${rule.parserType} 与 pipeline 只在 " +
                            "kind=SOURCE 的规则上生效，请修正 kind/parserType 组合后重试。",
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
    @Tool
    @LLMDescription(
        "Propose a PlaybackSourceRule for the user to import into their local playback rules. " +
            "REQUIRES sampleTitle/sampleEp: this tool re-runs the rule itself and probes the resolved " +
            "stream's first response, so the proposal carries real evidence rather than your claim. " +
            "If it resolves nothing, or the address turns out not to be playable, NO proposal is created — " +
            "fix the rule and try again. This does NOT store anything: it returns a PENDING_CONFIRMATION " +
            "proposal, and the rule reaches the user's settings only if they approve the card.",
    )
    suspend fun proposePlaybackRule(
        @LLMDescription("Serialized JSON string of the PlaybackSourceRule to propose for import")
        ruleJson: String,
        @LLMDescription("Sample anime title to substitute into {title} while re-running the rule")
        sampleTitle: String,
        @LLMDescription("Sample episode number to substitute into {ep}, e.g. 1")
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

        if (!rule.isResolvable) {
            return json.encodeToString(
                RuleTestOutput(
                    success = false,
                    ruleName = rule.name,
                    errorMessage =
                        "Rule shape is not executable (kind=${rule.kind}, parserType=${rule.parserType}); " +
                            "fix it and re-run testPlaybackRule before proposing.",
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

    /** 对解析结果里首个直链做首包断言；没有直链候选或未接入验证器时返回 null（按"未验证"处理） */
    private suspend fun verifyFirstPlayable(sources: List<PlayableSource>): StreamVerification? {
        val verifier = playbackSourceVerifier ?: return null
        val first = sources.firstOrNull { it.kind == PlaylistEntryKind.DIRECT } ?: return null
        return verifier.verify(first)
    }
}

/** 首包断言的说明文案：通过则无需解释，未探测或不通过都要说清原因 */
private fun StreamVerification?.note(): String? =
    when (this) {
        null, StreamVerification.Unverified ->
            "首包未探测（当前环境不支持），导入前建议先手动试播一次"
        StreamVerification.Playable -> null
        is StreamVerification.NotPlayable -> reason
    }
