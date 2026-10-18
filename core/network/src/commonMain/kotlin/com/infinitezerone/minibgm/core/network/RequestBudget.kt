package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.HttpTimeoutConfig

/**
 * 请求预算的唯一解释器：区分「没有 HttpTimeout 能力」与「能力存在但显式关闭」。
 *
 * Ktor 的 per-request `requestTimeoutMillis = null` 在插件层会被回填成 client 默认值；
 * 但引擎仍可能在 capability 缺失/字段为 null 时收到 null。此时不能把 null 当成
 * 引擎默认值——那正是「想关超时却被悄悄改成 15s」的来源。null 字段一律按无限处理，
 * 只有 capability 整体缺失才回落到引擎默认。
 */
internal fun resolveRequestBudgetMillis(
    timeoutConfig: HttpTimeoutConfig?,
    engineDefaultMillis: Long,
): Long =
    when {
        timeoutConfig == null -> engineDefaultMillis
        timeoutConfig.requestTimeoutMillis == null -> HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        else -> timeoutConfig.requestTimeoutMillis!!
    }
