package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** token 兑换/刷新成功后的凭据（bgm.tv 返回体可能携带额外字段，一律忽略） */
@Serializable
data class BgmTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("user_id") val userId: Long = 0L,
)

/** 业务层使用的成对凭据；refresh_token 允许为空（bgm.tv 刷新接口可能不回传新 refresh token） */
data class BgmTokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 经 Worker 代理完成 OAuth token 的兑换与刷新。
 *
 * 必须使用不带 Auth 插件的 "token client"，否则刷新请求自身携带过期
 * 凭据会引发 401 递归。client_secret 与 redirect_uri 均由 Worker 端注入，
 * 请求里不需要、也不允许携带任何凭据字段（Worker 会丢弃客户端发来的
 * client_id/secret/redirect_uri）。
 */
class BgmTokenService(
    private val client: HttpClient,
    private val config: BgmAuthConfig,
) {
    /** state/verifier 供 Worker 做 PKCE 等价校验（见 [BgmPkce]）；Worker 校验通过才代为兑换 */
    suspend fun exchangeCode(
        code: String,
        state: String,
        verifier: String,
    ): BgmTokenResponse =
        request(
            "authorization_code",
            "code" to code,
            "state" to state,
            "verifier" to verifier,
            "redirect_uri" to config.redirectUri,
        )

    suspend fun refresh(refreshToken: String): BgmTokenResponse = request("refresh_token", "refresh_token" to refreshToken)

    /**
     * 供业务 client 的 bearer 刷新回调（Ktor `refreshTokens`）使用的安全刷新入口。
     * 降级策略：
     * - OAuth 端点拒绝（4xx，如 refresh_token 过期的 invalid_grant）→ 返回 null，
     *   业务 client 据此清除本地凭据（登录态随之翻转，自动登出闭环）并沿用
     *   原始 401 抛 [BgmNetworkException.Unauthorized] 供错误提示；
     * - 429 限流、5xx 与网络异常属瞬时故障，原样上抛以免误登出；
     * - 协程取消（含 Ktor 超时异常，其本质是 CancellationException）原样上抛。
     */
    suspend fun refreshOrNull(refreshToken: String): BgmTokenResponse? =
        try {
            refresh(refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException.RateLimited) {
            throw e
        } catch (e: BgmNetworkException.ServerError) {
            if (e.statusCode in 400..499) null else throw e
        } catch (_: BgmNetworkException) {
            null
        }

    private suspend fun request(
        grantType: String,
        vararg params: Pair<String, String>,
    ): BgmTokenResponse {
        val response =
            client.post(config.tokenProxyUrl) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", grantType)
                            params.forEach { (name, value) -> append(name, value) }
                        },
                    ),
                )
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // 注：429 在 BgmHttpClient 的 HttpResponseValidator 中已映射为 RateLimited，
            // 不会走到这里；此处兜底覆盖校验器未映射的状态码（如 400 invalid_grant）
            throw BgmNetworkException.ServerError(response.status.value)
        }
        return BgmHttpClient.jsonConfig.decodeFromString(BgmTokenResponse.serializer(), body)
    }
}
