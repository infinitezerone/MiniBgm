package com.infinitezerone.minibgm.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        assertEquals("网络请求超时，请检查网络连接后重试", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesDnsFailure() {
        val ex = FakeUnknownHostException("api.bgm.tv: No address associated with hostname")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.DnsFailure>(netEx)
        assertEquals("无法连接到服务器，请检查网络或代理设置", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesOfflineOrConnectRefused() {
        val ex = FakeConnectException("Failed to connect to /104.26.12.13:443")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.Offline>(netEx)
        assertEquals("当前网络不可用，请检查网络连接", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesSslFailure() {
        val ex = FakeSSLException("Certificate path validation failed")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.SslFailure>(netEx)
        assertEquals("安全连接建立失败，请检查系统时间或网络安全设置", netEx.message)
    }

    @Test
    fun toBgmNetworkException_identifiesSerializationFailure() {
        val ex = FakeSerializationException("Field 'id' is missing in JSON")
        val netEx = ex.toBgmNetworkException()
        assertIs<BgmNetworkException.SerializationFailure>(netEx)
        assertEquals("数据解析异常，请稍后重试", netEx.message)
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
        assertEquals("网络请求超时，请检查网络连接后重试", timeout.toUserFriendlyMessage())

        val serverError = BgmNetworkException.ServerError(502)
        assertEquals("Bangumi 服务器异常 (502)，请稍后重试", serverError.toUserFriendlyMessage())
    }

    @Test
    fun toUserFriendlyMessage_formatsWithPrefixAndPreventsDuplicateSuffix() {
        val dns = FakeUnknownHostException("hostname not found")
        assertEquals("获取条目详情失败：无法连接到服务器，请检查网络或代理设置", dns.toUserFriendlyMessage("获取条目详情"))
        // 即使传入带 "失败" 或 "异常" 的前缀，也能自动消除重复
        assertEquals("打卡失败：无法连接到服务器，请检查网络或代理设置", dns.toUserFriendlyMessage("打卡失败"))
        assertEquals("同步在看收藏失败：无法连接到服务器，请检查网络或代理设置", dns.toUserFriendlyMessage("同步在看收藏异常"))
    }

    @Test
    fun toUserFriendlyMessage_sanitizesRawJavaExceptionStrings() {
        val rawJavaEx = RuntimeException("java.net.SocketTimeoutException: Read timed out")
        val msg = rawJavaEx.toUserFriendlyMessage("拉取数据")
        // 会被识别为超时或兜底为友好文案，绝不直接对外暴露 java.net 字符串
        assertEquals("拉取数据失败：网络请求超时，请检查网络连接后重试", msg)
    }
}
