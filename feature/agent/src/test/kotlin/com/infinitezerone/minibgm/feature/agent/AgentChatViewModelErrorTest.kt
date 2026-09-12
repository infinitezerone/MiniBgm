package com.infinitezerone.minibgm.feature.agent

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.miniagent.agentloop.CompletionRequest
import com.miniagent.agentloop.CompletionResponse
import com.miniagent.agentloop.LlmProvider
import com.miniagent.provider.cloud.CloudProviderException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 回归：v0.2.9 崩溃 —— 模型服务返回非 HTTP 报文（HTML）时，引擎异常穿透
 * send() 导致未捕获崩溃。修复后 Provider 异常必须转成错误气泡且 isThinking 复位。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentChatViewModelErrorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class StubScheduleRepository : ScheduleRepository {
        override fun getSchedulesByWeekday(weekday: Int) = error("unused")

        override fun getAllSchedulesStream() = error("unused")

        override suspend fun getUpcomingAiringForSubjects(
            subjectIds: List<Long>,
            hoursAhead: Long,
            lookbackHours: Long,
        ) = emptyList<com.infinitezerone.minibgm.core.model.UpcomingAiring>()

        override suspend fun refreshSchedules() = AppResult.Success(Unit)

        override suspend fun syncBangumiData(force: Boolean) = AppResult.Success(Unit)

        override suspend fun getScheduleDefaultOnlyWatching(): Boolean = false

        override suspend fun setScheduleDefaultOnlyWatching(onlyWatching: Boolean) = Unit
    }

    private fun createViewModel(provider: LlmProvider): AgentChatViewModel =
        AgentChatViewModel(
            toolsFactory = MiniBgmAgentTools(StubScheduleRepository(), FakeSearchRepository(), FakeCollectionRepository()),
            providerFactory = { provider },
        )

    @Test
    fun send_modelFailure_becomesErrorBubbleInsteadOfCrash() =
        runTest {
            val provider =
                object : LlmProvider {
                    override suspend fun complete(request: CompletionRequest): CompletionResponse =
                        throw CloudProviderException(
                            statusCode = null,
                            message = "无法连接或解析模型服务响应：Unsupported HTTP version: <html>",
                        )
                }
            val viewModel = createViewModel(provider)
            viewModel.updateConfig(baseUrl = "https://api.example.com/v1", apiKey = "k", model = "m")

            viewModel.send("今天有什么更新")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse("isThinking 必须复位", state.isThinking)
            assertTrue(
                state.bubbles
                    .last()
                    .text
                    .contains("调用模型失败"),
            )
            assertTrue(
                state.bubbles
                    .last()
                    .text
                    .contains("Unsupported HTTP version"),
            )
            assertEquals(AgentBubbleRole.AGENT, state.bubbles.last().role)
        }

    @Test
    fun send_unknownError_becomesErrorBubble() =
        runTest {
            val provider =
                object : LlmProvider {
                    override suspend fun complete(request: CompletionRequest): CompletionResponse = throw IllegalStateException("weird")
                }
            val viewModel = createViewModel(provider)
            viewModel.updateConfig("https://x/v1", "k", "m")

            viewModel.send("hi")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isThinking)
            assertTrue(
                state.bubbles
                    .last()
                    .text
                    .contains("出错了"),
            )
            assertEquals(AgentBubbleRole.AGENT, state.bubbles.last().role)
        }

    @Test
    fun send_withoutConfig_isBlocked() =
        runTest {
            val viewModel =
                AgentChatViewModel(
                    toolsFactory = MiniBgmAgentTools(StubScheduleRepository(), FakeSearchRepository(), FakeCollectionRepository()),
                    providerFactory = { error("must not be called") },
                )

            viewModel.send("hi")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.bubbles
                    .isEmpty(),
            )
            assertFalse(viewModel.uiState.value.isThinking)
        }
}
