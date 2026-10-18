package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.TokenProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * refreshOrNull 的降级策略回归测试：
 * - OAuth 端点拒绝（4xx，凭据已不可恢复）→ null，上层清凭据完成登出闭环
 * - 429 / 5xx / 网络异常（瞬时故障）→ 原样上抛，避免误登出
 */
class BgmTokenServiceRefreshTest {
    private val noopTokenProvider =
        object : TokenProvider {
            override suspend fun getAccessToken(): String? = null

            override suspend fun getRefreshToken(): String? = null

            override suspend fun saveTokens(
                userId: Long,
                accessToken: String,
                refreshToken: String,
            ) {}

            override suspend fun saveTokens(
                accessToken: String,
                refreshToken: String,
            ) {}

            override suspend fun setActiveUser(userId: Long) {}

            override suspend fun removeTokens(userId: Long) {}

            override suspend fun clearTokens() {}

            override val hasTokens: kotlinx.coroutines.flow.Flow<Boolean>
                get() = kotlinx.coroutines.flow.flowOf(false)

            override val activeUserId: kotlinx.coroutines.flow.Flow<Long?>
                get() = kotlinx.coroutines.flow.flowOf(null)
        }

    // 必须经 BgmHttpClient.create 构建：生产链路的 HttpResponseValidator
    // 决定了 429→RateLimited、401→Unauthorized、5xx→ServerError 的映射，
    // 裸 HttpClient 不含该插件，测出的降级行为会与生产不一致
    private fun serviceWith(engine: MockEngine): BgmTokenService =
        BgmTokenService(
            client =
                BgmHttpClient.create(
                    userAgent = "MiniBgm/test",
                    tokenProvider = noopTokenProvider,
                    engine = engine,
                ),
            config = BgmAuthConfig(tokenProxyUrl = "https://auth.minibgm.test/oauth/token"),
        )

    private fun serviceWith(
        status: HttpStatusCode,
        body: String = """{"error":"invalid_grant"}""",
    ): BgmTokenService =
        serviceWith(
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType to listOf("application/json")))
            },
        )

    /** 生产 token client 是 createBaseClient（无 validator），429 会以 ServerError 形态到达。 */
    private fun serviceWithBaseClient(status: HttpStatusCode): BgmTokenService =
        BgmTokenService(
            client =
                BgmHttpClient.createBaseClient(
                    engine = MockEngine { respond("", status) },
                ),
            config = BgmAuthConfig(tokenProxyUrl = "https://auth.minibgm.test/oauth/token"),
        )

    private val successBody =
        """{"access_token":"na","refresh_token":"nr","expires_in":604800,"token_type":"Bearer","user_id":1}"""

    @Test
    fun `上游 400 拒绝时返回 null 触发登出闭环`() =
        runTest {
            assertNull(serviceWith(HttpStatusCode.BadRequest).refreshOrNull("rt"))
        }

    @Test
    fun `上游 401 时返回 null`() =
        runTest {
            assertNull(serviceWith(HttpStatusCode.Unauthorized).refreshOrNull("rt"))
        }

    @Test
    fun `上游 403 时返回 null`() =
        runTest {
            assertNull(serviceWith(HttpStatusCode.Forbidden).refreshOrNull("rt"))
        }

    @Test
    fun `429 限流属瞬时故障 原样上抛而非登出`() =
        runTest {
            assertFailsWith<BgmNetworkException.RateLimited> {
                serviceWith(HttpStatusCode.TooManyRequests).refreshOrNull("rt")
            }
        }

    @Test
    fun `5xx 属瞬时故障 原样上抛而非登出`() =
        runTest {
            assertFailsWith<BgmNetworkException.ServerError> {
                serviceWith(HttpStatusCode.ServiceUnavailable).refreshOrNull("rt")
            }
        }

    @Test
    fun `生产 token client 的 429 仍按限流上抛`() =
        runTest {
            assertFailsWith<BgmNetworkException.RateLimited> {
                serviceWithBaseClient(HttpStatusCode.TooManyRequests).refreshOrNull("rt")
            }
        }

    @Test
    fun `刷新成功时正常返回凭据`() =
        runTest {
            val refreshed = assertNotNull(serviceWith(HttpStatusCode.OK, successBody).refreshOrNull("rt"))
            assertEquals("na", refreshed.accessToken)
            assertEquals("nr", refreshed.refreshToken)
        }

    /** 超时/断网是瞬时故障：必须原样上抛，绝不能返回 null 触发清凭据（误登出）。 */
    @Test
    fun `超时等瞬时网络异常原样上抛而不是清凭据`() =
        runTest {
            val service = serviceWith(MockEngine { throw BgmNetworkException.Timeout() })

            assertFailsWith<BgmNetworkException.Timeout> {
                service.refreshOrNull("rt")
            }
        }

    /** 408/425 是服务端没给出结论，不是凭据失效；判成 invalid_grant 会误登出。 */
    @Test
    fun `408 与 425 属瞬时故障 上抛而不是清凭据`() =
        runTest {
            assertFailsWith<BgmNetworkException.Timeout> {
                serviceWithBaseClient(HttpStatusCode.RequestTimeout).refreshOrNull("rt")
            }
            assertFailsWith<BgmNetworkException.ServerError> {
                serviceWithBaseClient(HttpStatusCode.TooEarly).refreshOrNull("rt")
            }
        }
}
