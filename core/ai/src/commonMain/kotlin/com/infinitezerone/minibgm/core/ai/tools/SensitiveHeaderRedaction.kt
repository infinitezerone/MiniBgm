package com.infinitezerone.minibgm.core.ai.tools

/**
 * 工具结果会原样发给用户配置的第三方 LLM 端点：凭据类 header 值必须先替换为占位符。
 * 仅用于"回传给模型的副本"；需要真实 header 的消费方（播放器、规则验证）拿 Store/仓库原始数据。
 */
internal const val REDACTED_VALUE = "__REDACTED__"

internal val SENSITIVE_HEADER_NAMES =
    setOf("authorization", "cookie", "set-cookie", "x-api-key", "api-key", "token", "proxy-authorization")

internal fun redactSensitiveHeaders(headers: Map<String, String>): Map<String, String> =
    headers.mapValues { (name, value) ->
        if (name.lowercase().trim() in SENSITIVE_HEADER_NAMES) REDACTED_VALUE else value
    }
