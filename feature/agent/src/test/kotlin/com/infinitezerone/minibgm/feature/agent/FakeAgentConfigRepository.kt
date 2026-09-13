package com.infinitezerone.minibgm.feature.agent

import com.infinitezerone.minibgm.core.data.repository.AgentConfig
import com.infinitezerone.minibgm.core.data.repository.AgentConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 测试用内存实现：模拟加密配置存储的读写 */
class FakeAgentConfigRepository(
    initial: AgentConfig? = null,
) : AgentConfigRepository {
    private val state = MutableStateFlow(initial)

    override val config: Flow<AgentConfig?> = state

    override suspend fun current(): AgentConfig? = state.value

    override suspend fun save(config: AgentConfig) {
        state.value = config
    }

    override suspend fun clear() {
        state.value = null
    }
}
