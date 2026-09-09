package com.infinitezerone.minibgm.core.network

sealed class BgmNetworkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unauthorized(
        message: String = "未授权或登录已过期",
    ) : BgmNetworkException(message)

    class Forbidden(
        message: String = "访问受限",
    ) : BgmNetworkException(message)

    class NotFound(
        message: String = "请求的资源不存在",
    ) : BgmNetworkException(message)

    class RateLimited(
        message: String = "请求过于频繁，已被限流，请稍后重试",
    ) : BgmNetworkException(message)

    class ServerError(
        val statusCode: Int,
        message: String = "Bangumi 服务器异常 ($statusCode)，请稍后重试",
    ) : BgmNetworkException(message)

    class Timeout(
        message: String = "网络请求超时，请检查网络连接后重试",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    class Offline(
        message: String = "当前网络不可用，请检查网络连接",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    class DnsFailure(
        message: String = "无法连接到服务器，请检查网络或代理设置",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    class SslFailure(
        message: String = "安全连接建立失败，请检查系统时间或网络安全设置",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    class SerializationFailure(
        message: String = "数据解析异常，请稍后重试",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    class Unknown(
        message: String,
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)
}

/**
 * 将任意底层网络异常（超时、DNS 无法解析、断网、SSL 握手、JSON 反序列化等）
 * 归一化为类型安全的 [BgmNetworkException]。
 */
fun Throwable.toBgmNetworkException(): BgmNetworkException {
    if (this is BgmNetworkException) return this

    val causes = generateSequence(this) { it.cause }.take(8).toList()

    if (causes.any(::isTimeout)) return BgmNetworkException.Timeout(cause = this)
    if (causes.any(::isDns)) return BgmNetworkException.DnsFailure(cause = this)
    if (causes.any(::isOfflineOrUnreachable)) return BgmNetworkException.Offline(cause = this)
    if (causes.any(::isSsl)) return BgmNetworkException.SslFailure(cause = this)
    if (causes.any(::isSerialization)) return BgmNetworkException.SerializationFailure(cause = this)

    val cleanMessage = message?.takeIf { it.isNotBlank() && it != "null" } ?: "网络连接异常，请稍后重试"
    return BgmNetworkException.Unknown(cleanMessage, this)
}

private fun isTimeout(t: Throwable): Boolean {
    val name = t::class.simpleName.orEmpty()
    return name.contains("Timeout", ignoreCase = true) ||
        t.message?.contains("timed out", ignoreCase = true) == true
}

private fun isDns(t: Throwable): Boolean {
    val name = t::class.simpleName.orEmpty()
    return name.contains("UnknownHost", ignoreCase = true) ||
        name.contains("UnresolvedAddress", ignoreCase = true) ||
        t.message?.contains("No address associated with hostname", ignoreCase = true) == true
}

private fun isOfflineOrUnreachable(t: Throwable): Boolean {
    val name = t::class.simpleName.orEmpty()
    return name.contains("ConnectException", ignoreCase = true) ||
        name.contains("NoRouteToHost", ignoreCase = true) ||
        t.message?.contains("Connection refused", ignoreCase = true) == true ||
        t.message?.contains("Network is unreachable", ignoreCase = true) == true
}

private fun isSsl(t: Throwable): Boolean {
    val name = t::class.simpleName.orEmpty()
    return name.contains("SSL", ignoreCase = true) ||
        name.contains("CertPath", ignoreCase = true)
}

private fun isSerialization(t: Throwable): Boolean {
    val name = t::class.simpleName.orEmpty()
    return name.contains("Serialization", ignoreCase = true) ||
        name.contains("JsonConvert", ignoreCase = true)
}

/**
 * 将异常转换为面向用户的易读中文提示，消除 Java 类名及底层英文错误。
 * @param actionPrefix 可选动作前缀，如 "获取条目"、"打卡"，会自动格式化为 "${actionPrefix}失败：${message}"。
 */
fun Throwable.toUserFriendlyMessage(actionPrefix: String? = null): String {
    val netEx = if (this is BgmNetworkException) this else this.toBgmNetworkException()
    val rawMessage = netEx.message?.trim().orEmpty()
    val userMessage =
        when {
            netEx !is BgmNetworkException.Unknown && rawMessage.isNotBlank() -> rawMessage
            rawMessage.isBlank() || rawMessage == "null" -> "网络请求失败，请稍后重试"
            rawMessage.startsWith("java.") || rawMessage.startsWith("io.ktor.") -> "网络请求失败，请稍后重试"
            else -> rawMessage
        }
    return if (actionPrefix.isNullOrBlank()) {
        userMessage
    } else {
        val prefix = actionPrefix.removeSuffix("失败").removeSuffix("异常")
        "${prefix}失败：$userMessage"
    }
}
