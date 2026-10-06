package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.ai.AiHistoryTurn
import com.infinitezerone.minibgm.core.ai.di.aiModule
import com.infinitezerone.minibgm.core.ai.tool.string
import com.infinitezerone.minibgm.core.ai.wire.AiEndpointException
import com.infinitezerone.minibgm.core.ai.wire.OpenAiWireClient
import com.infinitezerone.minibgm.core.ai.wire.WireChatMessage
import com.infinitezerone.minibgm.core.ai.wire.WireChatRequest
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 测试替身的非凭据标记值；用符号常量传递，避免在源码里出现凭据形状的字面量 */
private const val STUB_TOKEN = "stub-token"

private const val NL_DATA_DONE = "\n\ndata: [DONE]\n\n"

/** 把单帧 chat.completion JSON 转成 SSE 响应体（message→delta），供 MockEngine 走流式路径 */
private fun sseBody(chatJson: String): String = "data: " + chatJson.replace("message", "delta").lines().joinToString("") + NL_DATA_DONE

class DefaultBgmAiAgentServiceTest : KoinTest {
    private val fakeSettingsRepository = FakeSettingsRepository()

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                aiModule,
                module {
                    single<SettingsRepository> { fakeSettingsRepository }
                    single<com.infinitezerone.minibgm.core.data.repository.ScheduleRepository> {
                        com.infinitezerone.minibgm.core.testing.repository
                            .FakeScheduleRepository()
                    }
                    single<com.infinitezerone.minibgm.core.data.repository.SubjectRepository> {
                        com.infinitezerone.minibgm.core.testing.repository
                            .FakeSubjectRepository()
                    }
                    single<com.infinitezerone.minibgm.core.data.repository.CollectionRepository> {
                        com.infinitezerone.minibgm.core.testing.repository
                            .FakeCollectionRepository()
                    }
                    single<com.infinitezerone.minibgm.core.data.repository.SearchRepository> {
                        com.infinitezerone.minibgm.core.testing.repository
                            .FakeSearchRepository()
                    }
                    single<com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository> {
                        object : com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository {
                            override suspend fun resolvePages(
                                pageUrls: List<String>,
                                epNumber: Float,
                                siteName: String,
                                title: String,
                            ): List<com.infinitezerone.minibgm.core.model.PlayableSource> = emptyList()

                            override suspend fun resolveTemplate(
                                url: String,
                                headers: Map<String, String>,
                                epNumber: Float,
                                siteName: String,
                                title: String,
                            ): List<com.infinitezerone.minibgm.core.model.PlayableSource> = emptyList()
                        }
                    }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun aiModule_resolves_BgmAiAgentService() {
        val service: BgmAiAgentService by inject()
        assertIs<DefaultBgmAiAgentService>(service)
    }

    @Test
    fun execute_returns_error_when_prompt_is_blank() =
        runTest {
            val service = DefaultBgmAiAgentService(fakeSettingsRepository)
            val result = service.execute("   ")
            assertIs<AppResult.Error>(result)
            assertTrue(result.throwable.message?.contains("Prompt must not be blank") == true)
        }

    @Test
    fun execute_returns_error_when_apiKey_is_missing_for_cloud_providers() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    provider = AiConfig.PROVIDER_GEMINI,
                    apiKey = "",
                ),
            )
            val service = DefaultBgmAiAgentService(fakeSettingsRepository)
            val result = service.execute("Hello")
            assertIs<AppResult.Error>(result)
            assertTrue(result.throwable.message?.contains("API key is missing") == true)
        }

    @Test
    fun execute_returns_success_when_agentRunner_succeeds() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "http://localhost:11434/v1",
                    apiKey = STUB_TOKEN,
                    model = "qwen2.5:7b",
                    provider = "ollama",
                ),
            )
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, prompt -> "AI response for: $prompt" },
                )
            val result = service.execute("Recommend an anime")
            assertIs<AppResult.Success<String>>(result)
            assertEquals("AI response for: Recommend an anime", result.data)
        }

    @Test
    fun execute_returns_error_when_agentRunner_throws() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "http://localhost:11434/v1",
                    apiKey = STUB_TOKEN,
                    model = "qwen2.5:7b",
                    provider = "ollama",
                ),
            )
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, _ -> throw IllegalStateException("Network unreachable") },
                )
            val result = service.execute("Recommend an anime")
            assertIs<AppResult.Error>(result)
            assertEquals("Network unreachable", result.throwable.message)
        }

    @Test
    fun execute_retries_and_succeeds_on_temporary_429_rate_limit() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    model = "gpt-4o-mini",
                    provider = "openai",
                ),
            )
            var attempts = 0
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, prompt ->
                        attempts++
                        if (attempts == 1) {
                            throw IllegalStateException("Status code: 429, inference exceeds tpm/rpm limit")
                        }
                        "成功响应: $prompt"
                    },
                )
            val result = service.execute("测试重试")
            assertIs<AppResult.Success<String>>(result)
            assertEquals("成功响应: 测试重试", result.data)
            assertEquals(2, attempts, "应在第 2 次重试后成功")
        }

    @Test
    fun execute_passes_structured_history_turns_through() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    model = "gpt-4o-mini",
                    provider = "openai",
                ),
            )
            var capturedHistory: List<AiHistoryTurn>? = null
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, _, history, _ ->
                        capturedHistory = history
                        "ok"
                    },
                )
            val history =
                listOf(
                    AiHistoryTurn(isUser = true, content = "帮我分析 https://example.tv/"),
                    AiHistoryTurn(isUser = false, content = "正在分析"),
                )
            val result = service.execute("继续执行", history)
            assertIs<AppResult.Success<String>>(result)
            val captured = assertNotNull(capturedHistory)
            assertEquals(2, captured.size)
            assertTrue(captured[0].isUser)
            assertEquals("帮我分析 https://example.tv/", captured[0].content)
            assertFalse(captured[1].isUser)
        }

    @Test
    fun execute_drops_poisoned_history_turns() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    model = "gpt-4o-mini",
                    provider = "openai",
                ),
            )
            var capturedHistory: List<AiHistoryTurn>? = null
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, _, history, _ ->
                        capturedHistory = history
                        "ok"
                    },
                )
            val dirtyHistory =
                listOf(
                    AiHistoryTurn(isUser = false, content = "<tool_call>searchWeb</tool_call>"),
                    AiHistoryTurn(isUser = false, content = "❌ 执行出错：AI 响应超时（180 秒）"),
                    AiHistoryTurn(isUser = true, content = "正常的一轮提问"),
                )
            val result = service.execute("再次搜索", dirtyHistory)
            assertIs<AppResult.Success<String>>(result)
            // 毒化轮次被整体剔除，正常轮次保留
            val captured = assertNotNull(capturedHistory)
            assertEquals(1, captured.size)
            assertEquals("正常的一轮提问", captured[0].content)
        }

    @Test
    fun execute_with_default_runner_handles_connection_failure() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "http://localhost:11434/v1",
                    apiKey = STUB_TOKEN,
                    model = "qwen2.5:7b",
                    provider = "ollama",
                ),
            )
            // Real Koog AIAgent execution path: connection to offline localhost fails gracefully
            val service = DefaultBgmAiAgentService(fakeSettingsRepository)
            val result = service.execute("Recommend an anime")
            assertIs<AppResult.Error>(result)
        }

    @Test
    fun execute_cancellation_propagates_instead_of_becoming_error() =
        runTest {
            // 用户点"停止"时 Job 被取消：取消必须向上传播。
            // 若被 catch(Exception) 吞掉，"已停止"会伪装成一条错误消息进会话（真机实测踩过）
            fakeSettingsRepository.setAiConfig(
                AiConfig(endpoint = "http://localhost:11434/v1", provider = "ollama"),
            )
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, _, _, _ -> throw kotlinx.coroutines.CancellationException("用户停止") },
                )
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                service.execute("任意输入")
            }
        }

    @Test
    fun execute_with_custom_openai_model_configures_capabilities() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "http://127.0.0.1:9999/v1",
                    apiKey = STUB_TOKEN,
                    model = "DeepSeek-V4-Flash-Vision-Exp",
                    provider = "custom",
                ),
            )
            val service = DefaultBgmAiAgentService(fakeSettingsRepository)
            val result = service.execute("Hi")
            assertIs<AppResult.Error>(result)
            val msg = result.throwable.message.orEmpty()
            assertFalse(
                msg.contains("Cannot determine proper LLM params"),
                "Custom OpenAI model must have OpenAIEndpoint.Completions capability: $msg",
            )
        }

    @Test
    fun fetchAvailableModels_success_returns_parsed_models() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("Bearer $STUB_TOKEN", request.headers[HttpHeaders.Authorization])
                    assertEquals(OpenAiWireClient.DEFAULT_USER_AGENT, request.headers[HttpHeaders.UserAgent])
                    respond(
                        content = """{"data":[{"id":"qwen-2.5-7b"},{"id":"gpt-4o"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                )
            val result =
                service.fetchAvailableModels(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    provider = "custom",
                )
            assertIs<AppResult.Success<List<String>>>(result)
            assertEquals(listOf("qwen-2.5-7b", "gpt-4o"), result.data)
        }

    @Test
    fun fetchAvailableModels_customUserAgent_injectedInHeaders() =
        runTest {
            val customUa = "MiniBgm/0.3.5 (android) (https://github.com/infinitezerone/MiniBgm)"
            val engine =
                MockEngine { request ->
                    assertEquals(customUa, request.headers[HttpHeaders.UserAgent])
                    respond(
                        content = """{"data":[{"id":"gpt-4o"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val wireClient = OpenAiWireClient(httpClient = HttpClient(engine), userAgent = customUa)
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    wireClient = wireClient,
                )
            val result = service.fetchAvailableModels(endpoint = "https://api.openai.com/v1")
            assertIs<AppResult.Success<List<String>>>(result)
            assertEquals(listOf("gpt-4o"), result.data)
        }

    @Test
    fun fetchAvailableModels_http_401_returns_auth_failure_error() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"unauthorized"}""",
                        status = HttpStatusCode.Unauthorized,
                    )
                }
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                )
            val result = service.fetchAvailableModels()
            assertIs<AppResult.Error>(result)
            assertTrue(result.message.contains("鉴权失败") || result.message.contains("401"))
        }

    @Test
    fun fetchAvailableModels_empty_models_returns_error() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"data":[]}""",
                        status = HttpStatusCode.OK,
                    )
                }
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                )
            val result = service.fetchAvailableModels()
            assertIs<AppResult.Error>(result)
            assertTrue(result.message.contains("未返回任何可用模型"))
        }

    @Test
    fun fetchAvailableModels_network_exception_translates_friendly_error() =
        runTest {
            val engine =
                MockEngine {
                    throw IllegalStateException("connection refused")
                }
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                )
            val result = service.fetchAvailableModels()
            assertIs<AppResult.Error>(result)
            assertTrue(result.message.contains("无法连接"))
        }

    @Test
    fun parseModelsBody_filters_non_chat_models_and_ranks_models() {
        val mockJson =
            """
            {
              "data": [
                {
                  "id": "sensenova-u1-fast",
                  "output_modalities": ["image"]
                },
                {
                  "id": "text-embedding-3-small",
                  "output_modalities": ["text"]
                },
                {
                  "id": "sensenova-6.7-flash-lite",
                  "output_modalities": ["text", "image"]
                },
                {
                  "id": "sensenova-6.8-flash-lite",
                  "output_modalities": ["text", "image"]
                },
                {
                  "id": "dall-e-3",
                  "output_modalities": ["image"]
                },
                {
                  "id": "deepseek-v4-flash",
                  "output_modalities": ["text"]
                }
              ]
            }
            """.trimIndent()

        val parsed = parseModelsBody(mockJson)
        assertNotNull(parsed)
        // 非文本与非对话模型被过滤
        assertFalse(parsed.contains("sensenova-u1-fast"))
        assertFalse(parsed.contains("text-embedding-3-small"))
        assertFalse(parsed.contains("dall-e-3"))

        // 对话模型保留
        assertTrue(parsed.contains("sensenova-6.8-flash-lite"))
        assertTrue(parsed.contains("sensenova-6.7-flash-lite"))
        assertTrue(parsed.contains("deepseek-v4-flash"))

        // 6.8 排序应优于 6.7
        val idx68 = parsed.indexOf("sensenova-6.8-flash-lite")
        val idx67 = parsed.indexOf("sensenova-6.7-flash-lite")
        assertTrue(idx68 < idx67, "sensenova-6.8 should be ranked before 6.7")
    }

    @Test
    fun friendlyAiError_translates_404_model_route_not_found() {
        val config = AiConfig(endpoint = "https://token.sensenova.cn/v1", model = "sensenova-6.7-flash-lite")
        val error =
            IllegalStateException(
                "Status code: 404\nError body: {\"error\":{\"message\":\"model route not found\",\"type\":\"not_found_error\",\"code\":\"5\"}}",
            )
        val msg = friendlyAiError(config, error)
        assertTrue(msg.contains("model route not found") || msg.contains("不可用"))
        assertTrue(msg.contains("sensenova-6.7-flash-lite"))
    }

    @Test
    fun friendlyAiError_extracts_inner_json_error_message() {
        val config = AiConfig(endpoint = "https://example.com/v1", model = "gpt-4o")
        val error =
            IllegalStateException(
                "Status code: 500\nError body: {\"error\":{\"message\":\"Error from provider amd: 503 Service Unavailable\"}}",
            )
        val msg = friendlyAiError(config, error)
        assertTrue(msg.contains("503 Service Unavailable"))
        assertFalse(msg.contains("Status code: 500"))
    }

    @Test
    fun extractJsonErrorMessage_parses_nested_and_flat_formats() {
        val openAiJson = "Error: {\"error\":{\"message\":\"quota exceeded\"}}"
        assertEquals("quota exceeded", extractJsonErrorMessage(openAiJson))

        val flatJson = "Failed: {\"message\":\"invalid token\"}"
        assertEquals("invalid token", extractJsonErrorMessage(flatJson))

        val plainText = "Plain error without json"
        assertNull(extractJsonErrorMessage(plainText))
    }

    @Test
    fun formatRateLimitError_provides_groq_and_generic_guidance() {
        val groqConfig = AiConfig(endpoint = "https://api.groq.com/openai/v1", model = "qwen/qwen3.8-27b")
        val groqError = "429: {\"error\":{\"message\":\"Rate limit reached on TPM: Limit 8000, Used 7900\"}}"
        val groqMsg = formatRateLimitError(groqError, groqConfig)
        assertTrue(groqMsg.contains("Rate limit reached on TPM"))
        assertTrue(groqMsg.contains("Groq 免费层"))
        assertTrue(groqMsg.contains("8,000 Token"))

        val genericConfig = AiConfig(endpoint = "https://api.deepseek.com/v1", model = "deepseek-chat")
        val genericError = "429 Too Many Requests"
        val genericMsg = formatRateLimitError(genericError, genericConfig)
        assertTrue(genericMsg.contains("超出速率或配额限制"))
        assertFalse(genericMsg.contains("Groq 免费层"))
    }

    @Test
    fun extractRetryDelayMs_parses_seconds_correctly() {
        val raw = "Rate limit reached on TPM. Please try again in 5.812s."
        val delay = extractRetryDelayMs(raw)
        assertNotNull(delay)
        assertTrue(delay in 6000L..8000L)

        val noMatch = "Random 429 error without delay"
        assertNull(extractRetryDelayMs(noMatch))
    }

    @Test
    fun execute_piAgent_does_not_halt_on_intermediate_text_when_tool_calls_present() =
        runTest {
            var requestCount = 0
            val toolExecuted = mutableListOf<String>()

            val engine =
                MockEngine { request ->
                    requestCount++
                    val responseJson =
                        if (requestCount == 1) {
                            // Turn 1: Model outputs intermediate reasoning/introductory text AND tool calls
                            """
                            {
                              "id": "chatcmpl-1",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": "让我先探测几个主流动漫聚合站，确认它们是否有可播放的源数据：",
                                    "tool_calls": [
                                      {
                                        "id": "call_123",
                                        "type": "function",
                                        "function": {
                                          "name": "mockSearch",
                                          "arguments": "{\"query\":\"葬送的芙莉莲\"}"
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                            """.trimIndent()
                        } else {
                            // Turn 2: Model receives tool result and produces final answer
                            """
                            {
                              "id": "chatcmpl-2",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": "检索完成，已成功解析到芙莉莲的有效播放地址！"
                                  }
                                }
                              ]
                            }
                            """.trimIndent()
                        }

                    respond(
                        content = sseBody(responseJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val mockTool =
                com.infinitezerone.minibgm.core.ai.tool.bgmTool(
                    name = "mockSearch",
                    description = "Mock search tool",
                ) { args ->
                    val query = args.string("query")
                    toolExecuted.add(query)
                    """[{"url":"https://example.com/play/1","title":"芙莉莲 第1集"}]"""
                }

            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    model = "gpt-4o",
                    provider = "openai",
                ),
            )

            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                    agentRunner = { config, prompt, _, _ ->
                        runPiAgent(
                            wireClient = OpenAiWireClient(HttpClient(engine)),
                            config = config,
                            prompt = prompt,
                            history = emptyList(),
                            tools =
                                com.infinitezerone.minibgm.core.ai.tool
                                    .BgmToolRegistry(listOf(mockTool)),
                        )
                    },
                )

            val result = service.execute("帮我找葬送的芙莉莲")
            assertIs<AppResult.Success<String>>(result)
            assertEquals("检索完成，已成功解析到芙莉莲的有效播放地址！", result.data)
            assertEquals(listOf("葬送的芙莉莲"), toolExecuted)
            assertEquals(2, requestCount, "Agent should complete both turns without halting on intermediate text")
        }

    @Test
    fun execute_piAgent_falls_back_to_reasoning_content_when_content_is_blank() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "id": "chatcmpl-think",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": "",
                                    "reasoning_content": "DeepSeek-R1 / XingChen-4.0 思考过程得出的结论：这是一部优秀的番剧。"
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    model = "XingChenAGI/Xing4.0-29B",
                    provider = "openai",
                ),
            )

            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                )

            val result = service.execute("评价一下这部番剧")
            assertIs<AppResult.Success<String>>(result)
            assertEquals("DeepSeek-R1 / XingChen-4.0 思考过程得出的结论：这是一部优秀的番剧。", result.data)
        }

    @Test
    fun formatToolCallDetail_extracts_known_keys_and_fallbacks() {
        // 匹配 query
        val queryObj =
            kotlinx.serialization.json.buildJsonObject {
                put("query", kotlinx.serialization.json.JsonPrimitive("葬送的芙莉莲"))
            }
        assertEquals("葬送的芙莉莲", formatToolCallDetail(queryObj))

        // 匹配 name
        val nameObj =
            kotlinx.serialization.json.buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive("进击的巨人"))
            }
        assertEquals("进击的巨人", formatToolCallDetail(nameObj))

        // 未知 key 回退到 JSON 字符串
        val otherObj =
            kotlinx.serialization.json.buildJsonObject {
                put("customKey", kotlinx.serialization.json.JsonPrimitive("value123"))
            }
        assertNotNull(formatToolCallDetail(otherObj))
        assertTrue(formatToolCallDetail(otherObj)?.contains("customKey") == true)

        // 空对象返回 null
        val emptyObj = kotlinx.serialization.json.buildJsonObject {}
        assertNull(formatToolCallDetail(emptyObj))
    }

    @Test
    fun summarizeFinalOutcome_returns_model_content_on_success() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content =
                            """
                            {
                              "id": "chatcmpl-1",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": "总结：已为您检索完毕。"
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val wireClient = OpenAiWireClient(HttpClient(engine))
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val result = summarizeFinalOutcome(config, wireClient, "gpt-4o-mini", emptyList())
            assertEquals("总结：已为您检索完毕。", result)
        }

    @Test
    fun summarizeFinalOutcome_returns_fallback_on_failure() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = "",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val wireClient = OpenAiWireClient(HttpClient(engine))
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val result = summarizeFinalOutcome(config, wireClient, "gpt-4o-mini", emptyList())
            assertEquals(DEFAULT_SUMMARY_FALLBACK, result)
        }

    @Test
    fun runPiAgent_breaks_loop_on_duplicate_tool_calls() =
        runTest {
            var callCount = 0
            val engine =
                MockEngine { _ ->
                    callCount++
                    respond(
                        content =
                            sseBody(
                                """
                                {
                                  "id": "chatcmpl-loop",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "message": {
                                        "role": "assistant",
                                        "tool_calls": [
                                          {
                                            "id": "call_1",
                                            "type": "function",
                                            "function": {
                                              "name": "mockTool",
                                              "arguments": "{\"query\":\"test\"}"
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            val wireClient = OpenAiWireClient(HttpClient(engine))
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val tool =
                com.infinitezerone.minibgm.core.ai.tool.bgmTool(
                    name = "mockTool",
                    description = "desc",
                    parametersJsonSchema =
                        com.infinitezerone.minibgm.core.ai.tool
                            .schemaObject(properties = kotlinx.serialization.json.buildJsonObject {}),
                ) { "result" }
            val tools =
                com.infinitezerone.minibgm.core.ai.tool
                    .BgmToolRegistry(listOf(tool))
            val result = runPiAgent(wireClient, config, "test prompt", emptyList(), tools, maxTurns = 5)
            assertEquals(DEFAULT_SUMMARY_FALLBACK, result)
            assertTrue(callCount <= 4)
        }

    @Test
    fun runPiAgent_reports_invalid_tool_arguments_back_to_model_instead_of_executing() =
        runTest {
            val requestBodies = mutableListOf<String>()
            var requestCount = 0
            var toolExecutions = 0
            val engine =
                MockEngine { request ->
                    requestCount++
                    requestBodies.add((request.body as io.ktor.http.content.TextContent).text)
                    val responseJson =
                        if (requestCount == 1) {
                            """
                            {
                              "id": "chatcmpl-badargs",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "tool_calls": [
                                      {
                                        "id": "call_bad",
                                        "type": "function",
                                        "function": {
                                          "name": "mockTool",
                                          "arguments": "not-valid-json{{{"
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                            """.trimIndent()
                        } else {
                            """{"id":"chatcmpl-badargs-2","choices":[{"index":0,"message":{"role":"assistant","content":"done"}}]}"""
                        }
                    respond(
                        content = sseBody(responseJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val tool =
                com.infinitezerone.minibgm.core.ai.tool.bgmTool(
                    name = "mockTool",
                    description = "desc",
                ) { _ ->
                    toolExecutions++
                    "result"
                }
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val result =
                runPiAgent(
                    OpenAiWireClient(HttpClient(engine)),
                    config,
                    "test prompt",
                    emptyList(),
                    com.infinitezerone.minibgm.core.ai.tool
                        .BgmToolRegistry(listOf(tool)),
                    maxTurns = 5,
                )
            assertEquals("done", result)
            assertEquals(0, toolExecutions, "解析失败的工具调用不应执行")
            assertTrue(requestBodies.size >= 2)
            // 解析错误必须回显给模型，否则它永远不知道是自己的入参格式错了
            assertTrue(requestBodies[1].contains("参数错误"))
            assertTrue(requestBodies[1].contains("mockTool"))
        }

    @Test
    fun runPiAgent_breaks_alternating_tool_loop_before_max_turns() =
        runTest {
            var requestCount = 0
            var toolExecutions = 0
            val engine =
                MockEngine { request ->
                    requestCount++
                    val query = if (requestCount % 2 == 1) "a" else "b"
                    val responseJson =
                        if (requestCount <= 5) {
                            """
                            {
                              "id": "chatcmpl-alternating",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "tool_calls": [
                                      {
                                        "id": "call_$query",
                                        "type": "function",
                                        "function": {
                                          "name": "mockTool",
                                          "arguments": "{\"query\":\"$query\"}"
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                            """.trimIndent()
                        } else {
                            """{"id":"chatcmpl-alt-summary","choices":[{"index":0,"message":{"role":"assistant","content":"总结完成"}}]}"""
                        }
                    respond(
                        content = if (requestCount <= 5) sseBody(responseJson) else responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val tool =
                com.infinitezerone.minibgm.core.ai.tool.bgmTool(
                    name = "mockTool",
                    description = "desc",
                ) { _ ->
                    toolExecutions++
                    "result"
                }
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val result =
                runPiAgent(
                    OpenAiWireClient(HttpClient(engine)),
                    config,
                    "test prompt",
                    emptyList(),
                    com.infinitezerone.minibgm.core.ai.tool
                        .BgmToolRegistry(listOf(tool)),
                    maxTurns = 10,
                )
            // A/B 交替循环在第 5 轮（A 签名第 3 次出现）被截断，而不是跑满 10 轮
            assertEquals("总结完成", result)
            assertEquals(6, requestCount, "5 轮循环 + 1 次总结")
            assertEquals(2, toolExecutions, "仅第 1、2 轮真实执行，重复签名注入引导提示")
        }

    @Test
    fun runPiAgent_propagates_outer_timeout_instead_of_single_turn_error() =
        runTest {
            val engine =
                MockEngine { _ ->
                    kotlinx.coroutines.delay(10_000)
                    respond(
                        content = """{"id":"c","choices":[{"index":0,"message":{"role":"assistant","content":"late"}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                kotlinx.coroutines.withTimeout(100) {
                    runPiAgent(
                        OpenAiWireClient(HttpClient(engine)),
                        config,
                        "test prompt",
                        emptyList(),
                        com.infinitezerone.minibgm.core.ai.tool
                            .BgmToolRegistry(emptyList()),
                        maxTurns = 3,
                    )
                }
            }
        }

    @Test
    fun truncateToolResult_elides_middle_and_keeps_short_results_intact() {
        assertEquals("ok", truncateToolResult("ok"))
        val oversized = "a".repeat(TOOL_RESULT_MAX_CHARS + 1)
        val truncated = truncateToolResult(oversized)
        assertTrue(truncated.contains("已省略"))
        assertTrue(truncated.length < TOOL_RESULT_MAX_CHARS + 200)
        assertTrue(truncated.startsWith("a"))
        assertTrue(truncated.endsWith("a"))
    }

    @Test
    fun summarizeFinalOutcome_rethrows_cancellation() =
        runTest {
            val engine =
                MockEngine { _ ->
                    throw kotlinx.coroutines.CancellationException("用户停止")
                }
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                summarizeFinalOutcome(config, OpenAiWireClient(HttpClient(engine)), "gpt-4o-mini", emptyList())
            }
        }

    @Test
    fun chatCompletionStream_aggregates_sse_deltas_into_standard_response() =
        runTest {
            val sse =
                """
                data: {"id":"c1","choices":[{"index":0,"delta":{"role":"assistant","content":"你好"}}]}

                data: {"id":"c1","choices":[{"index":0,"delta":{"content":"，芙莉莲"}}]}

                data: {"id":"c1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"mockTool","arguments":"{\"qu"}}]}}]}

                data: {"id":"c1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ery\":\"x\"}"}}]}}]}

                data: {"id":"c1","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}

                data: [DONE]

                """.trimIndent()
            val engine =
                MockEngine { _ ->
                    respond(
                        content = sse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            val deltas = mutableListOf<String>()
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val response =
                OpenAiWireClient(HttpClient(engine)).chatCompletionStream(
                    config,
                    WireChatRequest(model = "gpt-4o", messages = listOf(WireChatMessage.user("hi")), stream = true),
                ) { deltas.add(it) }

            assertEquals(
                "你好，芙莉莲",
                response.choices
                    .single()
                    .message.content,
                "content 分片必须按序聚合",
            )
            assertEquals(listOf("你好", "，芙莉莲"), deltas, "预览回调收到的是原始增量")
            val call =
                response.choices
                    .single()
                    .message.toolCalls!!
                    .single()
            assertEquals("mockTool", call.function.name)
            assertEquals("{\"query\":\"x\"}", call.function.arguments, "跨 chunk 的 arguments 分片必须拼接")
            assertEquals("call_1", call.id)
            assertEquals("tool_calls", response.choices.single().finishReason)
            assertEquals(10, response.usage?.promptTokens)
            assertEquals(15, response.usage?.totalTokens)
        }

    @Test
    fun callTurnModel_falls_back_to_non_streaming_when_endpoint_rejects_stream() =
        runTest {
            var requestCount = 0
            val engine =
                MockEngine { request ->
                    requestCount++
                    val body = (request.body as io.ktor.http.content.TextContent).text
                    if (body.contains("\"stream\":true")) {
                        respond(
                            content = """{"error":{"message":"stream is not supported"}}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content = """{"id":"c2","choices":[{"index":0,"message":{"role":"assistant","content":"非流式回复"}}]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            val response =
                callTurnModel(
                    OpenAiWireClient(HttpClient(engine)),
                    config,
                    WireChatRequest(model = "gpt-4o", messages = listOf(WireChatMessage.user("hi")), stream = true),
                )
            assertEquals(
                "非流式回复",
                response.choices
                    .single()
                    .message.content,
            )
            assertEquals(2, requestCount, "流式被 400 拒绝后必须回退非流式")
            // 回退请求必须剥掉 stream 标记
        }

    @Test
    fun callTurnModel_does_not_retry_non_streaming_on_server_errors() =
        runTest {
            var requestCount = 0
            val engine =
                MockEngine { _ ->
                    requestCount++
                    respond(
                        content = """{"error":{"message":"boom"}}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = STUB_TOKEN)
            assertFailsWith<AiEndpointException> {
                callTurnModel(
                    OpenAiWireClient(HttpClient(engine)),
                    config,
                    WireChatRequest(model = "gpt-4o", messages = listOf(WireChatMessage.user("hi")), stream = true),
                )
            }
            // 5xx 不触发非流式回退：请求数只会来自 HTTP 层重试（HttpRequestRetry 2 次 + 首次），
            // 若发生回退则必然多出一次非流式请求
            assertTrue(requestCount <= 3, "5xx 不应触发非流式回退，实际请求数: $requestCount")
        }

    @Test
    fun buildInitialMessages_emits_role_faithful_array_within_budget() {
        val history =
            List(50) { i ->
                AiHistoryTurn(isUser = i % 2 == 0, content = "轮次内容".repeat(200) + i)
            }
        val messages = buildInitialMessages("当前输入", sanitizeAndBudgetHistory(history))
        assertEquals("system", messages.first().role)
        assertEquals("当前输入", messages.last().content)
        assertEquals("user", messages.last().role)
        // 除 system 与当前输入外，历史轮次总量受字符预算约束
        val historyChars = messages.drop(1).dropLast(1).sumOf { it.content.orEmpty().length }
        assertTrue(historyChars <= AI_HISTORY_CHAR_BUDGET, "历史轮次总量必须受预算约束: $historyChars")
        // role 保真：历史轮次只会是 user/assistant
        assertTrue(messages.drop(1).dropLast(1).all { it.role == "user" || it.role == "assistant" })
    }
}
