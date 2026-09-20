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

        override suspend fun execute(
            prompt: String,
            history: List<Pair<String, String>>,
        ): AppResult<String> {
            prompts.add(prompt)
            capturedHistories.add(history)
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
                    apiKey = "gemini-key",
                    model = "gemini-2.5-pro",
                )
            viewModel.saveAiConfig(newConfig)
            advanceUntilIdle()

            val saved = fakeSettingsRepository.aiConfig.first()
            assertEquals(AiConfig.PROVIDER_GEMINI, saved.provider)
            assertEquals("gemini-key", saved.apiKey)
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
            assertEquals("WebView 深度解析", last.playableSources?.source)
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
                    apiKey = "test-key",
                    provider = "custom",
                )

            assertIs<AppResult.Success<List<String>>>(result)
            assertEquals(listOf("gpt-4o", "gpt-4o-mini"), result.data)
            assertEquals("https://api.openai.com/v1", agentService.lastFetchEndpoint)
            assertEquals("test-key", agentService.lastFetchApiKey)
            assertEquals("custom", agentService.lastFetchProvider)
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

            val savedMessages = fakeRepo.getMessages().first()
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

            val savedMessages = fakeRepo.getMessages().first()
            assertEquals(2, savedMessages.size)
            val actionId =
                savedMessages[1]
                    .pendingActions
                    .first()
                    .action.actionId
            assertEquals("act_persist_test", actionId)

            viewModel.approveAction(actionId)
            advanceUntilIdle()

            val updatedMessages = fakeRepo.getMessages().first()
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

            val savedMessages = fakeRepo.getMessages().first()
            val actionId =
                savedMessages[1]
                    .pendingActions
                    .first()
                    .action.actionId
            assertEquals("act_reject_persist_test", actionId)

            viewModel.rejectAction(actionId)
            advanceUntilIdle()

            val updatedMessages = fakeRepo.getMessages().first()
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
            assertEquals(2, fakeRepo.getMessages().first().size)

            viewModel.clearConversation()
            advanceUntilIdle()

            assertTrue(fakeRepo.getMessages().first().isEmpty(), "清空会话后 Repository 应为空")
            assertTrue(
                viewModel.uiState.value.messages
                    .isEmpty(),
                "清空会话后 UIState 应为空",
            )
        }
}
