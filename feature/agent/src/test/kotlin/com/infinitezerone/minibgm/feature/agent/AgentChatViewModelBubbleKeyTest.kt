package com.infinitezerone.minibgm.feature.agent

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.miniagent.agentloop.ChatMessage
import com.miniagent.agentloop.CompletionRequest
import com.miniagent.agentloop.CompletionResponse
import com.miniagent.agentloop.FinishReason
import com.miniagent.agentloop.LlmProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 回归：v0.2.10 真机二次崩溃 —— 气泡 key 用 hashCode()，重复发送相同内容时
 * LazyColumn 出现重复 key 直接崩溃。气泡 id 必须唯一。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentChatViewModelBubbleKeyTest {
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

    /** 立即给出相同答复的 Provider（制造重复内容的气泡） */
    private class SameAnswerProvider : LlmProvider {
        override suspend fun complete(request: CompletionRequest) =
            CompletionResponse(ChatMessage.assistant("same answer"), finishReason = FinishReason.STOP)
    }

    @Test
    fun repeatedIdenticalMessages_produceUniqueBubbleIds() =
        runTest {
            val viewModel =
                AgentChatViewModel(
                    toolsFactory = MiniBgmAgentTools(StubScheduleRepository(), FakeSearchRepository(), FakeCollectionRepository()),
                    providerFactory = { SameAnswerProvider() },
                )
            viewModel.updateConfig("https://x/v1", "k", "m")

            repeat(3) {
                viewModel.send("same message")
                advanceUntilIdle()
            }

            val bubbles = viewModel.uiState.value.bubbles
            assertEquals(6, bubbles.size) // 3 user + 3 agent
            val ids = bubbles.map { it.id }
            assertEquals("气泡 id 必须唯一（重复内容不得碰撞）", ids.size, ids.toSet().size)
            assertTrue("气泡 key 不得再用 hashCode", bubbles.all { it.text == "same message" || it.text == "same answer" })
        }
}
