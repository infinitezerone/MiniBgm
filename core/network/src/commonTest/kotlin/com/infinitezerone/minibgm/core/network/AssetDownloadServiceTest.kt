package com.infinitezerone.minibgm.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 回环流判定回归测试。
 *
 * 该判定决定本地 MP4 流走纯流式 CIO 引擎还是 ECH 引擎，误判会让 GB 级本地视频
 * 走全量内存缓冲的 ECH 通道（见 [AssetDownloadServiceImpl.streamAsset]）。
 */
class AssetDownloadServiceTest {
    @Test
    fun `回环地址被识别为本地流`() {
        assertTrue(isLoopbackHttpUrl("http://127.0.0.1:8899/stream?hash=abc"))
        assertTrue(isLoopbackHttpUrl("http://127.0.0.1/stream"))
        assertTrue(isLoopbackHttpUrl("http://localhost:18080/stream/0"))
        assertTrue(isLoopbackHttpUrl("http://localhost/stream"))
        assertTrue(isLoopbackHttpUrl("http://[::1]:8899/stream"))
        assertTrue(isLoopbackHttpUrl("http://[::1]/stream"))
    }

    @Test
    fun `大小写与首尾空白被归一化`() {
        assertTrue(isLoopbackHttpUrl("  HTTP://127.0.0.1:8899/STREAM  "))
        assertTrue(isLoopbackHttpUrl("Http://LocalHost:8080/x"))
    }

    @Test
    fun `外部地址不被识别为本地流`() {
        assertFalse(isLoopbackHttpUrl("https://example.com/stream"))
        assertFalse(isLoopbackHttpUrl("http://example.com/stream"))
        assertFalse(isLoopbackHttpUrl("http://10.0.0.1:8899/stream"))
        assertFalse(isLoopbackHttpUrl("http://127.1.1.1:8899/stream"))
        assertFalse(isLoopbackHttpUrl("http://localhost.evil.com:8899/stream"))
    }

    @Test
    fun `缺少端口与路径分隔符的裸地址或空串不判定为本地流`() {
        assertFalse(isLoopbackHttpUrl(""))
        assertFalse(isLoopbackHttpUrl("http://127.0.0.1"))
        assertFalse(isLoopbackHttpUrl("http://localhost"))
    }
}
