package com.infinitezerone.minibgm.core.datastore

import com.infinitezerone.minibgm.core.common.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** TokenProvider 的落盘实现：经 AndroidKeyStore AES-GCM 加密的独立 DataStore */
class KeystoreTokenProvider(
    private val authTokens: AuthTokensDataSource,
) : TokenProvider {
    override suspend fun getAccessToken(): String? = authTokens.getAccessToken()

    override suspend fun getRefreshToken(): String? = authTokens.getRefreshToken()

    override val hasTokens: Flow<Boolean> = authTokens.tokens.map { it != null }

    override val activeUserId: Flow<Long?> = authTokens.activeUserId

    override suspend fun saveTokens(
        userId: Long,
        accessToken: String,
        refreshToken: String,
    ) {
        authTokens.saveTokens(userId, accessToken, refreshToken)
    }

    override suspend fun setActiveUser(userId: Long) {
        authTokens.setActiveUser(userId)
    }

    override suspend fun removeTokens(userId: Long) {
        authTokens.removeTokens(userId)
    }

    override suspend fun clearTokens() {
        authTokens.clear()
    }
}
