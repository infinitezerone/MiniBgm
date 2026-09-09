package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 密文 blob 的持久化格式：文件内容是 Base64 字符串，加解密与校验都在 [AuthTokensDataSource] 内完成 */
internal object AuthBlobSerializer : Serializer<String> {
    override val defaultValue: String = ""

    override suspend fun readFrom(input: InputStream): String = input.readBytes().decodeToString()

    override suspend fun writeTo(
        t: String,
        output: OutputStream,
    ) {
        withContext(Dispatchers.IO) {
            output.write(t.encodeToByteArray())
        }
    }
}

/**
 * OAuth token 的加密存储：独立于普通偏好的 DataStore 文件，
 * 内容为 Base64(IV || AES-GCM(JSON))，并在备份规则中整体排除。
 */
class AuthTokensDataSource(
    private val dataStore: DataStore<String>,
    private val crypto: CryptoManager,
) {
    val tokens: Flow<Pair<String, String>?> =
        dataStore.data.map { blob ->
            val state = decodeState(blob) ?: return@map null
            val activeTokens =
                state.accounts[state.activeUserId]
                    ?: state.accounts.values.firstOrNull()
            activeTokens?.let { it.accessToken to it.refreshToken }
        }

    val activeUserId: Flow<Long?> =
        dataStore.data.map { blob ->
            decodeState(blob)?.activeUserId?.takeIf { it != 0L }
        }

    suspend fun getAccessToken(): String? = currentActiveTokens()?.accessToken

    suspend fun getRefreshToken(): String? = currentActiveTokens()?.refreshToken

    suspend fun saveTokens(
        userId: Long,
        accessToken: String,
        refreshToken: String,
    ) {
        val current = currentState()
        val accountTokens = AccountTokens(accessToken = accessToken, refreshToken = refreshToken, userId = userId)
        val updatedAccounts = current.accounts + (userId to accountTokens)
        val newState = AuthTokensState(activeUserId = userId, accounts = updatedAccounts)
        saveState(newState)
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        val current = currentState()
        val targetUserId =
            current.activeUserId.takeIf { it != 0L }
                ?: current.accounts.keys.firstOrNull()
                ?: 0L
        saveTokens(targetUserId, accessToken, refreshToken)
    }

    suspend fun setActiveUser(userId: Long) {
        val current = currentState()
        if (current.accounts.containsKey(userId)) {
            saveState(current.copy(activeUserId = userId))
        }
    }

    suspend fun removeTokens(userId: Long) {
        val current = currentState()
        val updatedAccounts = current.accounts - userId
        val newActiveId =
            if (current.activeUserId == userId) {
                updatedAccounts.keys.firstOrNull() ?: 0L
            } else {
                current.activeUserId
            }
        if (updatedAccounts.isEmpty()) {
            clear()
        } else {
            saveState(AuthTokensState(activeUserId = newActiveId, accounts = updatedAccounts))
        }
    }

    suspend fun clear() {
        dataStore.updateData { "" }
    }

    private suspend fun currentState(): AuthTokensState = decodeState(dataStore.data.first()) ?: AuthTokensState()

    private suspend fun currentActiveTokens(): AccountTokens? {
        val state = currentState()
        return state.accounts[state.activeUserId] ?: state.accounts.values.firstOrNull()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun saveState(state: AuthTokensState) {
        val json = Json.encodeToString(AuthTokensState.serializer(), state)
        val blob = Base64.Default.encode(crypto.encrypt(json.encodeToByteArray()))
        dataStore.updateData { blob }
    }

    /** 密文损坏（如跨设备恢复后无法解密）时按"未登录"处理，避免崩溃循环 */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeState(blob: String): AuthTokensState? =
        if (blob.isBlank()) {
            null
        } else {
            runCatching {
                val plain = crypto.decrypt(Base64.Default.decode(blob.trim())).decodeToString()
                val state = Json.decodeFromString(AuthTokensState.serializer(), plain)
                if (state.accounts.isEmpty() && state.accessToken.isNotBlank()) {
                    val legacy = AccountTokens(state.accessToken, state.refreshToken, state.activeUserId)
                    AuthTokensState(activeUserId = state.activeUserId, accounts = mapOf(state.activeUserId to legacy))
                } else {
                    state
                }
            }.getOrNull()
        }

    @Serializable
    private data class AccountTokens(
        val accessToken: String = "",
        val refreshToken: String = "",
        val userId: Long = 0L,
    )

    @Serializable
    private data class AuthTokensState(
        val activeUserId: Long = 0L,
        val accounts: Map<Long, AccountTokens> = emptyMap(),
        val accessToken: String = "",
        val refreshToken: String = "",
    )
}
