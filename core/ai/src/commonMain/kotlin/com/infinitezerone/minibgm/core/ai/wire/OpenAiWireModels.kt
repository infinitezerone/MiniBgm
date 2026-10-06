package com.infinitezerone.minibgm.core.ai.wire

import com.infinitezerone.minibgm.core.ai.tool.ToolDefinitionDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class WireChatRequest(
    val model: String,
    val messages: List<WireChatMessage>,
    val tools: List<ToolDefinitionDto>? = null,
    val temperature: Double? = 0.3,
    /** null 时不序列化（aiJson explicitNulls=false），请求保持非流式 */
    val stream: Boolean? = null,
    @SerialName("stream_options")
    val streamOptions: WireStreamOptions? = null,
)

/** OpenAI 专有扩展：要求在最后一个流式分片里回传 usage；部分兼容端点不认识该字段，仅对声明的 provider 发送 */
@Serializable
data class WireStreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean = true,
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
    /** 部分兼容端点（Gemini OpenAI 层、vLLM 等）不回 id：解码后由 [normalizeWireResponse] 补合成 id */
    val id: String? = null,
    val type: String = "function",
    val function: WireFunctionCall,
)

@Serializable
data class WireFunctionCall(
    val name: String,
    /** 部分端点省略该字段：由 [normalizeWireResponse] 兜底为 "{}" */
    val arguments: String? = null,
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
    /** 个别端点在异常分支只回 choices 不回 message：兜底为空 assistant 消息而不是整包解码失败 */
    val message: WireChatMessage = WireChatMessage(role = "assistant"),
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

/**
 * 归一化端点脏响应：缺 tool_call.id 补全局唯一合成 id，缺 arguments 兜底 "{}"。
 *
 * 合成 id 必须每次解码都唯一（而不是按下标复现）：多轮会话里历史消息会原样回传，
 * 若两轮各自合成出相同 id，部分端点会拒绝重复的 tool_call_id。
 */
@OptIn(ExperimentalUuidApi::class)
internal fun WireChatResponse.normalizeDirtyFields(): WireChatResponse =
    copy(
        choices =
            choices.map { choice ->
                choice.copy(
                    message =
                        choice.message.copy(
                            toolCalls =
                                choice.message.toolCalls?.map { call ->
                                    call.copy(
                                        id = call.id ?: "call_${Uuid.random()}",
                                        function = call.function.copy(arguments = call.function.arguments ?: "{}"),
                                    )
                                },
                        ),
                )
            },
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

// ---------- 流式（SSE）分片模型：仅由 [OpenAiWireClient.chatCompletionStream] 消费 ----------

@Serializable
internal data class WireStreamChunk(
    val id: String? = null,
    val choices: List<WireStreamChoice> = emptyList(),
    val usage: WireUsage? = null,
    val error: WireError? = null,
)

@Serializable
internal data class WireStreamChoice(
    val index: Int = 0,
    val delta: WireStreamDelta = WireStreamDelta(),
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
internal data class WireStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<WireStreamDeltaToolCall> = emptyList(),
)

@Serializable
internal data class WireStreamDeltaToolCall(
    /** 按 index 归组增量：同一调用的 arguments 分片跨多个 chunk 追加 */
    val index: Int = 0,
    val id: String? = null,
    val function: WireStreamDeltaFunction? = null,
)

@Serializable
internal data class WireStreamDeltaFunction(
    val name: String? = null,
    val arguments: String? = null,
)
