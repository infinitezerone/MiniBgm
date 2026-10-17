package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpMethod
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
    fun non_timeout_exceptions_keep_the_original_policy() {
        assertTrue(shouldRetryAfterException(HttpMethod.Get, IllegalStateException("connection reset"), retryCount = 1))
        assertTrue(
            shouldRetryAfterException(HttpMethod.Post, IllegalStateException("connection reset"), retryCount = 1),
            "非超时异常沿用原策略：写请求遇到连接重置仍可重试（请求未发出）",
        )
    }
}
