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
                            ): List<com.infinitezerone.minibgm.core.model.PlayableSource> = emptyList()

                            override suspend fun resolveTemplate(
                                url: String,
                                headers: Map<String, String>,
                                epNumber: Float,
                                siteName: String,
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
}
