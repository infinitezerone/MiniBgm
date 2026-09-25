package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.ai.di.aiModule
import com.infinitezerone.minibgm.core.ai.tool.string
import com.infinitezerone.minibgm.core.ai.wire.OpenAiWireClient
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
                    apiKey = "dummy-key",
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
                    apiKey = "dummy-key",
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
                    apiKey = "dummy-key",
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
    fun execute_passes_conversation_history_in_prompt() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = "dummy-key",
                    model = "gpt-4o-mini",
                    provider = "openai",
                ),
            )
            var capturedPrompt = ""
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, prompt ->
                        capturedPrompt = prompt
                        "ok"
                    },
                )
            val history =
                listOf(
                    "user" to "帮我分析 https://example.tv/",
                    "assistant" to "正在分析",
                )
            val result = service.execute("继续执行", history)
            assertIs<AppResult.Success<String>>(result)
            assertTrue(capturedPrompt.contains("以下是先前的会话历史记录"))
            assertTrue(capturedPrompt.contains("[用户] 帮我分析 https://example.tv/"))
            assertTrue(capturedPrompt.contains("[助手] 正在分析"))
            assertTrue(capturedPrompt.contains("用户当前最新输入：\n继续执行"))
        }

    @Test
    fun execute_sanitizes_poisoned_history_from_prompt() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = "dummy-key",
                    model = "gpt-4o-mini",
                    provider = "openai",
                ),
            )
            var capturedPrompt = ""
            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    agentRunner = { _, prompt ->
                        capturedPrompt = prompt
                        "ok"
                    },
                )
            val dirtyHistory =
                listOf(
                    "assistant" to "<tool_call>searchWeb<param_key>query</param_key><param_value>test</param_value></tool_call>",
                    "assistant" to "❌ 执行出错：AI 响应超时（180 秒）",
                )
            val result = service.execute("再次搜索", dirtyHistory)
            assertIs<AppResult.Success<String>>(result)
            // 毒化标签被全部清洗，若没有合法历史，直接以最新 prompt 发送
            assertFalse(capturedPrompt.contains("<tool_call>"))
            assertFalse(capturedPrompt.contains("<param_key>"))
            assertFalse(capturedPrompt.contains("执行出错"))
            assertEquals("再次搜索", capturedPrompt)
        }

    @Test
    fun execute_with_default_runner_handles_connection_failure() =
        runTest {
            fakeSettingsRepository.setAiConfig(
                AiConfig(
                    endpoint = "http://localhost:11434/v1",
                    apiKey = "dummy-key",
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
                    agentRunner = { _, _, _ -> throw kotlinx.coroutines.CancellationException("用户停止") },
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
                    apiKey = "sk-test",
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
                    assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
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
                    apiKey = "test-key",
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
                        content = responseJson,
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
                    apiKey = "test-key",
                    model = "gpt-4o",
                    provider = "openai",
                ),
            )

            val service =
                DefaultBgmAiAgentService(
                    settingsRepository = fakeSettingsRepository,
                    httpClient = HttpClient(engine),
                    agentRunner = { config, prompt, _ ->
                        runPiAgent(
                            wireClient = OpenAiWireClient(HttpClient(engine)),
                            config = config,
                            prompt = prompt,
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
                    apiKey = "test-key",
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
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = "key")
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
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = "key")
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
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val wireClient = OpenAiWireClient(HttpClient(engine))
            val config = AiConfig(endpoint = "https://api.openai.com/v1", apiKey = "key")
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
            val result = runPiAgent(wireClient, config, "test prompt", tools, maxTurns = 5)
            assertEquals(DEFAULT_SUMMARY_FALLBACK, result)
            assertTrue(callCount <= 4)
        }
}
