package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        },
) : ToolSet {
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
}
