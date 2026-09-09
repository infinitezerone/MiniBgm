package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.util.UserDataCleaner
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.BgmPkce
import com.infinitezerone.minibgm.core.network.BgmTokenService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

interface AuthRepository {
    val activeUserId: Flow<Long?>

    /** 登录态由凭据库直接派生：凭据在即已登录，结构上不存在假登录态 */
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
    private val apiService: BangumiApiService,
    private val userDataCleaner: UserDataCleaner,
) : AuthRepository {
    private val log = bgmLogger("Bgm/Auth")
    private val _isAuthenticating = MutableStateFlow(false)
    override val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    // 会话事实唯一存放在凭据库（AuthTokensDataSource）：activeUserId 只在有可用
    // token 时非空，结构上不存在「标记已登录但凭据缺失」的假登录态——
    // 云备份可恢复偏好文件，但不会凭空恢复出会话
    override val activeUserId: Flow<Long?> = tokenProvider.activeUserId

    override val isLoggedIn: Flow<Boolean> =
        tokenProvider.activeUserId
            .map { it != null }

    override val activeProfile: Flow<UserProfile?> =
        combine(
            tokenProvider.activeUserId,
            userPreferences.userPreferences,
        ) { userId, prefs -> userId?.let { prefs.savedProfiles[it] } }

    override val savedAccounts: Flow<List<UserProfile>> =
        userPreferences.userPreferences.map { it.allProfiles }

    override suspend fun beginLogin(): String {
        // verifier 仅存本地；state 携带其 sha256 指纹，Worker 兑换时校验
        // sha256(verifier)==state（PKCE 等价，bgm.tv 不支持标准 PKCE，见 BgmPkce）。
        // Uuid.random() 底层为 SecureRandom，两次共 ≥256 位随机。
        log.i { "[LOGIN:BEGIN] generating oauth authorization request" }
        val verifier = BgmPkce.generateVerifier()
        userPreferences.setPendingOAuthVerifier(verifier)
        return authConfig.buildAuthorizeUrl(state = BgmPkce.challenge(verifier))
    }

    override suspend fun completeLogin(
        code: String?,
        state: String?,
    ): AppResult<Unit> {
        _isAuthenticating.value = true
        log.d { "[LOGIN:COMPLETE:START]" }
        return try {
            val verifier = userPreferences.userPreferences.first().pendingOAuthVerifier
            when {
                code.isNullOrBlank() || state.isNullOrBlank() -> {
                    log.w { "[LOGIN:COMPLETE:REJECTED] missing code or state" }
                    return AppResult.Error(IllegalArgumentException("回调缺少 code 或 state"))
                }
                verifier.isBlank() || BgmPkce.challenge(verifier) != state -> {
                    log.w { "[LOGIN:COMPLETE:REJECTED] state or verifier mismatch" }
                    return AppResult.Error(IllegalStateException("state 校验失败，疑似伪造回调"))
                }
            }
            val tokens = tokenService.exchangeCode(code, state, verifier)
            // saveTokens 同时把该用户置为凭据库的活跃账号，登录态随之成立
            tokenProvider.saveTokens(tokens.userId, tokens.accessToken, tokens.refreshToken)
            userPreferences.setPendingOAuthVerifier("")

            // 异步拉取个人资料并落盘（拉取失败不阻断登录完成）
            runCatching { apiService.getMe() }
                .getOrNull()
                ?.let { profile -> userPreferences.saveUserProfile(profile) }

            log.i { "[LOGIN:COMPLETE:SUCCESS] userId=${tokens.userId}" }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            log.e(e) { "[LOGIN:COMPLETE:FAILED] network exception: ${e.message}" }
            AppResult.Error(e, "授权码兑换失败：${e.message}")
        } catch (e: SerializationException) {
            log.e(e) { "[LOGIN:COMPLETE:FAILED] serialization exception: ${e.message}" }
            AppResult.Error(e, "兑换响应解析失败：${e.message}")
        } finally {
            _isAuthenticating.value = false
        }
    }

    override suspend fun switchAccount(userId: Long) {
        log.i { "[AUTH:SWITCH_ACCOUNT] userId=$userId" }
        tokenProvider.setActiveUser(userId)
    }

    override suspend fun logout() {
        withContext(NonCancellable) {
            val currentUserId = tokenProvider.activeUserId.first()
            if (currentUserId != null) {
                logout(currentUserId)
            } else {
                logoutAll()
            }
        }
    }

    override suspend fun logout(userId: Long) {
        withContext(NonCancellable) {
            log.i { "[AUTH:LOGOUT] userId=$userId" }
            tokenProvider.removeTokens(userId)
            userDataCleaner.clear(userId)
        }
    }

    override suspend fun logoutAll() {
        withContext(NonCancellable) {
            log.i { "[AUTH:LOGOUT_ALL]" }
            tokenProvider.clearTokens()
            userDataCleaner.clearAll()
        }
    }

    override suspend fun refreshProfile(): AppResult<UserProfile> =
        try {
            val profile = apiService.getMe()
            userPreferences.saveUserProfile(profile)
            log.d { "[PROFILE:REFRESH:SUCCESS] userId=${profile.id}" }
            AppResult.Success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            log.e(e) { "[PROFILE:REFRESH:FAILED] ${e.message}" }
            AppResult.Error(e, "拉取个人资料失败：${e.message}")
        } catch (e: Exception) {
            log.e(e) { "[PROFILE:REFRESH:ERROR] ${e.message}" }
            AppResult.Error(e, "个人资料同步异常：${e.message}")
        }
}
