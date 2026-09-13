package com.infinitezerone.minibgm.feature.agent

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AgentConfig
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 回归：v0.2.12 前后 —— 模型配置仅驻留内存，退出 Agent 屏幕即清空，
 * 重进后"先填写模型配置"且 send 被静默吞掉。配置必须持久化并在进入时恢复。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentChatViewModelConfigPersistenceTest {
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

    private fun createViewModel(configRepository: FakeAgentConfigRepository): AgentChatViewModel =
        AgentChatViewModel(
            toolsFactory = MiniBgmAgentTools(StubScheduleRepository(), FakeSearchRepository(), FakeCollectionRepository()),
            configRepository = configRepository,
            executorFactory = AgentExecutorFactory { error("unused") },
        )

    @Test
    fun updateConfig_isPersistedToRepository() =
        runTest {
            val store = FakeAgentConfigRepository()
            val viewModel = createViewModel(store)

            viewModel.updateConfig("https://api.example.com/v1", "sk-test", "test-model")
            advanceUntilIdle()

            assertEquals(AgentConfig("https://api.example.com/v1", "sk-test", "test-model"), store.current())
        }

    @Test
    fun newViewModel_restoresPersistedConfig() =
        runTest {
            val store =
                FakeAgentConfigRepository(
                    AgentConfig(baseUrl = "https://api.example.com/v1", apiKey = "sk-test", model = "test-model"),
                )

            // 模拟"退出后重进"：新建 ViewModel，配置应从存储恢复而非回到默认
            val viewModel = createViewModel(store)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("https://api.example.com/v1", state.baseUrl)
            assertEquals("sk-test", state.apiKey)
            assertEquals("test-model", state.model)
            assertEquals(true, state.isConfigured)
        }

    @Test
    fun noPersistedConfig_fallsBackToDefaults() =
        runTest {
            val viewModel = createViewModel(FakeAgentConfigRepository())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.apiKey)
            assertEquals(false, state.isConfigured)
        }
}
