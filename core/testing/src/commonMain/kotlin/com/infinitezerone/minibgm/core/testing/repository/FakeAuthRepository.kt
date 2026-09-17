package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialLoggedIn: Boolean = false,
    initialProfile: UserProfile? = null,
    initialAccounts: List<UserProfile> = emptyList(),
) : AuthRepository {
    private val _isLoggedIn = MutableStateFlow(initialLoggedIn)
    override val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()

    private val _activeUserId = MutableStateFlow(initialProfile?.id?.takeIf { it != 0L })
    override val activeUserId: Flow<Long?> = _activeUserId.asStateFlow()

    private val _activeProfile = MutableStateFlow(initialProfile)
    override val activeProfile: Flow<UserProfile?> = _activeProfile.asStateFlow()

    private val _savedAccounts = MutableStateFlow(initialAccounts)
    override val savedAccounts: Flow<List<UserProfile>> = _savedAccounts.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    override val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    var beginLoginCallCount: Int = 0
        private set
    var completeLoginCallCount: Int = 0
        private set
    var logoutCallCount: Int = 0
        private set
    var logoutAllCallCount: Int = 0
        private set
    var switchAccountCallCount: Int = 0
        private set

    var mockAuthorizeUrl: String = "https://bgm.tv/oauth/authorize?client_id=test&state=test_state"
    var completeLoginResult: AppResult<Unit> = AppResult.Success(Unit)

    fun setLoggedIn(loggedIn: Boolean) {
        _isLoggedIn.value = loggedIn
    }

    fun setActiveProfile(profile: UserProfile?) {
        _activeProfile.value = profile
        _activeUserId.value = profile?.id?.takeIf { it != 0L }
    }

    fun setSavedAccounts(accounts: List<UserProfile>) {
        _savedAccounts.value = accounts
    }

    fun setAuthenticating(authenticating: Boolean) {
        _isAuthenticating.value = authenticating
    }

    override suspend fun beginLogin(): String {
        beginLoginCallCount++
        return mockAuthorizeUrl
    }

    override suspend fun completeLogin(
        code: String?,
        state: String?,
    ): AppResult<Unit> {
        completeLoginCallCount++
        _isAuthenticating.value = true
        return try {
            if (completeLoginResult is AppResult.Success) {
                _isLoggedIn.value = true
            }
            completeLoginResult
        } finally {
            _isAuthenticating.value = false
        }
    }

    override suspend fun switchAccount(userId: Long) {
        switchAccountCallCount++
        _activeUserId.value = userId
        _activeProfile.value = _savedAccounts.value.firstOrNull { it.id == userId }
    }

    override suspend fun logout() {
        logoutCallCount++
        val current = _activeUserId.value
        if (current != null) {
            val remaining = _savedAccounts.value.filterNot { it.id == current }
            _savedAccounts.value = remaining
            if (remaining.isEmpty()) {
                _isLoggedIn.value = false
                _activeUserId.value = null
                _activeProfile.value = null
            } else {
                val next = remaining.first()
                _activeUserId.value = next.id
                _activeProfile.value = next
            }
        } else {
            _savedAccounts.value = emptyList()
            _isLoggedIn.value = false
            _activeUserId.value = null
            _activeProfile.value = null
        }
    }

    override suspend fun logout(userId: Long) {
        logoutCallCount++
        val remaining = _savedAccounts.value.filterNot { it.id == userId }
        _savedAccounts.value = remaining
        if (userId == _activeUserId.value) {
            if (remaining.isEmpty()) {
                _isLoggedIn.value = false
                _activeUserId.value = null
                _activeProfile.value = null
            } else {
                val next = remaining.first()
                _activeUserId.value = next.id
                _activeProfile.value = next
            }
        }
    }

    override suspend fun logoutAll() {
        logoutAllCallCount++
        _savedAccounts.value = emptyList()
        _isLoggedIn.value = false
        _activeUserId.value = null
        _activeProfile.value = null
    }

    var refreshProfileCallCount: Int = 0
        private set
    var refreshProfileResult: AppResult<UserProfile>? = null

    override suspend fun refreshProfile(): AppResult<UserProfile> {
        refreshProfileCallCount++
        return refreshProfileResult
            ?: _activeProfile.value?.let { AppResult.Success(it) }
            ?: AppResult.Error(IllegalStateException("No active profile"))
    }
}
