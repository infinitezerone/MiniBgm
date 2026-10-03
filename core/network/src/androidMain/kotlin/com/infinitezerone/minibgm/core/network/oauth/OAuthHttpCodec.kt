package com.infinitezerone.minibgm.core.network.oauth

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * HTTP 报文编解码与报文改写纯逻辑组件。
 *
 * 负责将本地环回代理接收到的原始 InputStream 读写为结构化数据，
 * 以及双向改写 scheme/cookie/header。
 */
internal object OAuthHttpCodec {
    const val MAX_REQUEST_BODY_BYTES: Int = 10 * 1024 * 1024

    val EXCLUDED_REQUEST_HEADERS: Set<String> =
        setOf(
            "host",
            "connection",
            "accept-encoding",
            "content-length",
            // 由 setBody 的 ByteArrayContent 携带，避免被覆盖成 octet-stream
            "content-type",
        )

    val EXCLUDED_RESPONSE_HEADERS: Set<String> =
        setOf(
            "connection",
            "transfer-encoding",
            "content-length",
            "content-encoding",
        )

    /** 匹配指向 bgm 系的 https 绝对链接，分组 1 为完整 host（含子域）。 */
    val BGM_HTTPS_LINK: Regex =
        Regex(
            """https://((?:[a-z0-9-]+\.)*(?:bgm\.tv|bangumi\.tv|chii\.in))""",
            RegexOption.IGNORE_CASE,
        )

