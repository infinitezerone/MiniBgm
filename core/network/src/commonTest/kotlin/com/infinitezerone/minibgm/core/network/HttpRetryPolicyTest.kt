package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 超时重试策略的纯函数契约。
 *
 * 背景：引擎曾把「自有超时」直接抛成 [CancellationException]，而重试策略显式排除取消语义——
 * 结果一次超时就把整次请求判死（详情页整页失败），而用户点「重试」只是再发一遍新请求。
 * 现在引擎把自有超时翻译成 [HttpRequestTimeoutException]，策略再按「幂等方法才重试」放行：
 * 读请求能自愈，写请求不会被悄悄发两遍（重复落库）。
 */
class HttpRetryPolicyTest {
    private fun timeout() = HttpRequestTimeoutException("https://api.bgm.tv/v0/subjects/1", 15_000, null)

    @Test
    fun get_timeout_is_retried() {
        assertTrue(shouldRetryAfterException(HttpMethod.Get, timeout(), retryCount = 1))
    }

    @Test
    fun head_timeout_is_retried() {
        assertTrue(shouldRetryAfterException(HttpMethod.Head, timeout(), retryCount = 1))
    }

    @Test
    fun post_timeout_is_never_retried() {
        assertFalse(
            shouldRetryAfterException(HttpMethod.Post, timeout(), retryCount = 1),
            "写请求超时重试会重复落库（打卡/收藏/回复），必须直接失败交给用户重试",
        )
    }

    @Test
    fun patch_timeout_is_never_retried() {
        assertFalse(shouldRetryAfterException(HttpMethod.Patch, timeout(), retryCount = 1))
    }

    @Test
    fun idempotent_put_and_delete_timeouts_are_retried() {
        assertTrue(shouldRetryAfterException(HttpMethod.Put, timeout(), retryCount = 1))
        assertTrue(shouldRetryAfterException(HttpMethod.Delete, timeout(), retryCount = 1))
    }

    @Test
    fun get_timeout_is_not_retried_a_second_time() {
        assertFalse(
            shouldRetryAfterException(HttpMethod.Get, timeout(), retryCount = 2),
            "超时只重试一次：死站点不该让用户等满 3 个请求预算",
        )
    }

    @Test
    fun cancellation_is_never_retried_even_for_get() {
        assertFalse(
            shouldRetryAfterException(HttpMethod.Get, CancellationException("caller left the screen"), retryCount = 1),
            "取消语义重试等于让取消失效",
        )
    }

    @Test
    fun transport_exceptions_are_retried_only_for_idempotent_methods() {
        assertTrue(shouldRetryAfterException(HttpMethod.Get, IllegalStateException("connection reset"), retryCount = 1))
        assertFalse(
            shouldRetryAfterException(HttpMethod.Post, IllegalStateException("connection reset"), retryCount = 1),
            "传输层异常无法证明请求未发出：写请求自动重放可能重复落库",
        )
    }

    @Test
    fun classified_http_errors_are_not_retried_again() {
        assertFalse(
            shouldRetryAfterException(HttpMethod.Get, BgmNetworkException.ServerError(500), retryCount = 1),
            "5xx 已在 retryIf 阶段处理过，按异常再重试一次等于重放同一个失败",
        )
    }

    @Test
    fun too_many_requests_is_retried_for_any_method() {
        assertTrue(shouldRetryAfterResponse(HttpMethod.Post, HttpStatusCode.TooManyRequests))
        assertTrue(shouldRetryAfterResponse(HttpMethod.Get, HttpStatusCode.TooManyRequests))
    }

    @Test
    fun server_errors_are_retried_only_for_idempotent_methods() {
        assertTrue(shouldRetryAfterResponse(HttpMethod.Get, HttpStatusCode.InternalServerError))
        assertFalse(
            shouldRetryAfterResponse(HttpMethod.Post, HttpStatusCode.InternalServerError),
            "5xx 的处理结果未知：写请求重放可能重复落库",
        )
        assertFalse(shouldRetryAfterResponse(HttpMethod.Get, HttpStatusCode.NotFound))
    }
}
