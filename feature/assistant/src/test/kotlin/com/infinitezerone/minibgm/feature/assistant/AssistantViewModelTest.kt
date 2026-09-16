package com.infinitezerone.minibgm.feature.assistant

import com.infinitezerone.minibgm.core.ai.BgmAiAgentService
import com.infinitezerone.minibgm.core.ai.PendingActionExecutor
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
        var prompts = mutableListOf<String>()

        override suspend fun execute(prompt: String): AppResult<String> {
            prompts.add(prompt)
            return executeResult
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
}
