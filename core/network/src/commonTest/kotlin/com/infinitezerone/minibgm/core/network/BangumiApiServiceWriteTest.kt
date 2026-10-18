package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.TokenProvider
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

/**
 * 写操作失败必须让调用方看见。
 *
 * 回归背景：validator 只映射了 401/403/404/429/500/502/503，而 updateCollection /
 * updateEpisodesStatus 既不读 status 也不解析响应体——400/409/504 会被静默当成成功，
 * 用户看到"已收藏"，云端却没有落库。
 */
class BangumiApiServiceWriteTest {
    private object NoopTokenProvider : TokenProvider {
        override suspend fun getAccessToken(): String? = null

        override suspend fun getRefreshToken(): String? = null

        override suspend fun saveTokens(
            userId: Long,
            accessToken: String,
            refreshToken: String,
        ) = Unit

        override suspend fun saveTokens(
            accessToken: String,
            refreshToken: String,
        ) = Unit

        override suspend fun setActiveUser(userId: Long) = Unit

        override suspend fun removeTokens(userId: Long) = Unit

        override suspend fun clearTokens() = Unit

        override val hasTokens: Flow<Boolean> = flowOf(false)

        override val activeUserId: Flow<Long?> = flowOf(null)
    }

    private fun service(status: HttpStatusCode): BangumiApiService =
        BangumiApiServiceImpl(
            client =
                BgmHttpClient.create(
                    userAgent = "MiniBgm/test",
                    tokenProvider = NoopTokenProvider,
                    engine =
                        MockEngine {
                            respond(
                                content = "",
                                status = status,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        },
                ),
            authConfig = BgmAuthConfig(),
        )

    @Test
    fun `收藏接口遇到 400 必须抛 RequestRejected`() =
        runTest {
            val error =
                assertFailsWith<BgmNetworkException.RequestRejected> {
                    service(HttpStatusCode.BadRequest).updateCollection(subjectId = 1, type = 2)
                }
            assertEquals(400, error.statusCode)
        }

    @Test
    fun `打卡接口遇到 409 必须抛 RequestRejected`() =
        runTest {
            val error =
                assertFailsWith<BgmNetworkException.RequestRejected> {
                    service(HttpStatusCode.Conflict).updateEpisodesStatus(
                        subjectId = 1,
                        episodeIds = listOf(11L, 12L),
                        type = 2,
                    )
                }
            assertEquals(409, error.statusCode)
        }

    @Test
    fun `收藏接口 200 时正常返回`() =
        runTest {
            service(HttpStatusCode.OK).updateCollection(subjectId = 1, type = 2)
        }
}
