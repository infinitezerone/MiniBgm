package com.infinitezerone.minibgm.core.testing.util

import com.infinitezerone.minibgm.core.common.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTokenProvider(
    initialUserId: Long? = null,
    private var accessToken: String? = "fake_access_token",
    private var refreshToken: String? = "fake_refresh_token",
) : TokenProvider {
    val uidFlow = MutableStateFlow<Long?>(initialUserId)
    override val activeUserId: Flow<Long?> = uidFlow
    override val hasTokens: Flow<Boolean> = uidFlow.map { it != null }

    override suspend fun getAccessToken(): String? = accessToken

    override suspend fun getRefreshToken(): String? = refreshToken

    override suspend fun saveTokens(
        userId: Long,
        accessToken: String,
        refreshToken: String,
    ) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        uidFlow.value = userId
    }

    override suspend fun setActiveUser(userId: Long) {
        uidFlow.value = userId
    }

    override suspend fun removeTokens(userId: Long) {
        if (uidFlow.value == userId) {
            uidFlow.value = null
        }
    }

    override suspend fun clearTokens() {
        accessToken = null
        refreshToken = null
        uidFlow.value = null
    }
}
