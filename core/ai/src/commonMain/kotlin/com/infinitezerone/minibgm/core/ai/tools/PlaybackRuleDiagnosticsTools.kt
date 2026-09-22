package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
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
            "Call this ONLY for a rule that testPlaybackRule just resolved successfully, passing the same ruleJson. " +
            "This does NOT store anything: it returns a PENDING_CONFIRMATION proposal, and the rule reaches the " +
            "user's settings only if they approve the card.",
    )
    suspend fun proposePlaybackRule(
        @LLMDescription("Serialized JSON string of the tested PlaybackSourceRule to propose for import")
        ruleJson: String,
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
                        "Rule shape is not executable (kind=${rule.kind}, parserType=${rule.parserType}); " +
                            "fix it and re-run testPlaybackRule before proposing.",
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
        return json.encodeToString(
            ActionProposal(
                message = "规则已在沙箱中验证可用，等待用户确认后写入本地播放源。",
                action = action,
            ),
        )
    }
}
