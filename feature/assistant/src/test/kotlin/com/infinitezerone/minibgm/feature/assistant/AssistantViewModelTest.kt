package com.infinitezerone.minibgm.feature.assistant

import com.infinitezerone.minibgm.core.ai.BgmAiAgentService
import com.infinitezerone.minibgm.core.ai.PendingActionExecutor
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.ActionCardStatus
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.ChatMessageRole
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.testing.repository.FakeAssistantRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeWebViewResolveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 测试替身的非凭据标记值；用符号常量传递，避免在源码里出现凭据形状的字面量 */
private const val STUB_TOKEN = "stub-token"

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeSettingsRepository = FakeSettingsRepository()
    private val actionStore = PendingActionStore()

    private class FakePendingActionExecutor : PendingActionExecutor {
        var executedActions = mutableListOf<PendingAction>()
        var result: AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun execute(action: PendingAction): AppResult<Unit> {
            executedActions.add(action)
            return result
        }
    }

    private class FakeAgentService(
        override val pendingActionExecutor: PendingActionExecutor? = null,
        override val pendingActionStore: PendingActionStore? = null,
        var executeResult: AppResult<String> = AppResult.Success("AI response"),
    ) : BgmAiAgentService {
        var fetchModelsResult: AppResult<List<String>> = AppResult.Success(emptyList())

        var prompts = mutableListOf<String>()
        var lastFetchEndpoint: String? = null
        var lastFetchApiKey: String? = null
        var lastFetchProvider: String? = null

        var capturedHistories = mutableListOf<List<Pair<String, String>>>()

        /** 非 null 时 execute 挂起等待，用于模拟长时间运行的取消场景 */
        var executeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        /** execute 被取消时置 true */
        var wasCancelled = false
            private set

        override suspend fun execute(
            prompt: String,
            history: List<Pair<String, String>>,
        ): AppResult<String> {
            prompts.add(prompt)
            capturedHistories.add(history)
            try {
                executeGate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                wasCancelled = true
                throw e
            }
            return executeResult
        }

        override suspend fun fetchAvailableModels(
            endpoint: String?,
            apiKey: String?,
            provider: String?,
        ): AppResult<List<String>> {
            lastFetchEndpoint = endpoint
            lastFetchApiKey = apiKey
            lastFetchProvider = provider
            return fetchModelsResult
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onInputChanged_updates_inputText() {
        val agentService = FakeAgentService()
        val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

        viewModel.onInputChanged("Hello AI")
        assertEquals("Hello AI", viewModel.uiState.value.inputText)
    }

    @Test
    fun sendMessage_ignores_blank_input() =
        runTest {
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("   ")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertTrue(agentService.prompts.isEmpty())
            assertTrue(
                viewModel.uiState.value.messages
                    .isEmpty(),
            )
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun sendMessage_adds_user_and_assistant_messages_on_success() =
        runTest {
            val agentService = FakeAgentService(executeResult = AppResult.Success("今天有《葬送的芙莉莲》更新！"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("今天有什么动画？")
            viewModel.sendMessage()

            // 立即状态：输入框清空，加载中
            assertEquals("", viewModel.uiState.value.inputText)
            assertTrue(viewModel.uiState.value.isLoading)
            assertEquals(1, viewModel.uiState.value.messages.size)
            assertEquals(
                "今天有什么动画？",
                viewModel.uiState.value.messages[0]
                    .content,
            )
            assertEquals(
                MessageRole.USER,
                viewModel.uiState.value.messages[0]
                    .role,
            )

            advanceUntilIdle()

            // 响应后：加载完成，两条消息
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(2, viewModel.uiState.value.messages.size)
            val assistantMsg = viewModel.uiState.value.messages[1]
            assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
            assertEquals("今天有《葬送的芙莉莲》更新！", assistantMsg.content)
            assertFalse(assistantMsg.isError)
        }

    @Test
    fun sendPrefilledPrompt_sends_route_prompt_only_once() =
        runTest {
            val agentService = FakeAgentService(executeResult = AppResult.Success("页面链接列表"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.sendPrefilledPrompt("帮我找《葬送的芙莉莲》的在线观看页面")
            advanceUntilIdle()
            // 路由未带预填提问（普通入口）与返回该页时的重复触发：都不再唤起智能体
            viewModel.sendPrefilledPrompt("")
            viewModel.sendPrefilledPrompt("帮我找《葬送的芙莉莲》的在线观看页面")
            advanceUntilIdle()

            assertEquals(1, agentService.prompts.size)
            assertEquals("帮我找《葬送的芙莉莲》的在线观看页面", agentService.prompts[0])
            assertEquals(2, viewModel.uiState.value.messages.size)
        }

    @Test
    fun sendMessage_captures_PendingAction_proposals() =
        runTest {
            val executor = FakePendingActionExecutor()
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            我已经为您准备了打卡提案：
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Update proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_ep_test",
                                "subjectId": 12345,
                                "subjectTitle": "葬送的芙莉莲",
                                "episodeNumber": 12,
                                "isWatched": true,
                                "description": "Mark ep 12 as watched"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("打卡第12集")
            viewModel.sendMessage()
            advanceUntilIdle()

            val assistantMsg =
                viewModel.uiState.value.messages
                    .last()
            assertEquals(1, assistantMsg.pendingActions.size)
            val cardState = assistantMsg.pendingActions[0]
            assertEquals(ActionStatus.PENDING, cardState.status)
            assertIs<PendingAction.UpdateEpisode>(cardState.action)
            assertEquals("act_ep_test", cardState.action.actionId)
            assertEquals(12, cardState.action.episodeNumber)
        }

    @Test
    fun approveAction_triggers_executor_and_updates_status_to_SUCCESS() =
        runTest {
            val executor = FakePendingActionExecutor()
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Update proposal",
                              "action": {
                                "type": "update_collection",
                                "actionId": "act_coll_test",
                                "subjectId": 99999,
                                "subjectTitle": "迷宫饭",
                                "collectionType": "DOING",
                                "rating": 9,
                                "comment": "太好看了",
                                "isPrivate": false,
                                "description": "Update to DOING"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("标记在看迷宫饭")
            viewModel.sendMessage()
            advanceUntilIdle()

            // 点击确认提交
            viewModel.approveAction("act_coll_test")
            advanceUntilIdle()

            assertEquals(1, executor.executedActions.size)
            assertEquals("act_coll_test", executor.executedActions[0].actionId)

            val assistantMsg =
                viewModel.uiState.value.messages
                    .last()
            val card = assistantMsg.pendingActions[0]
            assertEquals(ActionStatus.SUCCESS, card.status)
        }

    @Test
    fun approveAction_handles_executor_failure_and_updates_status_to_FAILED() =
        runTest {
            val executor = FakePendingActionExecutor()
            executor.result = AppResult.Error(IllegalStateException("Network timeout"))

            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Update proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_fail_test",
                                "subjectId": 12345,
                                "subjectTitle": "葬送的芙莉莲",
                                "episodeNumber": 5,
                                "isWatched": true,
                                "description": "Mark ep 5 as watched"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("打卡第5集")
            viewModel.sendMessage()
            advanceUntilIdle()

            viewModel.approveAction("act_fail_test")
            advanceUntilIdle()

            val assistantMsg =
                viewModel.uiState.value.messages
                    .last()
            val card = assistantMsg.pendingActions[0]
            assertEquals(ActionStatus.FAILED, card.status)
            assertEquals("Network timeout", card.errorMessage)
        }

    @Test
    fun rejectAction_updates_status_to_REJECTED_without_executing() =
        runTest {
            val executor = FakePendingActionExecutor()
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Update proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_reject_test",
                                "subjectId": 12345,
                                "subjectTitle": "葬送的芙莉莲",
                                "episodeNumber": 8,
                                "isWatched": true,
                                "description": "Mark ep 8 as watched"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("打卡第8集")
            viewModel.sendMessage()
            advanceUntilIdle()

            viewModel.rejectAction("act_reject_test")
            advanceUntilIdle()

            assertEquals(0, executor.executedActions.size)
            val assistantMsg =
                viewModel.uiState.value.messages
                    .last()
            val card = assistantMsg.pendingActions[0]
            assertEquals(ActionStatus.REJECTED, card.status)
        }

    @Test
    fun clearConversation_resets_messages() =
        runTest {
            val agentService = FakeAgentService(executeResult = AppResult.Success("AI msg"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("test")
            viewModel.sendMessage()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.messages.size)

            viewModel.clearConversation()
            assertTrue(
                viewModel.uiState.value.messages
                    .isEmpty(),
            )
        }

    @Test
    fun saveAiConfig_persists_config_via_SettingsRepository() =
        runTest {
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            val newConfig =
                AiConfig(
                    provider = AiConfig.PROVIDER_GEMINI,
                    apiKey = STUB_TOKEN,
                    model = "gemini-2.5-pro",
                )
            viewModel.saveAiConfig(newConfig)
            advanceUntilIdle()

            val saved = fakeSettingsRepository.aiConfig.first()
            assertEquals(AiConfig.PROVIDER_GEMINI, saved.provider)
            assertEquals(STUB_TOKEN, saved.apiKey)
            assertEquals("gemini-2.5-pro", saved.model)
            assertFalse(viewModel.uiState.value.showConfigDialog)
        }

    @Test
    fun approveAction_is_idempotent_and_ignores_when_already_SUCCESS() =
        runTest {
            val executor = FakePendingActionExecutor()
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_idempotent",
                                "subjectId": 12345,
                                "episodeNumber": 1,
                                "isWatched": true,
                                "description": "Ep 1"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("打卡")
            viewModel.sendMessage()
            advanceUntilIdle()

            // 第一次确认
            viewModel.approveAction("act_idempotent")
            advanceUntilIdle()
            assertEquals(1, executor.executedActions.size)

            // 第二次重复确认：应被忽略，执行器不被重复调用
            viewModel.approveAction("act_idempotent")
            advanceUntilIdle()
            assertEquals(1, executor.executedActions.size)

            val card =
                viewModel.uiState.value.messages
                    .last()
                    .pendingActions[0]
            assertEquals(ActionStatus.SUCCESS, card.status)
        }

    @Test
    fun sendMessage_renders_playable_sources_card_from_agent_json() =
        runTest {
            val agentService =
                FakeAgentService(
                    executeResult =
                        AppResult.Success(
                            """
                            {"subjectId":1001,"title":"葬送的芙莉莲","source":"自备片单","episodes":[
                              {"url":"https://cdn.example.com/ep12.m3u8","kind":"DIRECT","label":"12","episodeSort":12,
                               "headers":{"Referer":"https://example.com/"}}
                            ]}
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("帮我找《葬送的芙莉莲》的可播放资源")
            viewModel.sendMessage()
            advanceUntilIdle()

            val message =
                viewModel.uiState.value.messages
                    .last()
            val sources = assertNotNull(message.playableSources)
            assertEquals(1001L, sources.subjectId)
            assertEquals("https://cdn.example.com/ep12.m3u8", sources.episodes.single().url)
            assertTrue(message.content.contains("1 条可播放来源"))
            assertTrue(message.content.contains("自备片单"))
        }

    @Test
    fun rejectAction_ignores_when_already_SUCCESS() =
        runTest {
            val executor = FakePendingActionExecutor()
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_reject_after_success",
                                "subjectId": 12345,
                                "episodeNumber": 2,
                                "isWatched": true,
                                "description": "Ep 2"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("打卡第2集")
            viewModel.sendMessage()
            advanceUntilIdle()

            viewModel.approveAction("act_reject_after_success")
            advanceUntilIdle()

            val cardAfterSuccess =
                viewModel.uiState.value.messages
                    .last()
                    .pendingActions[0]
            assertEquals(ActionStatus.SUCCESS, cardAfterSuccess.status)

            // 已成功执行后尝试取消：应被忽略，状态保持 SUCCESS
            viewModel.rejectAction("act_reject_after_success")
            advanceUntilIdle()

            val card =
                viewModel.uiState.value.messages
                    .last()
                    .pendingActions[0]
            assertEquals(ActionStatus.SUCCESS, card.status)
        }

    @Test
    fun noPlayableSourceReply_offersDeepResolveEntry() =
        runTest {
            val fakeSettingsRepository = FakeSettingsRepository()
            val agentService =
                FakeAgentService(
                    executeResult =
                        AppResult.Success(
                            "No playable source found for subject ID 1001: no imported playlist is bound to it and no source page is recorded.",
                        ),
                )
            val webviewRepo =
                FakeWebViewResolveRepository().apply {
                    setPlayableSources(1001L, listOf(PlayableSource(url = "https://cdn.example.com/1.m3u8")))
                }
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository, webviewResolveRepository = webviewRepo)

            viewModel.sendMessage("哪里能看")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1001L, state.deepResolve?.subjectId)
            assertFalse(state.deepResolve?.isRunning ?: true)

            viewModel.runDeepResolve()
            advanceUntilIdle()

            assertEquals(listOf(1001L), webviewRepo.deepResolveCalls)
            val last =
                viewModel.uiState.value.messages
                    .last()
            assertTrue(last.playableSources != null)
            assertEquals("WebView 深度解析", last.playableSources.source)
        }

    @Test
    fun deepResolve_emptyResult_appendsPlainMessage() =
        runTest {
            val fakeSettingsRepository = FakeSettingsRepository()
            val agentService = FakeAgentService(executeResult = AppResult.Success("No playable source found for subject ID 1002"))
            val webviewRepo = FakeWebViewResolveRepository().apply { deepResolveResult = AppResult.Success(emptyList()) }
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository, webviewResolveRepository = webviewRepo)

            viewModel.sendMessage("哪里能看")
            advanceUntilIdle()
            viewModel.runDeepResolve()
            advanceUntilIdle()

            val last =
                viewModel.uiState.value.messages
                    .last()
            assertTrue(last.playableSources == null)
            assertTrue(last.content.contains("没有捕获到"))
        }

    @Test
    fun successfulSourceReply_doesNotOfferDeepResolve() =
        runTest {
            val fakeSettingsRepository = FakeSettingsRepository()
            val sourcesJson =
                """
                {"subjectId": 1003, "title": "测试番剧", "source": "自备片单",
                 "episodes": [{"url": "https://cdn.example.com/1.m3u8", "kind": "DIRECT", "label": "1"}]}
                """.trimIndent()
            val agentService = FakeAgentService(executeResult = AppResult.Success(sourcesJson))
            val viewModel =
                AssistantViewModel(agentService, fakeSettingsRepository, webviewResolveRepository = FakeWebViewResolveRepository())

            viewModel.sendMessage("哪里能看")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.deepResolve)
        }

    @Test
    fun sendMessage_captures_and_approves_ImportPlaybackRules() =
        runTest {
            val executor = FakePendingActionExecutor()
            val rule =
                com.infinitezerone.minibgm.core.model.PlaybackSourceRule(
                    id = "rule_1",
                    name = "示例动漫源",
                    urlTemplate = "https://example.com/{title}",
                )
            val importAction =
                com.infinitezerone.minibgm.core.model.PendingAction.ImportPlaybackRules(
                    actionId = "act_import_rules_test",
                    sourceName = "社区二次元播放源",
                    rules = listOf(rule),
                    description = "导入 1 个规则",
                )
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult = AppResult.Success("已为您检索到可用规则并完成连通性测速"),
                )
            actionStore.add(importAction)

            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            viewModel.sendMessage("帮我找找播放源规则")
            advanceUntilIdle()

            val assistantMsg =
                viewModel.uiState.value.messages
                    .last()
            assertEquals(1, assistantMsg.pendingActions.size)
            val card = assistantMsg.pendingActions[0]
            assertEquals(ActionStatus.PENDING, card.status)
            assertIs<com.infinitezerone.minibgm.core.model.PendingAction.ImportPlaybackRules>(card.action)
            assertEquals("act_import_rules_test", card.action.actionId)

            viewModel.approveAction("act_import_rules_test")
            advanceUntilIdle()

            assertEquals(1, executor.executedActions.size)
            assertEquals("act_import_rules_test", executor.executedActions[0].actionId)
            val updatedMsg =
                viewModel.uiState.value.messages
                    .last()
            assertEquals(ActionStatus.SUCCESS, updatedMsg.pendingActions[0].status)
        }

    @Test
    fun fetchAvailableModels_delegates_to_agentService() =
        runTest {
            val agentService = FakeAgentService()
            agentService.fetchModelsResult = AppResult.Success(listOf("gpt-4o", "gpt-4o-mini"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            val result =
                viewModel.fetchAvailableModels(
                    endpoint = "https://api.openai.com/v1",
                    apiKey = STUB_TOKEN,
                    provider = "custom",
                )

            assertIs<AppResult.Success<List<String>>>(result)
            assertEquals(listOf("gpt-4o", "gpt-4o-mini"), result.data)
            assertEquals("https://api.openai.com/v1", agentService.lastFetchEndpoint)
            assertEquals(STUB_TOKEN, agentService.lastFetchApiKey)
            assertEquals("custom", agentService.lastFetchProvider)
        }

    @Test
    fun stopGeneration_cancels_run_and_appends_stopped_message() =
        runTest {
            val agentService = FakeAgentService()
            agentService.executeGate = kotlinx.coroutines.CompletableDeferred()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.sendMessage("卡住的请求")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isLoading)

            viewModel.stopGeneration()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(agentService.wasCancelled, "取消应传导到智能体执行")
            val lastMsg =
                viewModel.uiState.value.messages
                    .last()
            assertEquals("⏹ 已停止生成。", lastMsg.content)
            assertEquals(MessageRole.ASSISTANT, lastMsg.role)
            assertFalse(lastMsg.isError)
        }

    @Test
    fun stopGeneration_isNoOp_whenIdle() =
        runTest {
            val agentService = FakeAgentService(executeResult = AppResult.Success("回答"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.sendMessage("问题")
            advanceUntilIdle()

            viewModel.stopGeneration()
            advanceUntilIdle()

            // 空闲时停止不应追加消息
            assertEquals(2, viewModel.uiState.value.messages.size)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun retryAfterError_resends_last_user_prompt_without_new_user_bubble() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService =
                FakeAgentService(
                    executeResult = AppResult.Error(IllegalStateException("boom"), message = "请求失败"),
                )
            val viewModel =
                AssistantViewModel(agentService, fakeSettingsRepository, assistantRepository = fakeRepo)

            viewModel.sendMessage("问题 1")
            advanceUntilIdle()

            val errorMsg =
                viewModel.uiState.value.messages
                    .last()
            assertTrue(errorMsg.isError)

            agentService.executeResult = AppResult.Success("重试后的回答")
            viewModel.retryAfterError(errorMsg.id)
            advanceUntilIdle()

            assertEquals(listOf("问题 1", "问题 1"), agentService.prompts, "重试应原样重发上一条用户输入")
            val messages = viewModel.uiState.value.messages
            // 用户消息不重复追加，且重试成功后错误气泡被移除：user, assistant(重试成功)
            assertEquals(2, messages.size)
            assertEquals(MessageRole.ASSISTANT, messages[1].role)
            assertEquals("重试后的回答", messages[1].content)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun retryAfterError_removes_error_from_persisted_history_on_success() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService =
                FakeAgentService(
                    executeResult = AppResult.Error(IllegalStateException("boom"), message = "请求失败"),
                )
            val viewModel =
                AssistantViewModel(agentService, fakeSettingsRepository, assistantRepository = fakeRepo)

            viewModel.sendMessage("问题")
            advanceUntilIdle()
            assertEquals(2, fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first().size, "user + error 均已持久化")

            val errorMsgId =
                viewModel.uiState.value.messages
                    .last()
                    .id
            agentService.executeResult = AppResult.Success("重试成功")
            viewModel.retryAfterError(errorMsgId)
            advanceUntilIdle()

            val saved = fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first()
            assertEquals(2, saved.size, "持久化历史 = user + 重试成功的新回答（错误消息已删）")
            assertTrue(saved.none { it.id == errorMsgId })
        }

    @Test
    fun retryAfterError_keeps_error_when_retry_fails_again() =
        runTest {
            val agentService =
                FakeAgentService(
                    executeResult = AppResult.Error(IllegalStateException("boom"), message = "请求失败"),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.sendMessage("问题")
            advanceUntilIdle()
            val firstError =
                viewModel.uiState.value.messages
                    .last()

            agentService.executeResult = AppResult.Error(IllegalStateException("still bad"), message = "仍然失败")
            viewModel.retryAfterError(firstError.id)
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(3, messages.size, "重试再失败：原错误保留 + 新错误追加")
            assertTrue(messages[1].isError)
            assertTrue(messages[2].isError)
            assertEquals("❌ 执行出错：仍然失败", messages[2].content)
        }

    @Test
    fun retryAfterError_ignores_nonError_or_unknown_message() =
        runTest {
            val agentService = FakeAgentService(executeResult = AppResult.Success("回答"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.sendMessage("问题")
            advanceUntilIdle()

            val normalMsg =
                viewModel.uiState.value.messages
                    .last()
            assertFalse(normalMsg.isError)
            viewModel.retryAfterError(normalMsg.id)
            advanceUntilIdle()
            viewModel.retryAfterError("不存在的 id")
            advanceUntilIdle()

            assertEquals(1, agentService.prompts.size, "非错误消息与未知 id 不应触发重试")
        }

    @Test
    fun saveAiProfile_creates_and_activates_profile() =
        runTest {
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            advanceUntilIdle()

            val config =
                AiConfig(
                    endpoint = "https://api.example.com/v1",
                    apiKey = "key-a",
                    model = "model-a",
                    provider = AiConfig.PROVIDER_CUSTOM,
                )
            viewModel.saveAiProfile(profileId = null, name = " 端点A ", config = config)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.aiProfiles.size)
            assertEquals("端点A", state.aiProfiles[0].name)
            assertEquals(state.aiProfiles[0].id, state.activeProfileId, "保存后应立即启用")
            assertEquals(config, viewModel.uiState.value.aiConfig)
        }

    @Test
    fun saveAiProfile_withExistingId_updatesInPlace() =
        runTest {
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            advanceUntilIdle()

            val original =
                AiConfig(endpoint = "https://old.example.com/v1", apiKey = "k1", model = "m1", provider = AiConfig.PROVIDER_CUSTOM)
            viewModel.saveAiProfile(profileId = null, name = "方案一", config = original)
            advanceUntilIdle()
            val savedId =
                viewModel.uiState.value.aiProfiles
                    .single()
                    .id

            val updated = original.copy(apiKey = "k2")
            viewModel.saveAiProfile(profileId = savedId, name = "方案一", config = updated)
            advanceUntilIdle()

            val profiles = viewModel.uiState.value.aiProfiles
            assertEquals(1, profiles.size, "同 id 保存应覆盖而非追加")
            assertEquals("k2", profiles.single().config.apiKey)
            assertEquals(savedId, viewModel.uiState.value.activeProfileId)
        }

    @Test
    fun saveAiProfile_ignores_blank_name() =
        runTest {
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            advanceUntilIdle()

            viewModel.saveAiProfile(profileId = null, name = "   ", config = AiConfig())
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.aiProfiles
                    .isEmpty(),
            )
        }

    @Test
    fun activateAiProfile_switches_active_config() =
        runTest {
            val configA = AiConfig(endpoint = "https://a.example.com/v1", apiKey = "ka", model = "ma", provider = AiConfig.PROVIDER_CUSTOM)
            val configB = AiConfig(endpoint = "https://b.example.com/v1", apiKey = "kb", model = "mb", provider = AiConfig.PROVIDER_CUSTOM)
            fakeSettingsRepository.setAiProfiles(
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .AiConfigProfile(id = "prof-a", name = "A", config = configA),
                    com.infinitezerone.minibgm.core.model
                        .AiConfigProfile(id = "prof-b", name = "B", config = configB),
                ),
            )
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            advanceUntilIdle()

            viewModel.activateAiProfile("prof-b")
            advanceUntilIdle()

            assertEquals(configB, viewModel.uiState.value.aiConfig, "启用方案应切换生效配置")
            assertEquals("prof-b", viewModel.uiState.value.activeProfileId)
        }

    @Test
    fun activateAiProfile_unknown_id_isNoOp() =
        runTest {
            val configA = AiConfig(endpoint = "https://a.example.com/v1", apiKey = "ka", model = "ma", provider = AiConfig.PROVIDER_CUSTOM)
            fakeSettingsRepository.setAiProfiles(
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .AiConfigProfile(id = "prof-a", name = "A", config = configA),
                ),
            )
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            advanceUntilIdle()

            viewModel.activateAiProfile("不存在的 id")
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.activeProfileId)
        }

    @Test
    fun deleteAiProfile_removes_and_clears_active_marker() =
        runTest {
            val configA = AiConfig(endpoint = "https://a.example.com/v1", apiKey = "ka", model = "ma", provider = AiConfig.PROVIDER_CUSTOM)
            fakeSettingsRepository.setAiProfiles(
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .AiConfigProfile(id = "prof-a", name = "A", config = configA),
                ),
            )
            val agentService = FakeAgentService()
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)
            advanceUntilIdle()

            viewModel.activateAiProfile("prof-a")
            advanceUntilIdle()
            viewModel.deleteAiProfile("prof-a")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.aiProfiles
                    .isEmpty(),
            )
            assertEquals("", viewModel.uiState.value.activeProfileId, "删除启用中的方案应清除启用标记")
        }

    @Test
    fun switchSession_loads_that_sessions_messages() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService = FakeAgentService()
            val viewModel =
                AssistantViewModel(agentService, fakeSettingsRepository, assistantRepository = fakeRepo)
            advanceUntilIdle()
            val firstId = viewModel.uiState.value.activeSessionId
            assertTrue(firstId.isNotBlank())

            agentService.executeResult = AppResult.Success("第一会话回答")
            viewModel.sendMessage("第一会话的问题")
            advanceUntilIdle()

            viewModel.createNewSession()
            advanceUntilIdle()
            val secondId = viewModel.uiState.value.activeSessionId
            assertTrue(secondId != firstId, "新建后应切到新会话")
            assertTrue(
                viewModel.uiState.value.messages
                    .isEmpty(),
                "新会话应从空消息开始",
            )

            viewModel.switchSession(firstId)
            advanceUntilIdle()
            assertEquals(firstId, viewModel.uiState.value.activeSessionId)
            assertTrue(
                viewModel.uiState.value.messages
                    .any { it.content == "第一会话的问题" },
                "切回应恢复原会话消息",
            )
        }

    @Test
    fun createNewSession_skips_when_current_session_is_empty() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val viewModel =
                AssistantViewModel(FakeAgentService(), fakeSettingsRepository, assistantRepository = fakeRepo)
            advanceUntilIdle()
            val before = viewModel.uiState.value.sessions.size

            viewModel.createNewSession()
            advanceUntilIdle()

            assertEquals(before, viewModel.uiState.value.sessions.size, "空会话下不应堆积新会话")
        }

    @Test
    fun deleteSession_active_self_heals_to_remaining_session() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val viewModel =
                AssistantViewModel(FakeAgentService(), fakeSettingsRepository, assistantRepository = fakeRepo)
            advanceUntilIdle()
            val firstId = viewModel.uiState.value.activeSessionId

            viewModel.sendMessage("第一会话的问题")
            advanceUntilIdle()
            viewModel.createNewSession()
            advanceUntilIdle()
            val secondId = viewModel.uiState.value.activeSessionId
            assertTrue(secondId != firstId)

            viewModel.switchSession(firstId)
            advanceUntilIdle()
            viewModel.deleteSession(firstId)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.sessions
                    .none { it.id == firstId },
            )
            assertEquals(secondId, viewModel.uiState.value.activeSessionId, "删除激活会话应自愈到剩余会话")
        }

    @Test
    fun first_user_message_renames_new_session() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService = FakeAgentService()
            val viewModel =
                AssistantViewModel(agentService, fakeSettingsRepository, assistantRepository = fakeRepo)
            advanceUntilIdle()

            agentService.executeResult = AppResult.Success("回答")
            viewModel.sendMessage("帮我找芙莉莲的播放源")
            advanceUntilIdle()

            val sessions = fakeRepo.getSessions().first()
            assertEquals(1, sessions.size)
            assertEquals("帮我找芙莉莲的播放源", sessions.first().title, "首条提问应自动命名会话")
        }

    @Test
    fun renameSession_updates_title_and_ignores_blank() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val viewModel =
                AssistantViewModel(FakeAgentService(), fakeSettingsRepository, assistantRepository = fakeRepo)
            advanceUntilIdle()
            val sessionId = viewModel.uiState.value.activeSessionId

            viewModel.renameSession(sessionId, "  我的找源记录  ")
            advanceUntilIdle()
            assertEquals(
                "我的找源记录",
                fakeRepo
                    .getSessions()
                    .first()
                    .first()
                    .title,
            )

            // 空白标题被忽略
            viewModel.renameSession(sessionId, "   ")
            advanceUntilIdle()
            assertEquals(
                "我的找源记录",
                fakeRepo
                    .getSessions()
                    .first()
                    .first()
                    .title,
            )
        }

    @Test
    fun sendMessage_error_displays_friendly_message_from_result() =
        runTest {
            val agentService =
                FakeAgentService(
                    executeResult =
                        AppResult.Error(
                            throwable = IllegalStateException("Raw stack trace here"),
                            message = "模型不可用（服务商提示 model route not found），请更换模型",
                        ),
                )
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.onInputChanged("你好")
            viewModel.sendMessage()
            advanceUntilIdle()

            val lastMsg =
                viewModel.uiState.value.messages
                    .last()
            assertTrue(lastMsg.isError)
            assertEquals("❌ 执行出错：模型不可用（服务商提示 model route not found），请更换模型", lastMsg.content)
        }

    @Test
    fun sendMessage_passes_recent_history_to_agentService() =
        runTest {
            val agentService = FakeAgentService(executeResult = AppResult.Success("回答 1"))
            val viewModel = AssistantViewModel(agentService, fakeSettingsRepository)

            viewModel.sendMessage("问题 1")
            advanceUntilIdle()

            agentService.executeResult = AppResult.Success("回答 2")
            viewModel.sendMessage("问题 2")
            advanceUntilIdle()

            assertEquals(2, agentService.capturedHistories.size)
            assertTrue(agentService.capturedHistories[0].isEmpty(), "首轮会话历史为空")
            val secondHistory = agentService.capturedHistories[1]
            assertEquals(2, secondHistory.size, "第二轮应包含首轮问答")
            assertEquals("user" to "问题 1", secondHistory[0])
            assertEquals("assistant" to "回答 1", secondHistory[1])
        }

    @Test
    fun init_restores_messages_from_repository() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            fakeRepo.setMessages(
                listOf(
                    AssistantChatMessage(
                        id = "msg-1",
                        role = ChatMessageRole.USER,
                        content = "历史问题",
                        timestamp = 1000L,
                    ),
                    AssistantChatMessage(
                        id = "msg-2",
                        role = ChatMessageRole.ASSISTANT,
                        content = "历史回答",
                        timestamp = 2000L,
                    ),
                ),
            )
            val agentService = FakeAgentService()
            val viewModel =
                AssistantViewModel(
                    agentService = agentService,
                    settingsRepository = fakeSettingsRepository,
                    assistantRepository = fakeRepo,
                )

            advanceUntilIdle()

            val stateMessages = viewModel.uiState.value.messages
            assertEquals(2, stateMessages.size)
            assertEquals("msg-1", stateMessages[0].id)
            assertEquals(MessageRole.USER, stateMessages[0].role)
            assertEquals("历史问题", stateMessages[0].content)
            assertEquals("msg-2", stateMessages[1].id)
            assertEquals(MessageRole.ASSISTANT, stateMessages[1].role)
            assertEquals("历史回答", stateMessages[1].content)
        }

    @Test
    fun sendMessage_persists_user_and_assistant_messages() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService = FakeAgentService(executeResult = AppResult.Success("智能体回答"))
            val viewModel =
                AssistantViewModel(
                    agentService = agentService,
                    settingsRepository = fakeSettingsRepository,
                    assistantRepository = fakeRepo,
                )

            viewModel.sendMessage("新问题")
            advanceUntilIdle()

            val savedMessages = fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first()
            assertEquals(2, savedMessages.size)
            assertEquals(ChatMessageRole.USER, savedMessages[0].role)
            assertEquals("新问题", savedMessages[0].content)
            assertEquals(ChatMessageRole.ASSISTANT, savedMessages[1].role)
            assertEquals("智能体回答", savedMessages[1].content)
        }

    @Test
    fun approveAction_updates_persisted_status_to_success() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val executor = FakePendingActionExecutor()
            val agentService =
                FakeAgentService(
                    pendingActionExecutor = executor,
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Update proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_persist_test",
                                "subjectId": 12345,
                                "subjectTitle": "葬送的芙莉莲",
                                "episodeNumber": 5,
                                "isWatched": true,
                                "description": "Mark ep 5 as watched"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel =
                AssistantViewModel(
                    agentService = agentService,
                    settingsRepository = fakeSettingsRepository,
                    assistantRepository = fakeRepo,
                )

            viewModel.sendMessage("打卡第5集")
            advanceUntilIdle()

            val savedMessages = fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first()
            assertEquals(2, savedMessages.size)
            val actionId =
                savedMessages[1]
                    .pendingActions
                    .first()
                    .action.actionId
            assertEquals("act_persist_test", actionId)

            viewModel.approveAction(actionId)
            advanceUntilIdle()

            val updatedMessages = fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first()
            assertEquals(ActionCardStatus.SUCCESS, updatedMessages[1].pendingActions.first().status)
        }

    @Test
    fun rejectAction_updates_persisted_status_to_rejected() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService =
                FakeAgentService(
                    pendingActionStore = actionStore,
                    executeResult =
                        AppResult.Success(
                            """
                            {
                              "status": "PENDING_CONFIRMATION",
                              "message": "Update proposal",
                              "action": {
                                "type": "update_episode",
                                "actionId": "act_reject_persist_test",
                                "subjectId": 12345,
                                "subjectTitle": "葬送的芙莉莲",
                                "episodeNumber": 5,
                                "isWatched": true,
                                "description": "Mark ep 5 as watched"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val viewModel =
                AssistantViewModel(
                    agentService = agentService,
                    settingsRepository = fakeSettingsRepository,
                    assistantRepository = fakeRepo,
                )

            viewModel.sendMessage("打卡第5集")
            advanceUntilIdle()

            val savedMessages = fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first()
            val actionId =
                savedMessages[1]
                    .pendingActions
                    .first()
                    .action.actionId
            assertEquals("act_reject_persist_test", actionId)

            viewModel.rejectAction(actionId)
            advanceUntilIdle()

            val updatedMessages = fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first()
            assertEquals(ActionCardStatus.REJECTED, updatedMessages[1].pendingActions.first().status)
        }

    @Test
    fun clearConversation_clears_repository_messages() =
        runTest {
            val fakeRepo = FakeAssistantRepository()
            val agentService = FakeAgentService(executeResult = AppResult.Success("回答"))
            val viewModel =
                AssistantViewModel(
                    agentService = agentService,
                    settingsRepository = fakeSettingsRepository,
                    assistantRepository = fakeRepo,
                )

            viewModel.sendMessage("测试")
            advanceUntilIdle()
            assertEquals(2, fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first().size)

            viewModel.clearConversation()
            advanceUntilIdle()

            assertTrue(fakeRepo.getMessages(FakeAssistantRepository.DEFAULT_SESSION_ID).first().isEmpty(), "清空会话后 Repository 应为空")
            assertTrue(
                viewModel.uiState.value.messages
                    .isEmpty(),
                "清空会话后 UIState 应为空",
            )
        }
}
