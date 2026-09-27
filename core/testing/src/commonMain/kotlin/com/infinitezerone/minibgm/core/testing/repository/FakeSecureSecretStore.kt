package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.SecureSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [SecureSecretStore] 的内存测试替身（不做加密，只保证读写语义一致）。 */
class FakeSecureSecretStore : SecureSecretStore {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun observeSecrets(): Flow<Map<String, String>> = state.map { it }

    override suspend fun getSecret(key: String): String? = state.value[key]

    override suspend fun setSecret(
        key: String,
        value: String,
    ) {
        state.value = if (value.isBlank()) state.value - key else state.value + (key to value)
    }

    override suspend fun removeSecret(key: String) {
        state.value = state.value - key
    }
}
