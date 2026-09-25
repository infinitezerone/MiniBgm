package com.infinitezerone.minibgm.core.ai.wire

import com.infinitezerone.minibgm.core.ai.tool.ToolDefinitionDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WireChatRequest(
    val model: String,
    val messages: List<WireChatMessage>,
    val tools: List<ToolDefinitionDto>? = null,
    val temperature: Double? = 0.3,
)

@Serializable
data class WireChatMessage(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
) {
    companion object {
        fun system(content: String): WireChatMessage = WireChatMessage(role = "system", content = content)

        fun user(content: String): WireChatMessage = WireChatMessage(role = "user", content = content)

        fun assistant(
            content: String? = null,
            reasoningContent: String? = null,
            toolCalls: List<WireToolCall>? = null,
        ): WireChatMessage =
            WireChatMessage(
                role = "assistant",
                content = content,
                reasoningContent = reasoningContent,
                toolCalls = toolCalls,
            )

        fun tool(
            toolCallId: String,
            content: String,
            name: String? = null,
        ): WireChatMessage =
            WireChatMessage(
                role = "tool",
                content = content,
                toolCallId = toolCallId,
                name = name,
            )
    }
}

@Serializable
data class WireToolCall(
    val id: String,
    val type: String = "function",
    val function: WireFunctionCall,
)

@Serializable
data class WireFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class WireChatResponse(
    val id: String? = null,
    val choices: List<WireChoice> = emptyList(),
    val usage: WireUsage? = null,
    val error: WireError? = null,
)

@Serializable
data class WireChoice(
    val index: Int = 0,
    val message: WireChatMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class WireUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)

@Serializable
data class WireError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

@Serializable
data class WireModelsResponse(
    val data: List<WireModelItem> = emptyList(),
)

@Serializable
data class WireModelItem(
    val id: String,
)

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModelItem> = emptyList(),
)

@Serializable
data class OllamaModelItem(
    val name: String,
)
