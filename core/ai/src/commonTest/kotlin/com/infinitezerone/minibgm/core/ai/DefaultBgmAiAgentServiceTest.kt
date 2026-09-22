package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.ai.di.aiModule
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
                    assertEquals("MiniBgm/1.0 (Android)", request.headers[HttpHeaders.UserAgent])
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
}
