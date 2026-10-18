package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.HttpTimeoutConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestBudgetTest {
    @Test
    fun `capability 缺失时回落到引擎默认`() {
        assertEquals(15_000L, resolveRequestBudgetMillis(timeoutConfig = null, engineDefaultMillis = 15_000L))
    }

    @Test
    fun `capability 存在但请求预算为 null 时按无限处理`() {
        val config = HttpTimeoutConfig(requestTimeoutMillis = null, socketTimeoutMillis = 30_000L)
        assertEquals(
            HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
            resolveRequestBudgetMillis(config, engineDefaultMillis = 15_000L),
        )
    }

    @Test
    fun `显式请求预算优先于引擎默认`() {
        val config = HttpTimeoutConfig(requestTimeoutMillis = 5_000L)
        assertEquals(5_000L, resolveRequestBudgetMillis(config, engineDefaultMillis = 15_000L))
    }

    @Test
    fun `显式无限预算保持无限`() {
        val config = HttpTimeoutConfig(requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS)
        assertEquals(
            HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
            resolveRequestBudgetMillis(config, engineDefaultMillis = 15_000L),
        )
    }
}
