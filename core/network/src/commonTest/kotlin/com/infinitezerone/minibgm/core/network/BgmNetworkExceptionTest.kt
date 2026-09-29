package com.infinitezerone.minibgm.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BgmNetworkExceptionTest {
    class FakeTimeoutException(
        message: String,
    ) : Exception(message)

    class FakeUnknownHostException(
        message: String,
    ) : Exception(message)

    class FakeConnectException(
        message: String,
    ) : Exception(message)

    class FakeSSLException(
        message: String,
    ) : Exception(message)

    class FakeSerializationException(
        message: String,
    ) : Exception(message)

    @Test
    fun toBgmNetworkException_identifiesTimeout() {
        val ex = FakeTimeoutException("Request timed out after 15000ms")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.Timeout>(netEx)
        assertEquals("网络超时，请重试", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesDnsFailure() {
        val ex = FakeUnknownHostException("api.bgm.tv: No address associated with hostname")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.DnsFailure>(netEx)
        assertEquals("连不上服务器，请检查网络或代理", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesOfflineOrConnectRefused() {
        val ex = FakeConnectException("Failed to connect to /104.26.12.13:443")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.Offline>(netEx)
        assertEquals("网络未连接", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesSslFailure() {
        val ex = FakeSSLException("Certificate path validation failed")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.SslFailure>(netEx)
        assertEquals("安全连接失败，请检查系统时间", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesSerializationFailure() {
        val ex = FakeSerializationException("Field 'id' is missing in JSON")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.SerializationFailure>(netEx)
        assertEquals("数据异常，请稍后重试", netEx.message)
    }

    @Test
    fun toBgmNetworkException_unwrapsNestedCauseChain() {
        val root = FakeUnknownHostException("api.bgm.tv")
        val wrapper = RuntimeException("Wrapper error", root)
        val outer = IllegalStateException("Outer error", wrapper)

        val netEx = outer.toBgmNetworkException()
        assertIs<BgmNetworkException.DnsFailure>(netEx)
    }

    @Test
    fun toUserFriendlyMessage_formatsWithoutPrefix() {
        val timeout = FakeTimeoutException("timed out")
        assertEquals("网络超时，请重试", timeout.toUserFriendlyMessage())

        val serverError = BgmNetworkException.ServerError(502)
        assertEquals("服务器异常 (502)，请稍后重试", serverError.toUserFriendlyMessage())
    }

    @Test
    fun toUserFriendlyMessage_formatsWithPrefixAndPreventsDuplicateSuffix() {
        val dns = FakeUnknownHostException("hostname not found")
        assertEquals("获取条目详情失败：连不上服务器，请检查网络或代理", dns.toUserFriendlyMessage("获取条目详情"))
        // 即使传入带 "失败" 或 "异常" 的前缀，也能自动消除重复
        assertEquals("打卡失败：连不上服务器，请检查网络或代理", dns.toUserFriendlyMessage("打卡失败"))
        assertEquals("同步在看收藏失败：连不上服务器，请检查网络或代理", dns.toUserFriendlyMessage("同步在看收藏异常"))
    }

    @Test
    fun toUserFriendlyMessage_sanitizesRawJavaExceptionStrings() {
        val rawJavaEx = RuntimeException("java.net.SocketTimeoutException: Read timed out")
        val msg = rawJavaEx.toUserFriendlyMessage("拉取数据")
        // 会被识别为超时或兜底为友好文案，绝不直接对外暴露 java.net 字符串
        assertEquals("拉取数据失败：网络超时，请重试", msg)
    }

    @Test
    fun toUserFriendlyMessage_replacesUnknownNonChineseMessageWithFallback() {
        // 认不出类型的异常只在**文案本身是中文**时才透传：中文只可能出自我们手写的领域提示，
        // 而英文原文来自第三方库或系统。旧版这里会把 "Network error" 原样放给用户，是实打实的泄漏。
        assertEquals("网络异常，请稍后重试", RuntimeException("Network error").toUserFriendlyMessage())
        // 带前缀时同样不该把英文带出去
        assertEquals("获取单集吐槽失败：网络异常，请稍后重试", RuntimeException("Network error").toUserFriendlyMessage("获取单集吐槽"))

        // 反向钉子：我们自己的中文领域提示必须原样保留，不能被兜底文案吃掉
        assertEquals("已在您的「想看」列表中", RuntimeException("已在您的「想看」列表中").toUserFriendlyMessage())
    }

    @Test
    fun defaultMessages_stayShortEnoughForASnackbarLine() {
        // 每条文案都会和动作前缀拼成 `xxx失败：<这里>` 才落到 Snackbar，一行放不下就被截断。
        // 历史上出现过 `安全连接建立失败，请检查系统时间或网络安全设置`（21 字）与
        // `Bangumi 服务器异常 (500)，请稍后重试`（22 字）这类堆字退化。
        // 上面那些 assertEquals 只能钉住已存在的文案，这条上限是拦**新增**错误类型再往这个方向漂。
        val defaults =
            listOf(
                BgmNetworkException.Unauthorized().message,
                BgmNetworkException.Forbidden().message,
                BgmNetworkException.NotFound().message,
                BgmNetworkException.RateLimited().message,
                BgmNetworkException.ServerError(500).message,
                BgmNetworkException.Timeout().message,
                BgmNetworkException.Offline().message,
                BgmNetworkException.DnsFailure().message,
                BgmNetworkException.SslFailure().message,
                BgmNetworkException.SerializationFailure().message,
            ).map { it.orEmpty() }

        defaults.forEach { msg ->
            assertTrue(msg.length <= 18, "文案过长（${msg.length} 字），Snackbar 会被截断：$msg")
        }
    }
}