    /** 匹配指向 bgm 系的 http 绝对链接，用于把请求头还原成上游的 https 形态。 */
    val BGM_HTTP_LINK: Regex =
        Regex(
            """http://((?:[a-z0-9-]+\.)*(?:bgm\.tv|bangumi\.tv|chii\.in))""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * 单行读取 ISO-8859-1 编码的 HTTP 协议行。
     * 支持 CRLF 或 LF 结尾；到达 EOF 且无数据返回 null。
     */
    fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name())
            }
            if (b == '\n'.code) {
                val str = out.toString(Charsets.ISO_8859_1.name())
                return if (str.endsWith("\r")) str.substring(0, str.length - 1) else str
            }
            out.write(b)
        }
    }

    /**
     * 解析请求行：`METHOD TARGET HTTP/1.1`
     */
    fun parseRequestLine(line: String): Pair<String, String>? {
        val parts = line.trim().split(" ")
        if (parts.size < 2) return null
        return parts[0] to parts[1]
    }

    /**
     * 将请求目标解析为目标主机与相对路径。
     *
     * 代理形态：`GET http://bgm.tv/path?query` -> `bgm.tv` to `/path?query`
     * 原始形态：`GET /path?query` -> `defaultHost` to `/path?query`
     */
    fun resolveTarget(
        rawTarget: String,
        defaultHost: String,
    ): Pair<String, String>? {
        if (!rawTarget.startsWith("http://", ignoreCase = true)) {
            return defaultHost to rawTarget
        }
        val uri = runCatching { URI(rawTarget) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return host to "$path$query"
    }

    /**
     * 从输入流连续解析 HTTP 头部直到空行。
     */
    fun parseHeaders(input: InputStream): Pair<Map<String, String>, Int> {
        val headers = mutableMapOf<String, String>()
        var contentLength = 0
        while (true) {
            val headerLine = readLine(input) ?: break
            if (headerLine.isEmpty()) break
            val colonIdx = headerLine.indexOf(':')
            if (colonIdx > 0) {
                val name = headerLine.substring(0, colonIdx).trim()
                val value = headerLine.substring(colonIdx + 1).trim()
                headers[name] = value
                if (name.equals(HttpHeaders.ContentLength, ignoreCase = true)) {
                    contentLength = value.toIntOrNull() ?: 0
                }
            }
        }
        return headers to contentLength
    }

    /**
     * 读取指定字节数的请求体。
     */
    fun readBody(
        input: InputStream,
        contentLength: Int,
    ): ByteArray {
        if (contentLength <= 0) return ByteArray(0)
        val body = ByteArray(contentLength)
        var readTotal = 0
        while (readTotal < contentLength) {
            val count = input.read(body, readTotal, contentLength - readTotal)
            if (count < 0) break
            readTotal += count
        }
        return body
    }

    /**
     * 把页面里指向 bgm 系的 https 绝对链接降级为 http。
     * 页面以 http 加载，降级为 http 后才能走代理并使用原生 ECH 通道。
     */
    fun rewriteSecureLinks(text: String): String = BGM_HTTPS_LINK.replace(text) { match -> "http://${match.groupValues[1]}" }

    /**
     * 把指回 bgm 系的 http 链接还原成 https，避免上游 referer/origin 校验失败。
     */
    fun restoreSecureScheme(value: String): String = BGM_HTTP_LINK.replace(value) { match -> "https://${match.groupValues[1]}" }

    /**
     * 过滤并转换入站请求头，剥除敏感令牌并还原 referer/origin 的 https 协议。
     */
    fun filterInboundHeaders(headers: Map<String, String>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        headers.forEach { (name, value) ->
            val lower = name.lowercase()
            if (lower in EXCLUDED_REQUEST_HEADERS) return@forEach
            when (lower) {
                "cookie" -> {
                    val forwarded = stripSessionCookie(value, OAuthSecurityGuard.SESSION_COOKIE_NAME)
                    if (forwarded.isNotBlank()) result.add(name to forwarded)
                }
                "referer", "origin" -> {
                    result.add(name to restoreSecureScheme(value))
                }
                else -> {
                    result.add(name to value)
                }
            }
        }
        return result
    }

    /**
     * 过滤出站响应头，重写 location 与消毒 set-cookie。
     */
    fun filterOutboundHeaders(headers: Headers): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        headers.forEach { name, values ->
            val lower = name.lowercase()
            if (lower in EXCLUDED_RESPONSE_HEADERS) return@forEach
            values.forEach { rawVal ->
                val finalVal =
                    when (lower) {
                        "location" -> rewriteSecureLinks(rawVal)
                        "set-cookie" -> sanitizeSetCookie(rawVal)
                        else -> rawVal
                    }
                result.add(name to finalVal)
            }
        }
        return result
    }

    /**
     * 根据 Content-Type 决定是否需要对响应体进行链接重写。
     */
    fun prepareResponseBody(
        bytes: ByteArray,
        contentType: String,
    ): ByteArray =
        if (isTextOrHtml(contentType)) {
            rewriteSecureLinks(bytes.decodeToString()).encodeToByteArray()
        } else {
            bytes
        }

    /**
     * 将 HTTP 状态行、响应头和响应体完整写出到输出流并刷新。
     */
    fun writeResponse(
        output: OutputStream,
        status: HttpStatusCode,
        headers: Headers,
        bodyBytes: ByteArray,
    ) {
        val contentType = headers[HttpHeaders.ContentType].orEmpty()
        val finalBody = prepareResponseBody(bodyBytes, contentType)
        val transformedHeaders = filterOutboundHeaders(headers)

        output.write("HTTP/1.1 ${status.value} ${status.description}\r\n".toByteArray(Charsets.ISO_8859_1))
        transformedHeaders.forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray(Charsets.ISO_8859_1))
        }
        output.write("Content-Length: ${finalBody.size}\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write(finalBody)
        output.flush()
    }

    /**
     * 消毒 Set-Cookie 头：剥除 domain 与 secure 属性，防止本地环回环境下 Cookie 失效。
     */
    fun sanitizeSetCookie(cookie: String): String =
        cookie
            .split(';')
            .map { it.trim() }
            .filterNot { part ->
                part.startsWith("domain=", ignoreCase = true) ||
                    part.equals("secure", ignoreCase = true)
            }.joinToString("; ")

    /**
     * 转发前剥掉会话令牌，避免泄露给上游。
     */
    fun stripSessionCookie(
        value: String,
        sessionCookieName: String,
    ): String =
        value
            .split(';')
            .map { it.trim() }
            .filterNot { it.startsWith("$sessionCookieName=") }
            .joinToString("; ")

    /**
     * 判断响应 Content-Type 是否为需文本替换的 HTML/JS/CSS/JSON。
     */
    fun isTextOrHtml(contentType: String): Boolean {
        val lower = contentType.lowercase()
        return lower.contains("text/html") ||
            lower.contains("application/xhtml+xml") ||
            lower.contains("text/javascript") ||
            lower.contains("application/javascript") ||
            lower.contains("text/css") ||
            lower.contains("application/json") ||
            lower.contains("text/json")
    }

    /**
     * 解析请求体的 ContentType。
     */
    fun parseContentType(headers: Map<String, String>): ContentType? =
        headers.entries
            .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
            ?.value
            ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
}
