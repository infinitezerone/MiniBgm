package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.data.util.UserDataCleaner
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.BgmPkce
import com.infinitezerone.minibgm.core.network.BgmTokenService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

interface AuthRepository {
    val activeUserId: Flow<Long?>
    val isLoggedIn: Flow<Boolean>
    val activeProfile: Flow<UserProfile?>
    val savedAccounts: Flow<List<UserProfile>>
    val isAuthenticating: StateFlow<Boolean>

    /** 生成并持久化一次性 verifier，返回授权 URL（state 为其指纹，交给 Custom Tabs 打开） */
    suspend fun beginLogin(): String

    /** 深链回调入口：校验 state → 经 Worker 兑换 token → 加密落盘 → 同步 Profile */
    suspend fun completeLogin(
        code: String?,
        state: String?,
    ): AppResult<Unit>

    /** 切换当前活跃账号 */
    suspend fun switchAccount(userId: Long)

    /** 退出当前活跃账号并清理其私有数据 */
    suspend fun logout()

    /** 退出指定账号并清理其私有数据 */
    suspend fun logout(userId: Long)

    /** 退出并注销所有账号，清理全局私有数据 */
    suspend fun logoutAll()

    /** 从远端拉取最新个人资料并落盘保存 */
    suspend fun refreshProfile(): AppResult<UserProfile>
}

class AuthRepositoryImpl(
    private val tokenService: BgmTokenService,
    private val tokenProvider: TokenProvider,
    private val userPreferences: UserPreferencesDataSource,
    private val authConfig: BgmAuthConfig,
    private val apiService: BangumiApiService? = null,
    private val userDataCleaner: UserDataCleaner? = null,
) : AuthRepository {
    private val _isAuthenticating = MutableStateFlow(false)
    override val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    override val activeUserId: Flow<Long?> =
        userPreferences.userPreferences.map { it.activeUserId.takeIf { id -> id != 0L } }

    override val activeProfile: Flow<UserProfile?> =
        userPreferences.userPreferences.map { it.activeProfile }

    override val savedAccounts: Flow<List<UserProfile>> =
        userPreferences.userPreferences.map { it.allProfiles }

    override suspend fun beginLogin(): String {
        // verifier 仅存本地；state 携带其 sha256 指纹，Worker 兑换时校验
        // sha256(verifier)==state（PKCE 等价，bgm.tv 不支持标准 PKCE，见 BgmPkce）。
        // Uuid.random() 底层为 SecureRandom，两次共 ≥256 位随机。
        val verifier = BgmPkce.generateVerifier()
        userPreferences.setPendingOAuthVerifier(verifier)
        return authConfig.buildAuthorizeUrl(state = BgmPkce.challenge(verifier))
    }

    override suspend fun completeLogin(
        code: String?,
        state: String?,
    ): AppResult<Unit> {
        _isAuthenticating.value = true
        return try {
            val verifier = userPreferences.userPreferences.first().pendingOAuthVerifier
            when {
                code.isNullOrBlank() || state.isNullOrBlank() ->
                    return AppResult.Error(IllegalArgumentException("回调缺少 code 或 state"))
                verifier.isBlank() || BgmPkce.challenge(verifier) != state ->
                    return AppResult.Error(IllegalStateException("state 校验失败，疑似伪造回调"))
            }
            val tokens = tokenService.exchangeCode(code, state, verifier)
            tokenProvider.saveTokens(tokens.userId, tokens.accessToken, tokens.refreshToken)
            tokenProvider.setActiveUser(tokens.userId)
            userPreferences.markLoggedIn(tokens.userId)
            userPreferences.setPendingOAuthVerifier("")

            // 异步拉取个人资料并落盘（拉取失败不阻断登录完成）
            val api = apiService
            if (api != null) {
                runCatching { api.getMe() }
                    .getOrNull()
                    ?.let { profile -> userPreferences.saveUserProfile(profile) }
            }

            AppResult.Success(Unit)
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "授权码兑换失败：${e.message}")
        } catch (e: SerializationException) {
            AppResult.Error(e, "兑换响应解析失败：${e.message}")
        } finally {
            _isAuthenticating.value = false
        }
    }

    override suspend fun switchAccount(userId: Long) {
        tokenProvider.setActiveUser(userId)
        userPreferences.switchAccount(userId)
    }

    override suspend fun logout() {
        val currentUserId = userPreferences.userPreferences.first().activeUserId
        if (currentUserId != 0L) {
            logout(currentUserId)
        } else {
            logoutAll()
        }
    }

    override suspend fun logout(userId: Long) {
        tokenProvider.removeTokens(userId)
        userDataCleaner?.clear(userId)
    }

    override suspend fun logoutAll() {
        tokenProvider.clearTokens()
        userDataCleaner?.clearAll()
    }

    // 登录态要求"偏好已标记"且"token 实际存在"：云备份/设备迁移会把
    // user_preferences.pb 恢复到新设备，而 auth_tokens.pb 被排除在备份之外，
    // 仅看偏好会呈现"已登录但无凭据"的假登录态（API 全 401 且无法刷新）。
    override val isLoggedIn: Flow<Boolean> =
        combine(
            userPreferences.userPreferences,
            tokenProvider.hasTokens,
        ) { prefs, hasTokens ->
            prefs.isLoggedIn && hasTokens
        }

    override suspend fun refreshProfile(): AppResult<UserProfile> {
        val api = apiService ?: return AppResult.Error(IllegalStateException("API 服务未配置"))
        return try {
            val profile = api.getMe()
            userPreferences.saveUserProfile(profile)
            AppResult.Success(profile)
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "拉取个人资料失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "个人资料同步异常：${e.message}")
        }
    }
}
