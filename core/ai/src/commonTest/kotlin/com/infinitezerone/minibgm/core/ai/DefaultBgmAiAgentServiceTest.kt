package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.ai.di.aiModule
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
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
}
