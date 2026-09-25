package com.infinitezerone.minibgm.core.ai.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 对应 OpenAI 兼容协议的标准 Function Definition
 */
@Serializable
data class ToolDefinitionDto(
    val type: String = "function",
    val function: FunctionDefinitionDto,
)

@Serializable
data class FunctionDefinitionDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * MiniBgm 领域工具接口，完全解耦第三方 Agent 框架。
 * 显式声明 JSON Schema，无反射调用，性能更高且在 Android 上 100% 确定。
 */
interface BgmTool {
    val name: String
    val description: String
    val parametersJsonSchema: JsonObject

    suspend fun execute(arguments: JsonObject): String

    fun toDefinition(): ToolDefinitionDto =
        ToolDefinitionDto(
            function =
                FunctionDefinitionDto(
                    name = name,
                    description = description,
                    parameters = parametersJsonSchema,
                ),
        )
}

fun bgmTool(
    name: String,
    description: String,
    parametersJsonSchema: JsonObject = emptyObjectSchema(),
    execute: suspend (JsonObject) -> String,
): BgmTool =
    object : BgmTool {
        override val name: String = name
        override val description: String = description
        override val parametersJsonSchema: JsonObject = parametersJsonSchema

        override suspend fun execute(arguments: JsonObject): String = execute(arguments)
    }

fun emptyObjectSchema(): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

fun schemaObject(
    properties: JsonObject,
    required: List<String> = emptyList(),
): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("properties", properties)
        if (required.isNotEmpty()) {
            putJsonArray("required") {
                required.forEach { add(JsonPrimitive(it)) }
            }
        }
    }

fun schemaProperty(
    type: String,
    description: String,
    itemsType: String? = null,
): JsonObject =
    buildJsonObject {
        put("type", type)
        put("description", description)
        if (itemsType != null) {
            putJsonObject("items") {
                put("type", itemsType)
            }
        }
    }

fun JsonObject.string(
    key: String,
    default: String = "",
): String = this[key]?.jsonPrimitive?.contentOrNull ?: default

fun JsonObject.int(
    key: String,
    default: Int = 0,
): Int = this[key]?.jsonPrimitive?.intOrNull ?: default

fun JsonObject.long(
    key: String,
    default: Long = 0L,
): Long = this[key]?.jsonPrimitive?.longOrNull ?: default

fun JsonObject.boolean(
    key: String,
    default: Boolean = false,
): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: default

fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

fun JsonObject.longList(key: String): List<Long> = this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull } ?: emptyList()

/**
 * 工具注册表与分发器
 */
class BgmToolRegistry(
    val tools: List<BgmTool> = emptyList(),
) {
    private val toolMap: Map<String, BgmTool> = tools.associateBy { it.name }

    fun toDefinitions(): List<ToolDefinitionDto> = tools.map { it.toDefinition() }

    fun getTool(name: String): BgmTool? = toolMap[name]

    suspend fun execute(
        name: String,
        arguments: JsonObject,
    ): String {
        val tool = toolMap[name] ?: return """{"error":"Tool '$name' not found"}"""
        return tool.execute(arguments)
    }
}
