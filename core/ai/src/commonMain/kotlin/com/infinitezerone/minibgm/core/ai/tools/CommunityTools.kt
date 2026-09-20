package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
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
 * 自动从开源社区索引发现并测速可用的二次元站点规则，组装 [PendingAction.ImportPlaybackRules] 提案等待用户确认导入。
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
    @OptIn(ExperimentalUuidApi::class)
    @Tool
    @LLMDescription(
        "Search and test open-source anime playback subscriptions from the community, then generate an import proposal for user confirmation. (HITL SAFE)",
    )
    suspend fun discoverCommunityPlaybackSources(
        @LLMDescription("Optional custom remote subscription JSON URL. Leave empty to use community default endpoints.")
        customSubscriptionUrl: String = "",
    ): String {
        val urlParam = customSubscriptionUrl.trim().ifEmpty { null }
        return when (val result = settingsRepository.discoverCommunityPlaybackSources(urlParam)) {
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
                            sources.map { it.toPlaybackSourceRule(id = Uuid.random().toString()) }
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
