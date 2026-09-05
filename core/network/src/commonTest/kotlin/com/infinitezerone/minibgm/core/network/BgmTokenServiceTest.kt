package com.infinitezerone.minibgm.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * refreshOrNull 降级策略回归测试：凭据被 OAuth 拒绝（4xx）必须降级为 null，
 * 供 bearer 回退原始 401 并清除本地凭据（自动登出闭环）；5xx 瞬时故障原样上抛，
 * 避免把服务端抖动误判成登录失效。
 */
class BgmTokenServiceTest {
    private class NoopTokenProvider : TokenProvider {
        override suspend fun getAccessToken(): String? = null

        override suspend fun getRefreshToken(): String? = null

        override val hasTokens: Flow<Boolean>
            get() = flowOf(false)

        override val activeUserId: Flow<Long?>
            get() = flowOf(null)

        override suspend fun saveTokens(
            userId: Long,
            accessToken: String,
            refreshToken: String,
        ) = Unit

        override suspend fun setActiveUser(userId: Long) = Unit

        override suspend fun removeTokens(userId: Long) = Unit

        override suspend fun clearTokens() = Unit
    }

    private fun service(engine: MockEngine): BgmTokenService =
        BgmTokenService(
            client = BgmHttpClient.create(userAgent = "MiniBgm/test", tokenProvider = NoopTokenProvider(), engine = engine),
            config = BgmAuthConfig(),
        )

    @Test
    fun `refresh_token 失效（400 invalid_grant）时降级为 null 而非抛异常`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"error":"invalid_grant"}""",
                        HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            assertNull(service(engine).refreshOrNull("expired-token"))
        }

    @Test
    fun `刷新遭遇 5xx 时原样上抛 ServerError 而非降级为 null`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        "",
                        HttpStatusCode.ServiceUnavailable,
                        headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            val e =
                assertFailsWith<BgmNetworkException.ServerError> {
                    service(engine).refreshOrNull("valid-token")
                }
            assertEquals(503, e.statusCode)
        }

    @Test
    fun `刷新成功时正常返回凭据`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"access_token":"new-access","refresh_token":"","token_type":"Bearer","expires_in":604800}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            val tokens = service(engine).refreshOrNull("old-token")
            assertEquals("new-access", tokens?.accessToken)
            assertEquals("", tokens?.refreshToken)
        }
}
