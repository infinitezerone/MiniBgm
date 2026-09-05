package com.infinitezerone.minibgm.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Auth 插件与 HttpResponseValidator 的协同回归测试：
 * 401 必须先走 bearer 刷新重试；刷新彻底失败时清除本地凭据（isLoggedIn
 * 随之翻转，自动登出闭环）并以 BgmNetworkException.Unauthorized 上抛。
 */
class BgmHttpClientAuthTest {
    private class FakeTokenProvider(
        initialAccess: String?,
        initialRefresh: String?,
    ) : TokenProvider {
        var accessToken: String? = initialAccess
            private set
        var refreshToken: String? = initialRefresh
            private set
        var saveCount = 0
            private set
        var clearCount = 0
            private set

        override suspend fun getAccessToken(): String? = accessToken

        override suspend fun getRefreshToken(): String? = refreshToken

        override val hasTokens: Flow<Boolean>
            get() = flowOf(accessToken != null)

        override val activeUserId: Flow<Long?>
            get() = flowOf(1L)

        override suspend fun saveTokens(
            userId: Long,
            accessToken: String,
            refreshToken: String,
        ) {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            saveCount++
        }

        override suspend fun setActiveUser(userId: Long) = Unit

        override suspend fun removeTokens(userId: Long) {
            clearTokens()
        }

        override suspend fun clearTokens() {
            accessToken = null
            refreshToken = null
            clearCount++
        }
    }

    @Test
    fun `401 时经 refresher 刷新并以新凭据重试`() =
        runTest {
            val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    if (requests.size == 1) {
                        respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType to listOf("application/json")))
                    } else {
                        respond(
                            """{"id":1}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType to listOf("application/json")),
                        )
                    }
                }
            val provider = FakeTokenProvider("old-access", "old-refresh")
            val client =
                BgmHttpClient.create(
                    userAgent = "MiniBgm/test",
                    tokenProvider = provider,
                    tokenRefresher = { old ->
                        assertEquals("old-refresh", old)
                        BgmTokenPair(accessToken = "new-access", refreshToken = "new-refresh")
                    },
                    engine = engine,
                )

            val body = client.get("https://api.bgm.tv/v0/subjects/1").bodyAsText()

            assertEquals("""{"id":1}""", body)
            assertEquals(2, requests.size)
            assertEquals("Bearer old-access", requests[0].headers[HttpHeaders.Authorization])
            assertEquals("Bearer new-access", requests[1].headers[HttpHeaders.Authorization])
            assertEquals(1, provider.saveCount)
            assertEquals("new-access", provider.accessToken)
            assertEquals("new-refresh", provider.refreshToken)
        }

    @Test
    fun `刷新彻底失败时清除凭据并以 Unauthorized 上抛`() =
        runTest {
            val engine =
                MockEngine {
                    respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType to listOf("application/json")))
                }
            val provider = FakeTokenProvider("old-access", "old-refresh")
            val client =
                BgmHttpClient.create(
                    userAgent = "MiniBgm/test",
                    tokenProvider = provider,
                    tokenRefresher = { null },
                    engine = engine,
                )

            assertFailsWith<BgmNetworkException.Unauthorized> {
                client.get("https://api.bgm.tv/v0/subjects/1")
            }
            assertEquals(0, provider.saveCount)
            assertEquals(1, provider.clearCount)
            assertEquals(null, provider.accessToken)
            assertEquals(null, provider.refreshToken)
        }

    @Test
    fun `缺少 refresh_token 时同样清除凭据避免假登录`() =
        runTest {
            val engine =
                MockEngine {
                    respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType to listOf("application/json")))
                }
            val provider = FakeTokenProvider("old-access", null)
            val client =
                BgmHttpClient.create(
                    userAgent = "MiniBgm/test",
                    tokenProvider = provider,
                    tokenRefresher = { null },
                    engine = engine,
                )

            assertFailsWith<BgmNetworkException.Unauthorized> {
                client.get("https://api.bgm.tv/v0/subjects/1")
            }
            assertEquals(1, provider.clearCount)
        }
}
