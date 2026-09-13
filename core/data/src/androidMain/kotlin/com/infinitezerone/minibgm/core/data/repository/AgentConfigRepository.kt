package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.datastore.AgentConfigDataSource
import com.infinitezerone.minibgm.core.datastore.AgentModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Agent 云端模型配置（Base URL / API Key / 模型名）——feature 层可见的仓库模型 */
data class AgentConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** Agent 模型配置的单一来源：feature 层经由本仓库读写，不直接接触 DataStore */
interface AgentConfigRepository {
    val config: Flow<AgentConfig?>

    suspend fun current(): AgentConfig?

    suspend fun save(config: AgentConfig)

    suspend fun clear()
}

class AgentConfigRepositoryImpl(
    private val dataSource: AgentConfigDataSource,
) : AgentConfigRepository {
    override val config: Flow<AgentConfig?> =
        dataSource.config.map { it?.toAgentConfig() }

    override suspend fun current(): AgentConfig? = dataSource.current()?.toAgentConfig()

    override suspend fun save(config: AgentConfig) = dataSource.save(AgentModelConfig(config.baseUrl, config.apiKey, config.model))

    override suspend fun clear() = dataSource.clear()

    private fun AgentModelConfig.toAgentConfig() = AgentConfig(baseUrl, apiKey, model)
}
