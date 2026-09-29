package com.infinitezerone.minibgm.core.network

/**
 * 网络层异常 → 用户可见文案的**唯一出口**。
 *
 * 文案规范（新增错误类型时照这个来，别退回旧风格）：
 * 1. 只写"发生了什么"，加一句**真正可执行**的动作；动作显而易见时连这句也省掉。
 * 2. **不写同义反复**——这是最容易退化的地方：`请求过于频繁，已被限流`、
 *    `网络不可用，请检查网络连接`，前后半句说的是同一件事，等于把一句话说了两遍。
 * 3. **不给无效建议**：超时意味着网是通的，就别照抄 [Offline] 的"检查网络连接"。
 * 4. 尽量压在 15 字以内。这些文案总要和动作前缀拼成 `xxx失败：<这里>` 才落到 Snackbar，
 *    Snackbar 一行放不下就被截断，多出来的字是负收益。
 *
 * 第 4 条由 [BgmNetworkExceptionTest.defaultMessages_stayShortEnoughForASnackbarLine] 兜底，
 * 上限 18 字（给 [ServerError] 的状态码留余量）。写超了测试会红——那时候该做的是把话说短，
 * 而不是把阈值调大。
 */
sealed class BgmNetworkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** 401。Ktor 会自动刷新令牌，能走到这里说明刷新也失败了，客户端随后会自动登出 */
    class Unauthorized(
        message: String = "登录已过期",
    ) : BgmNetworkException(message)

    /** 403 */
    class Forbidden(
        message: String = "访问受限",
    ) : BgmNetworkException(message)

    /** 404 */
    class NotFound(
        message: String = "内容不存在",
    ) : BgmNetworkException(message)

    /** 429。保留"请稍后重试"——用户第一反应就是立刻重试，这句不是废话 */
    class RateLimited(
        message: String = "操作太频繁，请稍后重试",
    ) : BgmNetworkException(message)

    /** 5xx。带状态码，便于用户反馈时定位 */
    class ServerError(
        val statusCode: Int,
        message: String = "服务器异常 ($statusCode)，请稍后重试",
    ) : BgmNetworkException(message)

    /**
     * 请求超时。
     *
     * 刻意不提"检查网络连接"——那是 [Offline] 的台词。超时的含义恰恰是"网是通的，
     * 只是对面没及时回"，给同一句建议会让两类错误无从区分。
     */
    class Timeout(
        message: String = "网络超时，请重试",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    /** 断网 / 连接被拒。只说事实——该做什么用户自己清楚 */
    class Offline(
        message: String = "网络未连接",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    /**
     * DNS 解析失败。与 [Offline] 的区别是"网通、但解析不出地址"，多半是代理或 DNS 配置问题，
     * 所以这里给"检查网络或代理"才是有效建议。
     */
    class DnsFailure(
        message: String = "连不上服务器，请检查网络或代理",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    /**
     * TLS 握手失败。用户自己能修的最常见原因是系统时间不对（证书被判成未生效或已过期），
     * 所以只留这一条；原来的"网络安全设置"太泛，等于没说。
     */
    class SslFailure(
        message: String = "安全连接失败，请检查系统时间",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    /** 响应无法解析。服务端返回了非预期结构，用户唯一能做的是重试 */
    class SerializationFailure(
        message: String = "数据异常，请稍后重试",
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)

    class Unknown(
        message: String,
        cause: Throwable? = null,
    ) : BgmNetworkException(message, cause)
}

/** 兜底文案：既认不出异常类型、也拿不到可读原文时用。三处出口共用，避免各写一份慢慢漂移 */
private const val UNKNOWN_MESSAGE = "网络异常，请稍后重试"

/** 是否含中日韩统一表意文字。用来区分"我们手写的中文提示"与"第三方库吐出来的英文原文" */
private fun String.containsCjk(): Boolean = any { it.code in 0x4E00..0x9FFF }

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

    val cleanMessage = message?.takeIf { it.isNotBlank() && it != "null" } ?: UNKNOWN_MESSAGE
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
 *
 * 认不出类型的异常（[BgmNetworkException.Unknown]）只在**文案本身是中文**时才原样透传：
 * 中文只可能出自我们自己手写的领域提示（如"已在您的「想看」列表中"），
 * 而英文原文来自第三方库或系统，摆到界面上就是泄漏——这正是本函数存在的理由，
 * 所以用 [containsCjk] 把这条守住，而不是只挡 `java.` / `io.ktor.` 两种前缀。
 *
 * @param actionPrefix 可选动作前缀，如 "获取条目"、"打卡"，会自动格式化为 "${actionPrefix}失败：${message}"。
 */
fun Throwable.toUserFriendlyMessage(actionPrefix: String? = null): String {
    val netEx = if (this is BgmNetworkException) this else this.toBgmNetworkException()
    val rawMessage = netEx.message?.trim().orEmpty()
    val userMessage =
        when {
            netEx !is BgmNetworkException.Unknown && rawMessage.isNotBlank() -> rawMessage
            rawMessage.isBlank() || rawMessage == "null" -> UNKNOWN_MESSAGE
            rawMessage.startsWith("java.") || rawMessage.startsWith("io.ktor.") -> UNKNOWN_MESSAGE
            !rawMessage.containsCjk() -> UNKNOWN_MESSAGE
            else -> rawMessage
        }
    return if (actionPrefix.isNullOrBlank()) {
        userMessage
    } else {
        val prefix = actionPrefix.removeSuffix("失败").removeSuffix("异常")
        "${prefix}失败：$userMessage"
    }
}
