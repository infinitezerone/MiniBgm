package com.infinitezerone.minibgm.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.data.util.UserDataCleaner
import com.infinitezerone.minibgm.core.datastore.UserPreferences
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmPkce
import com.infinitezerone.minibgm.core.network.BgmTokenService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryImplTest {
    private class FakeTokenProvider : TokenProvider {
        var accessToken: String? = null
            private set
        var refreshToken: String? = null
            private set
        var clearCount = 0
            private set

        private val hasTokensState = MutableStateFlow(false)
        override val hasTokens: Flow<Boolean> = hasTokensState

        private val activeUserIdState = MutableStateFlow<Long?>(null)
        override val activeUserId: Flow<Long?> = activeUserIdState

        override suspend fun getAccessToken(): String? = accessToken

        override suspend fun getRefreshToken(): String? = refreshToken

        override suspend fun saveTokens(
            userId: Long,
            accessToken: String,
            refreshToken: String,
        ) {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            this.activeUserIdState.value = userId
            hasTokensState.value = true
        }

        override suspend fun setActiveUser(userId: Long) {
            this.activeUserIdState.value = userId
        }

        override suspend fun removeTokens(userId: Long) {
            clearTokens()
        }

        override suspend fun clearTokens() {
            accessToken = null
            refreshToken = null
            activeUserIdState.value = null
            hasTokensState.value = false
            clearCount++
        }
    }

    /** 用 MockEngine 驱动真实的 BgmTokenService，记录 Worker 被调用的次数、目标与表单 */
    private class FakeTokenApi(
        private val status: HttpStatusCode,
        private val body: String,
    ) {
        var requestCount = 0
            private set
        var lastMethod: HttpMethod? = null
            private set
        var lastUrl: String? = null
            private set
        var lastForm: Parameters? = null
            private set

        val service =
            BgmTokenService(
                client =
                    HttpClient(
                        MockEngine { request ->
                            requestCount++
                            lastMethod = request.method
                            lastUrl = request.url.toString()
                            lastForm = (request.body as? FormDataContent)?.formData
                            respond(
                                content = body,
                                status = status,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        },
                    ),
                config = BgmAuthConfig(),
            )
    }

    /** 单进程内存协调器：SingleProcessCoordinator 在 datastore 1.2.1 里是 internal 的。
     *  版本计数必须真实递增：写路径用 incrementAndGetVersion 更新缓存，
     *  恒为 0 会让 tryUpdate 因版本未变而被拒绝，写后读不到新值。 */
    private class LocalCoordinator : InterProcessCoordinator {
        private val mutex = Mutex()
        private var version = 0

        override val updateNotifications: Flow<Unit> = emptyFlow()

        override suspend fun <T> lock(block: suspend () -> T): T = mutex.withLock { block() }

        override suspend fun <T> tryLock(block: suspend (Boolean) -> T): T {
            if (!mutex.tryLock()) return block(false)
            return try {
                block(true)
            } finally {
                mutex.unlock()
            }
        }

        override suspend fun getVersion(): Int = version

        override suspend fun incrementAndGetVersion(): Int = ++version
    }

    /**
     * 内存版 Storage：本套件测的是 AuthRepository 逻辑而非磁盘持久化。
     * 不用文件系统是为了绕开 DataStore 在 Windows 上的已知问题
     * (issuetracker 194301881：读句柄未释放导致原子改名失败、被误报为多实例)。
     */
    private class InMemoryStorage : Storage<UserPreferences> {
        @Volatile
        private var data: UserPreferences = UserPreferences()

        private val coordinator = LocalCoordinator()

        override fun createConnection(): StorageConnection<UserPreferences> =
            object : StorageConnection<UserPreferences> {
                override val coordinator: InterProcessCoordinator = this@InMemoryStorage.coordinator

                override suspend fun <R> readScope(block: suspend (ReadScope<UserPreferences>, Boolean) -> R): R =
                    block(
                        object : ReadScope<UserPreferences> {
                            override suspend fun readData(): UserPreferences = data

                            override fun close() {}
                        },
                        // 第二个参数是 locked：内存存储无跨进程竞争，视为始终拿到读锁
                        true,
                    )

                override suspend fun writeScope(block: suspend (WriteScope<UserPreferences>) -> Unit) {
                    block(
                        object : WriteScope<UserPreferences> {
                            override suspend fun readData(): UserPreferences = data

                            override suspend fun writeData(value: UserPreferences) {
                                data = value
                            }

                            override fun close() {}
                        },
                    )
                }

                override fun close() {}
            }
    }

    private class Harness(
        api: FakeTokenApi,
    ) {
        val tokenProvider = FakeTokenProvider()
        val dataStore: DataStore<UserPreferences> =
            DataStoreFactory.create(
                storage = InMemoryStorage(),
            )
        val userPreferencesDataSource = UserPreferencesDataSource(dataStore)
        val userDataCleaner = UserDataCleaner(listOf(userPreferencesDataSource))
        val repository =
            AuthRepositoryImpl(
                tokenService = api.service,
                tokenProvider = tokenProvider,
                userPreferences = userPreferencesDataSource,
                authConfig = BgmAuthConfig(),
                userDataCleaner = userDataCleaner,
            )

        suspend fun prefs(): UserPreferences = dataStore.data.first()
    }

    private fun apiWith(
        status: HttpStatusCode,
        body: String,
    ) = FakeTokenApi(status, body)

    private fun harness(api: FakeTokenApi) = Harness(api)

    private companion object {
        val SUCCESS_BODY =
            """{"access_token":"at1","refresh_token":"rt1","expires_in":604800,"token_type":"Bearer","user_id":42}"""
        val INVALID_GRANT_BODY = """{"error":"invalid_grant","error_description":"Authorization code doesn't exist"}"""
    }

    @Test
    fun `beginLogin 返回携带 state 的授权地址并持久化`() =
        runTest {
            val harness = harness(apiWith(HttpStatusCode.OK, SUCCESS_BODY))

            val url = harness.repository.beginLogin()

            assertTrue(url.startsWith("https://bgm.tv/oauth/authorize"))
            assertTrue("client_id=bgm69976a90e07dcf869" in url)
            // URLBuilder 会对 query 值做百分号编码
            assertTrue("redirect_uri=minibgm%3A%2F%2Foauth%2Fcallback" in url)
            val state = url.substringAfter("state=")
            assertTrue(state.isNotBlank())
            // URL state = 本地 verifier 的公开指纹
            assertEquals(state, BgmPkce.challenge(harness.prefs().pendingOAuthVerifier))
        }

    @Test
    fun `completeLogin 缺少 code 时失败且不请求 Worker`() =
        runTest {
            val api = apiWith(HttpStatusCode.OK, SUCCESS_BODY)
            val harness = harness(api)

            val result = harness.repository.completeLogin(code = null, state = "any")

            assertIs<AppResult.Error>(result)
            assertEquals(0, api.requestCount)
            assertNull(harness.tokenProvider.accessToken)
            assertTrue(!harness.prefs().isLoggedIn)
        }

    @Test
    fun `completeLogin 未曾 beginLogin 时因缺少持久化 state 而失败`() =
        runTest {
            val api = apiWith(HttpStatusCode.OK, SUCCESS_BODY)
            val harness = harness(api)

            val result = harness.repository.completeLogin(code = "code", state = "forged-state")

            assertIs<AppResult.Error>(result)
            assertEquals(0, api.requestCount)
            assertNull(harness.tokenProvider.accessToken)
        }

    @Test
    fun `completeLogin state 不匹配时失败且不请求 Worker`() =
        runTest {
            val api = apiWith(HttpStatusCode.OK, SUCCESS_BODY)
            val harness = harness(api)
            harness.repository.beginLogin()

            val result = harness.repository.completeLogin(code = "code", state = "forged-state")

            assertIs<AppResult.Error>(result)
            assertEquals(0, api.requestCount)
            assertNull(harness.tokenProvider.accessToken)
            assertTrue(!harness.prefs().isLoggedIn)
        }

    @Test
    fun `completeLogin 成功时 token 加密落盘并建立登录态`() =
        runTest {
            val api = apiWith(HttpStatusCode.OK, SUCCESS_BODY)
            val harness = harness(api)
            val state = harness.repository.beginLogin().substringAfter("state=")

            val result = harness.repository.completeLogin(code = "auth-code", state = state)

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, api.requestCount)
            assertEquals(HttpMethod.Post, api.lastMethod)
            assertEquals(BgmAuthConfig().tokenProxyUrl, api.lastUrl)
            // 兑换表单必须携带 code + state + verifier 三件套供 Worker 校验
            val verifier = api.lastForm?.get("verifier")
            assertEquals("auth-code", api.lastForm?.get("code"))
            assertEquals(state, api.lastForm?.get("state"))
            assertEquals(BgmPkce.challenge(verifier ?: ""), state)
            assertEquals("at1", harness.tokenProvider.accessToken)
            assertEquals("rt1", harness.tokenProvider.refreshToken)
            val prefs = harness.prefs()
            assertTrue(prefs.isLoggedIn)
            assertEquals(42L, prefs.userId)
            assertEquals("", prefs.pendingOAuthVerifier)
        }

    @Test
    fun `completeLogin 被 Worker 拒绝时失败且不落盘`() =
        runTest {
            val api = apiWith(HttpStatusCode.BadRequest, INVALID_GRANT_BODY)
            val harness = harness(api)
            val state = harness.repository.beginLogin().substringAfter("state=")

            val result = harness.repository.completeLogin(code = "stolen-code", state = state)

            assertIs<AppResult.Error>(result)
            assertEquals(1, api.requestCount)
            assertNull(harness.tokenProvider.accessToken)
            assertTrue(!harness.prefs().isLoggedIn)
        }

    @Test
    fun `completeLogin 收到非法成功响应体时失败`() =
        runTest {
            val api = apiWith(HttpStatusCode.OK, "<html>not json</html>")
            val harness = harness(api)
            val state = harness.repository.beginLogin().substringAfter("state=")

            val result = harness.repository.completeLogin(code = "code", state = state)

            assertIs<AppResult.Error>(result)
            assertNull(harness.tokenProvider.accessToken)
            assertTrue(!harness.prefs().isLoggedIn)
        }

    @Test
    fun `logout 清除 token 与登录态但保留普通偏好`() =
        runTest {
            val api = apiWith(HttpStatusCode.OK, SUCCESS_BODY)
            val harness = harness(api)
            val state = harness.repository.beginLogin().substringAfter("state=")
            harness.repository.completeLogin(code = "code", state = state)
            // 直接改普通偏好，模拟用户在登录之外还设置了深色模式、昵称
            harness.dataStore.updateData { it.copy(isDarkMode = true, nickname = "某人") }

            harness.repository.logout()

            assertEquals(1, harness.tokenProvider.clearCount)
            assertNull(harness.tokenProvider.accessToken)
            assertNull(harness.tokenProvider.refreshToken)
            val prefs = harness.prefs()
            assertTrue(!prefs.isLoggedIn)
            assertEquals("", prefs.nickname)
            assertEquals("", prefs.pendingOAuthVerifier)
            assertTrue(prefs.isDarkMode)
        }

    @Test
    fun `两次 beginLogin 产生不同 state`() =
        runTest {
            val harness = harness(apiWith(HttpStatusCode.OK, SUCCESS_BODY))

            val first = harness.repository.beginLogin().substringAfter("state=")
            val second = harness.repository.beginLogin().substringAfter("state=")

            assertNotEquals(first, second)
            assertEquals(second, BgmPkce.challenge(harness.prefs().pendingOAuthVerifier))
        }

    @Test
    fun `偏好标记登录但 token 缺失时（备份恢复场景）不视为已登录`() =
        runTest {
            val harness = harness(apiWith(HttpStatusCode.OK, SUCCESS_BODY))
            // 模拟云备份把 user_preferences.pb 恢复到新设备、auth_tokens.pb 被排除
            harness.dataStore.updateData { it.copy(isLoggedIn = true, userId = 42L) }
            assertNull(harness.tokenProvider.accessToken)
            assertTrue(!harness.repository.isLoggedIn.first())

            // token 到位后登录态成立
            harness.tokenProvider.saveTokens("at1", "rt1")
            assertTrue(harness.repository.isLoggedIn.first())
        }

    @Test
    fun `token 被清除而偏好残留时也不视为已登录`() =
        runTest {
            val harness = harness(apiWith(HttpStatusCode.OK, SUCCESS_BODY))
            val state = harness.repository.beginLogin().substringAfter("state=")
            harness.repository.completeLogin(code = "code", state = state)
            // 模拟密文损坏被 decode 兜底清空的场景：偏好已 clearAuth 前的中间态
            harness.tokenProvider.clearTokens()
            harness.dataStore.updateData { it.copy(isLoggedIn = true) }

            assertTrue(!harness.repository.isLoggedIn.first())
        }

    @Test
    fun `completeLogin 执行期间 isAuthenticating 状态流正确置位与复位`() =
        runTest {
            val harness = harness(apiWith(HttpStatusCode.OK, SUCCESS_BODY))
            assertEquals(false, harness.repository.isAuthenticating.value)

            val state = harness.repository.beginLogin().substringAfter("state=")
            val result = harness.repository.completeLogin(code = "code", state = state)

            assertIs<AppResult.Success<*>>(result)
            assertEquals(false, harness.repository.isAuthenticating.value)
        }
}
