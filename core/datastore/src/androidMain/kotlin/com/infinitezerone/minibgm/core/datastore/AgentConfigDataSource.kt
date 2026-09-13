package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Agent 云端模型配置（Base URL / API Key / 模型名） */
data class AgentModelConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * Agent 模型配置的加密存储，模式与 [AuthTokensDataSource] 一致：
 * 独立 DataStore 文件，内容为 Base64(IV || AES-GCM(JSON))，整体排除自云备份。
 * API key 属凭据，绝不写入 UserPreferences。
 */
class AgentConfigDataSource(
    private val dataStore: DataStore<String>,
    private val crypto: CryptoManager,
) {
    val config: Flow<AgentModelConfig?> =
        dataStore.data.map { blob -> decodeConfig(blob) }

    suspend fun current(): AgentModelConfig? = decodeConfig(dataStore.data.first())

    suspend fun save(config: AgentModelConfig) {
        dataStore.updateData { encodeConfig(config) }
    }

    suspend fun clear() {
        dataStore.updateData { "" }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeConfig(config: AgentModelConfig): String {
        val json = Json.encodeToString(PersistedConfig.serializer(), PersistedConfig(config.baseUrl, config.apiKey, config.model))
        return Base64.Default.encode(crypto.encrypt(json.encodeToByteArray()))
    }

    /** 密文损坏（如跨设备恢复后无法解密）时按"未配置"处理，不崩溃 */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeConfig(blob: String): AgentModelConfig? =
        if (blob.isBlank()) {
            null
        } else {
            runCatching {
                val plain = crypto.decrypt(Base64.Default.decode(blob.trim())).decodeToString()
                val persisted = Json.decodeFromString(PersistedConfig.serializer(), plain)
                AgentModelConfig(persisted.baseUrl, persisted.apiKey, persisted.model)
            }.getOrNull()
        }

    @Serializable
    private data class PersistedConfig(
        val baseUrl: String = "",
        val apiKey: String = "",
        val model: String = "",
    )
}
