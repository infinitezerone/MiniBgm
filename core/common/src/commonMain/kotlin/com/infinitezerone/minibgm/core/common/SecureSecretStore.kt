package com.infinitezerone.minibgm.core.common

import kotlinx.coroutines.flow.Flow

/**
 * 通用加密小秘密存储（AndroidKeyStore 加密落盘，随凭据库一起排除云备份）。
 *
 * OAuth token 有独立强类型入口 [TokenProvider]；其他凭据（如 AI 服务密钥）走这里。
 * 严禁把任何密钥写进明文偏好——`ArchitectureRulesTest.userPreferences_never_stores_sensitive_tokens`
 * 会把凭据形状的字段名当红线拦下。
 */
interface SecureSecretStore {
    /** 当前全部秘密（响应式），用于与明文配置组合出完整配置对象。 */
    fun observeSecrets(): Flow<Map<String, String>>

    suspend fun getSecret(key: String): String?

    /** 写入或覆盖；[value] 为空串等价于删除。 */
    suspend fun setSecret(
        key: String,
        value: String,
    )

    suspend fun removeSecret(key: String)
}
