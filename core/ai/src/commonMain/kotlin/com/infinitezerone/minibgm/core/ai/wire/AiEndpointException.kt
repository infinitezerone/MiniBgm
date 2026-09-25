package com.infinitezerone.minibgm.core.ai.wire

/**
 * 结构化的端点 HTTP 错误：状态码与响应体是一等字段，错误分类读字段而不是猜异常文本。
 *
 * 继承 [IllegalStateException] 是刻意的：既有调用链按 Exception 捕获并把 `message` 交给
 * 字符串兜底分类（非 HTTP 的网络异常没有结构可读），本类的 message 仍携带完整上下文，
 * 兜底路径因此对它依旧有效。
 */
class AiEndpointException(
    val status: Int,
    val responseBody: String,
    /** 端点 Retry-After 头（秒转毫秒）；HTTP-date 形式或缺失时为 null */
    val retryAfterMs: Long? = null,
) : IllegalStateException("HTTP $status: $responseBody")
