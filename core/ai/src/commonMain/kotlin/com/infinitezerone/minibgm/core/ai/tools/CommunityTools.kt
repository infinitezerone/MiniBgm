package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.ai.tool.BgmTool
import com.infinitezerone.minibgm.core.ai.tool.bgmTool
import com.infinitezerone.minibgm.core.ai.tool.schemaObject
import com.infinitezerone.minibgm.core.ai.tool.schemaProperty
import com.infinitezerone.minibgm.core.ai.tool.string
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.SubscriptionValidationReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 播放源订阅相关的智能体工具集。
 *
 * 只做一件事：把**用户明确给出的**地址或规则 JSON 拉下来、端侧测速探活，
 * 组装 [PendingAction.ImportPlaybackRules] 提案等用户确认。
 *
 * 刻意不提供「自行检索社区」的工具：目录属社区、机制属 App，
 * 模型不该替用户去公网猜站点，也不该凭记忆列举站点名。缺地址就向用户要。
 */
class CommunityTools(
    private val settingsRepository: SettingsRepository,
    private val json: Json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        },
    private val pendingActionStore: PendingActionStore? = null,
) {
    fun tools(): List<BgmTool> =
        listOf(
            bgmTool(
                name = "validateAndTestSubscription",
                description =
                    "Fetch and test a caller-supplied remote subscription URL (TVBox / MiniBgm / JSON), " +
                        "single third-party anime website URL, or candidate rules JSON array, probe connectivity of all rules, " +
                        "and generate an import proposal for user confirmation. Only accepts addresses the user actually provided; " +
                        "it does not search for or discover sites on its own. (HITL SAFE)",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "subscriptionUrl",
                                    schemaProperty(
                                        "string",
                                        "The user-provided remote subscription URL (TVBox/MiniBgm), single anime site URL, " +
                                            "or candidate rules JSON array to validate, probe and import.",
                                    ),
                                )
                            },
                        required = listOf("subscriptionUrl"),
                    ),
            ) { args ->
                validateAndTestSubscription(args.string("subscriptionUrl"))
            },
        )

    @OptIn(ExperimentalUuidApi::class)
    suspend fun validateAndTestSubscription(subscriptionUrl: String): String {
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
                val skippedNote = describeSkipped(report)
                val desc =
                    "导入 ${rules.size} 个社区动漫播放源（有效连通 ${report.aliveRules}/${report.totalRules}，" +
                        "平均延迟 ${report.averageLatencyMs}ms，包含 $sampleNames 等）$skippedNote"

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
                        message =
                            "已验证订阅地址并完成连通性测速（有效规则 ${report.aliveRules}/${report.totalRules}），" +
                                "生成待确认导入提案$skippedNote。",
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

    /**
     * 如实报告被跳过的站点。
     *
     * 订阅里常有本应用没有执行路径的爬虫源，静默丢掉会让用户以为"全部导进来了"。
     */
    private fun describeSkipped(report: SubscriptionValidationReport): String {
        val parts =
            buildList {
                if (report.skippedUnsupportedSites > 0) {
                    add("${report.skippedUnsupportedSites} 条为爬虫/扩展源，本应用不支持")
                }
                if (report.skippedMalformedSites > 0) {
                    add("${report.skippedMalformedSites} 条条目信息不完整")
                }
            }
        return if (parts.isEmpty()) "" else "；另有 ${parts.joinToString("，")}"
    }
}
