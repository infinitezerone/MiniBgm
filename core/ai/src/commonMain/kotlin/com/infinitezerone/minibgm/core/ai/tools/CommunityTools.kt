package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.PendingAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 社区开源订阅与播放规则相关的 Koog 智能体工具集。
 * 遵循严格的 HITL 安全准则：
 * 自动从开源社区动态检索并测速探活二次元站点规则，组装 [PendingAction.ImportPlaybackRules] 提案等待用户确认导入。
 */
class CommunityTools(
    private val settingsRepository: SettingsRepository,
    private val json: Json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        },
    private val pendingActionStore: PendingActionStore? = null,
) : ToolSet {
    @Tool
    @LLMDescription(
        "Search open-source anime playback subscriptions from public platforms (e.g. GitHub) and return verified candidates with alive rule counts and average latency. (READ-ONLY)",
    )
    suspend fun searchCommunitySubscriptions(
        @LLMDescription(
            "Search keywords for anime playback rules, such as 'anime playback rules', 'bangumi rules', or an open-source query.",
        )
        keywords: String = "",
    ): String {
        AiToolActivity.report("检索社区订阅规则", if (keywords.isBlank()) "热门规则" else keywords)
        return when (val result = settingsRepository.searchCommunitySubscriptions(keywords)) {
            is AppResult.Success -> {
                val candidates = result.data
                if (candidates.isEmpty()) {
                    "No verified community subscriptions found for '$keywords'. Try different keywords or provide a direct JSON URL or rules array."
                } else {
                    val sb = StringBuilder("Found ${candidates.size} verified subscription candidates:\n")
                    candidates.forEachIndexed { index, candidate ->
                        val sampleStr =
                            if (candidate.sampleSources.isNotEmpty()) {
                                " (e.g. ${candidate.sampleSources.joinToString(", ")})"
                            } else {
                                ""
                            }
                        sb.append("${index + 1}. [${candidate.name}] ${candidate.subscriptionUrl}\n")
                        sb.append(
                            "   - Live rules: ${candidate.aliveCount}/${candidate.sourceCount}, Avg latency: ${candidate.averageLatencyMs}ms$sampleStr\n",
                        )
                        if (candidate.description.isNotBlank()) {
                            sb.append("   - Description: ${candidate.description}\n")
                        }
                    }
                    sb.append("\nYou can call validateAndTestSubscription with any of these URLs to inspect and propose importing them.")
                    sb.toString()
                }
            }
            is AppResult.Error -> {
                "Failed to search community subscriptions: ${result.throwable.message}"
            }
            is AppResult.Loading -> {
                "Searching community subscriptions in progress..."
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Tool
    @LLMDescription(
        "Fetch and test any remote subscription URL (TVBox / MiniBgm / JSON), single third-party anime website URL, or candidate rules JSON array, probe connectivity of all rules, and generate an import proposal for user confirmation. (HITL SAFE)",
    )
    suspend fun validateAndTestSubscription(
        @LLMDescription(
            "Remote subscription URL (TVBox/MiniBgm), single anime site URL, or candidate rules JSON array to validate, probe and import.",
        )
        subscriptionUrl: String,
    ): String {
        val trimmed = subscriptionUrl.trim()
        if (trimmed.isBlank()) {
            return "Subscription URL must not be blank."
        }
        AiToolActivity.report("验证并测速规则", trimmed.take(40))

        return when (val result = settingsRepository.validateAndTestSubscription(trimmed)) {
            is AppResult.Success -> {
                val report = result.data
                if (!report.isHealthy || report.sources.isEmpty()) {
                    return "Subscription validation failed: ${report.errorMessage ?: "no reachable sources found in $trimmed"}."
                }

                val rules =
                    report.sources
                        .filter { it.isAlive }
                        .map { it.toPlaybackSourceRule(id = Uuid.random().toString()) }
                        .ifEmpty {
                            report.sources.map { it.toPlaybackSourceRule(id = Uuid.random().toString()) }
                        }

                val actionId = "act_rules_${TimeUtils.nowEpochMillis()}"
                val sampleNames = rules.take(3).joinToString("、") { it.name }
                val desc =
                    "导入 ${rules.size} 个社区动漫播放源（有效连通 ${report.aliveRules}/${report.totalRules}，" +
                        "平均延迟 ${report.averageLatencyMs}ms，包含 $sampleNames 等）"

                val pendingAction =
                    PendingAction.ImportPlaybackRules(
                        actionId = actionId,
                        sourceName = "社区二次元播放源",
                        rules = rules,
                        description = desc,
                    )

                val proposal =
                    ActionProposal(
                        status = "PENDING_CONFIRMATION",
                        message = "已验证订阅地址并完成连通性测速（有效规则 ${report.aliveRules}/${report.totalRules}），生成待确认导入提案。",
                        action = pendingAction,
                    )

                pendingActionStore?.add(pendingAction)
                json.encodeToString(proposal)
            }
            is AppResult.Error -> {
                "Failed to validate subscription: ${result.throwable.message}"
            }
            is AppResult.Loading -> {
                "Subscription validation in progress..."
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Tool
    @LLMDescription(
        "Search and test open-source anime playback subscriptions from the community, then generate an import proposal for user confirmation. (HITL SAFE)",
    )
    suspend fun discoverCommunityPlaybackSources(
        @LLMDescription("Optional custom remote subscription JSON URL. Leave empty to dynamically discover from community.")
        customSubscriptionUrl: String = "",
    ): String {
        val urlParam = customSubscriptionUrl.trim().ifEmpty { null }
        if (urlParam != null) {
            return validateAndTestSubscription(urlParam)
        }
        AiToolActivity.report("发现社区动漫源", "开源社区检索与测速")

        return when (val result = settingsRepository.discoverCommunityPlaybackSources(null)) {
            is AppResult.Success -> {
                val sources = result.data
                if (sources.isEmpty()) {
                    return "No community anime playback sources found or all endpoints are unreachable."
                }

                val rules =
                    sources
                        .filter { it.isAlive }
                        .map { source ->
                            source.toPlaybackSourceRule(id = Uuid.random().toString())
                        }.ifEmpty {
                            sources.map { source ->
                                source.toPlaybackSourceRule(id = Uuid.random().toString())
                            }
                        }

                val actionId = "act_rules_${TimeUtils.nowEpochMillis()}"
                val sampleNames = rules.take(3).joinToString("、") { it.name }
                val desc = "导入 ${rules.size} 个社区动漫播放源（包含 $sampleNames 等）"

                val pendingAction =
                    PendingAction.ImportPlaybackRules(
                        actionId = actionId,
                        sourceName = "社区二次元播放源",
                        rules = rules,
                        description = desc,
                    )

                val proposal =
                    ActionProposal(
                        status = "PENDING_CONFIRMATION",
                        message = "已为您发现并测速 ${rules.size} 个社区动漫播放源，生成待确认导入提案。",
                        action = pendingAction,
                    )

                pendingActionStore?.add(pendingAction)
                json.encodeToString(proposal)
            }
            is AppResult.Error -> {
                "Failed to discover community playback sources: ${result.throwable.message}"
            }
            is AppResult.Loading -> {
                "Community sources discovery in progress..."
            }
        }
    }
}
