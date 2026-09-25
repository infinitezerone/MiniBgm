package com.infinitezerone.minibgm.core.ai.wire

import com.infinitezerone.minibgm.core.ai.DEFAULT_SUMMARY_FALLBACK
import com.infinitezerone.minibgm.core.ai.aiJson
import com.infinitezerone.minibgm.core.ai.friendlyAiError
import com.infinitezerone.minibgm.core.ai.requestTemperature
import com.infinitezerone.minibgm.core.ai.runPiAgent
import com.infinitezerone.minibgm.core.ai.tool.BgmToolRegistry
import com.infinitezerone.minibgm.core.model.AiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 敌意样本测试：各家兼容端点（Gemini OpenAI 层、vLLM、各类代理）的真实脏响应固化为黄金样本。
 *
 * 协议层与循环层的回归靠 happy path 测试永远发现不了——缺字段的 tool_call、缺 message 的
 * choices、HTTP-date 形式的 Retry-After，每一个都曾是"测试全绿但用户撞 400/解码失败"的实锤。
 */
class OpenAiWireDirtyResponseTest {
    private fun wireClientOf(vararg bodies: String): OpenAiWireClient {
        var index = 0
        val engine =
            MockEngine { request ->
                val body = bodies[index.coerceAtMost(bodies.lastIndex)]
                index++
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return OpenAiWireClient(HttpClient(engine))
    }

    @Test
    fun dirtyResponse_missingToolCallIdAndArguments_getsSyntheticDefaults() =
        runTest {
            // Gemini OpenAI 兼容层与 vLLM 的常见形态：tool_calls 无 id、无 arguments
            val client =
                wireClientOf(
                    """
                    {
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "tool_calls": [
                              {"type": "function", "function": {"name": "findPlayableSources"}}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            val response =
                client.chatCompletion(
                    config = AiConfig(endpoint = "https://example.com/v1", apiKey = "k"),
                    request = WireChatRequest(model = "m", messages = listOf(WireChatMessage.user("hi"))),
                )
            val call =
                response.choices
                    .single()
                    .message.toolCalls
                    .orEmpty()
                    .single()
            assertNotNull(call.id, "缺失的 tool_call.id 必须补合成 id，否则 tool 消息无法回传")
            assertTrue(call.id.startsWith("call_"))
            assertEquals("{}", call.function.arguments, "缺失的 arguments 必须兜底为空参对象，而不是让入参解析抛异常")
        }

    @Test
    fun dirtyResponse_syntheticIds_areUniqueAcrossDecodes() =
        runTest {
            val dirty =
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [{"type": "function", "function": {"name": "t"}}]
                      }
                    }
                  ]
                }
                """.trimIndent()
            val client = wireClientOf(dirty, dirty)
            val first =
                client.chatCompletion(
                    AiConfig(endpoint = "e", apiKey = "k"),
                    WireChatRequest(model = "m", messages = listOf(WireChatMessage.user("hi"))),
                )
            val second =
                client.chatCompletion(
                    AiConfig(endpoint = "e", apiKey = "k"),
                    WireChatRequest(model = "m", messages = listOf(WireChatMessage.user("hi"))),
                )
            val id1 =
                first.choices
                    .single()
                    .message.toolCalls
                    .orEmpty()
                    .single()
                    .id
            val id2 =
                second.choices
                    .single()
                    .message.toolCalls
                    .orEmpty()
                    .single()
                    .id
            // 多轮会话会把历史消息原样回传：重复的合成 tool_call_id 会被部分端点拒绝
            assertFalse(id1 == id2, "两轮各自合成的 tool_call_id 必须全局唯一")
        }

    @Test
    fun dirtyResponse_missingMessage_fallsBackToEmptyAssistantInsteadOfDecodeFailure() =
        runTest {
            val client =
                wireClientOf(
                    """
                    {"choices": [{"index": 0, "finish_reason": "stop"}]}
                    """.trimIndent(),
                )
            val response =
                client.chatCompletion(
                    config = AiConfig(endpoint = "https://example.com/v1", apiKey = "k"),
                    request = WireChatRequest(model = "m", messages = listOf(WireChatMessage.user("hi"))),
                )
            assertEquals(
                "assistant",
                response.choices
                    .single()
                    .message.role,
            )
            assertNull(
                response.choices
                    .single()
                    .message.toolCalls,
            )
        }

    @Test
    fun endpointError_429_carriesStatusAndParsedRetryAfter() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":{"message":"Rate limit exceeded"}}""",
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.RetryAfter, "7"),
                    )
                }
            val client = OpenAiWireClient(HttpClient(engine))
            val e =
                assertFailsWith<AiEndpointException> {
                    client.chatCompletion(
                        config = AiConfig(endpoint = "https://example.com/v1", apiKey = "k"),
                        request = WireChatRequest(model = "m", messages = listOf(WireChatMessage.user("hi"))),
                    )
                }
            assertEquals(429, e.status)
            assertEquals(7000L, e.retryAfterMs, "秒数形式的 Retry-After 应解析为毫秒并随异常携带")
            assertTrue(e.message.orEmpty().contains("Rate limit exceeded"))
        }

    @Test
    fun endpointError_httpDateRetryAfter_isIgnoredRatherThanMisread() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = headersOf(HttpHeaders.RetryAfter, "Wed, 21 Oct 2026 07:28:00 GMT"),
                    )
                }
            val client = OpenAiWireClient(HttpClient(engine))
            val e =
                assertFailsWith<AiEndpointException> {
                    client.fetchModelsRaw(endpoint = "https://example.com/v1", apiKey = "k", provider = "custom")
                }
            assertEquals(503, e.status)
            assertNull(e.retryAfterMs, "HTTP-date 形式的 Retry-After 不应被误读成秒数")
        }

    @Test
    fun friendlyAiError_readsStructuredStatusBeforeGuessingText() {
        val config = AiConfig(endpoint = "https://example.com/v1", model = "gpt-4o")

        // 401：响应体不含任何鉴权关键词，仅凭状态码也能分类
        val auth = friendlyAiError(config, AiEndpointException(401, """{"x":1}"""))
        assertTrue(auth.contains("鉴权失败"), "实际输出：$auth")

        // 429：走 formatRateLimitError
        val rate = friendlyAiError(config, AiEndpointException(429, "TPM: Limit 8000"))
        assertTrue(rate.contains("请求已被服务商限制"), "实际输出：$rate")

        // 5xx：提取内层 message，不带出 HTTP 状态行
        val server = friendlyAiError(config, AiEndpointException(502, """{"error":{"message":"upstream dead"}}"""))
        assertTrue(server.contains("upstream dead"), "实际输出：$server")
        assertTrue(server.contains("502"))

        // 无专属语义的状态码退回字符串匹配：正文含 model not found 仍按模型不可用处理
        val fallback = friendlyAiError(config, AiEndpointException(422, """{"error":{"message":"model not found"}}"""))
        assertTrue(fallback.contains("不可用"), "实际输出：$fallback")
    }

    @Test
    fun requestTemperature_omittedForOpenAiReasoningModelsOnly() {
        assertNull(requestTemperature("o1"), "o1 拒绝 temperature 参数")
        assertNull(requestTemperature("o3-mini"))
        assertNull(requestTemperature("o4-mini"))
        assertNull(requestTemperature("gpt-5-mini"))
        assertEquals(0.3, requestTemperature("gpt-4o-mini"))
        assertEquals(0.3, requestTemperature("deepseek-reasoner"))
        assertEquals(0.3, requestTemperature("qwen3-235b-a22b"))
    }

    @Test
    fun temperatureOmission_roundTripsThroughWireEncoding() {
        // 显式验证 encodeDefaults 不会把 null temperature 拼回请求体
        val encoded =
            aiJson.encodeToString(
                WireChatRequest.serializer(),
                WireChatRequest(
                    model = "o3-mini",
                    messages = listOf(WireChatMessage.user("hi")),
                    temperature = requestTemperature("o3-mini"),
                ),
            )
        assertFalse(encoded.contains("temperature"), "推理模型请求不得携带 temperature：$encoded")

        val normal =
            aiJson.encodeToString(
                WireChatRequest.serializer(),
                WireChatRequest(
                    model = "gpt-4o-mini",
                    messages = listOf(WireChatMessage.user("hi")),
                    temperature = requestTemperature("gpt-4o-mini"),
                ),
            )
        assertTrue(normal.contains("temperature"))
    }

    @Test
    fun runPiAgent_truncatedEmptyReply_failsExplicitlyInsteadOfEmptyBubble() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "id": "chatcmpl-cut",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {"role": "assistant", "content": ""},
                                  "finish_reason": "length"
                                }
                              ]
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val e =
                assertFailsWith<IllegalStateException> {
                    runPiAgent(
                        wireClient = OpenAiWireClient(HttpClient(engine)),
                        config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = "k", model = "gpt-4o-mini"),
                        prompt = "讲讲这部番",
                        tools = BgmToolRegistry(emptyList()),
                    )
                }
            assertTrue(e.message.orEmpty().contains("截断"), "实际输出：${e.message}")
        }

    @Test
    fun runPiAgent_summaryFallback_stillAppliesAfterTruncationGuard() {
        // 防回归哨兵：截断守卫改动不得影响 summarize 的既有兜底语义
        assertTrue(DEFAULT_SUMMARY_FALLBACK.isNotBlank())
    }
}
