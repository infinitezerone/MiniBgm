package com.infinitezerone.minibgm.feature.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.serialization.typeToken
import com.miniagent.agentloop.AgentTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val schemaJson = Json { ignoreUnknownKeys = true }

/**
 * 把 MiniAgent 契约的 [AgentTool] 适配为 Koog 的 [Tool]：
 * - 入参保持 [JsonObject]，参数 schema 依旧由 [AgentTool.parametersJsonSchema] 声明，
 *   在此解析为 Koog 的 [ToolDescriptor]；
 * - 出参统一转文本回填给模型。工具内部已把异常折叠为失败文本（让模型自行纠错，
 *   见 [MiniBgmAgentTools]），适配层不再二次捕获。
 */
internal fun AgentTool.asKoogTool(): Tool<JsonObject, String> =
    object : Tool<JsonObject, String>(
        argsType = typeToken<JsonObject>(),
        resultType = typeToken<String>(),
        descriptor = toKoogDescriptor(),
    ) {
        override suspend fun execute(args: JsonObject): String = this@asKoogTool.execute(args.toString()).content
    }

internal fun List<AgentTool>.asKoogTools(): List<Tool<JsonObject, String>> = map { it.asKoogTool() }

private fun AgentTool.toKoogDescriptor(): ToolDescriptor {
    val schema =
        runCatching { schemaJson.parseToJsonElement(parametersJsonSchema).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
    val properties = schema["properties"]?.jsonObject
    val required =
        schema["required"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()

    fun parameter(name: String): ToolParameterDescriptor {
        val property = properties?.get(name)?.jsonObject
        val type =
            when (property?.get("type")?.jsonPrimitive?.contentOrNull) {
                "integer", "number" -> ToolParameterType.Integer
                "boolean" -> ToolParameterType.Boolean
                else -> ToolParameterType.String
            }
        return ToolParameterDescriptor(
            name = name,
            description = property?.get("description")?.jsonPrimitive?.contentOrNull ?: "",
            type = type,
        )
    }

    val names = properties?.keys?.toList().orEmpty()
    return ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = names.filter { it in required }.map(::parameter),
        optionalParameters = names.filterNot { it in required }.map(::parameter),
    )
}
