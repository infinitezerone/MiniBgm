package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.util.UserDataCleaner
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.InAppWebSession
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.BgmPkce
import com.infinitezerone.minibgm.core.network.BgmTokenService
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/** 应用内网页浏览允许的 bgm 系域名（含子域）。 */
private val BGM_WEB_HOSTS = listOf("bgm.tv", "bangumi.tv", "chii.in")

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

    /**
     * 开始应用内内嵌登录：启动本地环回代理（走原生 ECH 通道），
     * 返回内置 WebView 应加载的会话（含必须先行写入的会话 Cookie）。
     * 代理不可用时返回 null，调用方应回退到系统浏览器。
     */
    suspend fun beginInAppLogin(): InAppWebSession?

    /**
     * 开始应用内浏览：把 bgm 系域名页面同样交给内置 WebView（复用 ECH 通道）。
     *
     * @return 非 bgm 域名或代理不可用时返回 null
     */
    suspend fun beginInAppBrowse(url: String): InAppWebSession?

    /**
     * 结束应用内网页会话（登录完成/取消、浏览页退出时调用）。
     *
     * 非 suspend 且幂等：调用方可能已被取消（页面销毁、ViewModel 清理），
     * 这里不能再依赖协程作用域。
     */
    fun stopInAppWeb()

    /**
     * 个人访问令牌（Personal Access Token）登录入口：
     * 校验 token 有效性 → 获取用户资料 → 加密落盘 → 激活会话
     */
    suspend fun loginWithPersonalAccessToken(token: String): AppResult<Unit>

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
    private val oAuthProxyService: com.infinitezerone.minibgm.core.network.oauth.OAuthProxyService? = null,
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
            .distinctUntilChanged()

    override val activeProfile: Flow<UserProfile?> =
        combine(
            tokenProvider.activeUserId,
            userPreferences.userPreferences,
        ) { userId, prefs -> userId?.let { prefs.savedProfiles[it] } }
            .distinctUntilChanged()

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

    override suspend fun beginInAppLogin(): InAppWebSession? {
        log.i { "[LOGIN:IN_APP:BEGIN] starting in-app oauth proxy" }
        val verifier = BgmPkce.generateVerifier()
        userPreferences.setPendingOAuthVerifier(verifier)
        val state = BgmPkce.challenge(verifier)
        // 授权地址先生成上游形态，再由代理映射为环回地址：host 与 path 的解析只留在代理侧
        return startInAppWebSession(authConfig.buildAuthorizeUrl(state = state))
    }

    override suspend fun beginInAppBrowse(url: String): InAppWebSession? {
        log.i { "[IN_APP_WEB:BEGIN] starting in-app browse session" }
        return startInAppWebSession(url)
    }

    override fun stopInAppWeb() {
        log.i { "[IN_APP_WEB:STOP] stopping loopback proxy" }
        oAuthProxyService?.stop()
    }

    /**
     * 启动环回代理并把上游地址映射为 WebView 可加载的会话。
     *
     * 失败一律收摊（停代理 + 返回 null），避免留下一个「启动了但没人用」的监听端口。
     */
    private fun startInAppWebSession(upstreamUrl: String): InAppWebSession? {
        val proxy = oAuthProxyService ?: return null
        val host = hostOf(upstreamUrl) ?: return null
        if (!isBgmWebHost(host)) return null
        return try {
            proxy.start(host)
            val cookie = proxy.sessionCookie()
            val loopback = proxy.toLoopbackUrl(upstreamUrl)
            if (cookie == null || loopback == null) {
                proxy.stop()
                null
            } else {
                InAppWebSession(
                    url = loopback,
                    cookieName = cookie.first,
                    cookieValue = cookie.second,
                )
            }
        } catch (e: Exception) {
            log.w { "[IN_APP_WEB:FAILED] proxy unavailable: ${e.message}" }
            proxy.stop()
            null
        }
    }

    /** 取 URL 的 host（commonMain 无 java.net，按 scheme 分隔符手取即可）。 */
    private fun hostOf(url: String): String? =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .takeIf { it.isNotBlank() }

    /**
     * bgm 系域名白名单：应用内浏览路由只服务这些域，
     * 避免任意第三方站点被接进本机环回代理（也避免误把外部站点当 bgm 页面处理）。
     */
    private fun isBgmWebHost(host: String): Boolean =
        BGM_WEB_HOSTS.any { base -> host.equals(base, ignoreCase = true) || host.endsWith(".$base", ignoreCase = true) }

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
            AppResult.Error(e, e.toUserFriendlyMessage("授权码兑换"))
        } catch (e: SerializationException) {
            // 别拼 e.message：SerializationException 的原文是英文（如 "Unexpected JSON token at offset 12"），
            // 会原样显示给用户。统一走 toUserFriendlyMessage 归一化。
            log.e(e) { "[LOGIN:COMPLETE:FAILED] serialization exception: ${e.message}" }
            AppResult.Error(e, e.toUserFriendlyMessage("兑换响应解析"))
        } finally {
            _isAuthenticating.value = false
            oAuthProxyService?.stop()
        }
    }

    override suspend fun loginWithPersonalAccessToken(token: String): AppResult<Unit> {
        _isAuthenticating.value = true
        log.i { "[LOGIN:PAT:START] verifying personal access token" }
        return try {
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                log.w { "[LOGIN:PAT:REJECTED] token is empty" }
                return AppResult.Error(IllegalArgumentException("访问令牌不能为空"))
            }
            val profile = tokenService.verifyToken(trimmed)
            // saveTokens 同时把该用户置为凭据库的活跃账号，登录态随之成立
            tokenProvider.saveTokens(profile.id, trimmed, "")
            userPreferences.saveUserProfile(profile)
            log.i { "[LOGIN:PAT:SUCCESS] userId=${profile.id}, nickname=${profile.nickname}" }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            log.e(e) { "[LOGIN:PAT:FAILED] network exception: ${e.message}" }
            AppResult.Error(e, e.toUserFriendlyMessage("令牌验证"))
        } catch (e: Exception) {
            log.e(e) { "[LOGIN:PAT:FAILED] unexpected exception: ${e.message}" }
            AppResult.Error(e, "令牌验证失败: ${e.message ?: "未知错误"}")
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
            AppResult.Error(e, e.toUserFriendlyMessage("拉取个人资料"))
        } catch (e: Exception) {
            // 兜底分支拿到的是任意异常，原文可能是英文/类名，同样不能直接拼给用户
            log.e(e) { "[PROFILE:REFRESH:ERROR] ${e.message}" }
            AppResult.Error(e, e.toUserFriendlyMessage("个人资料同步"))
        }
}
