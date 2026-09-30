package com.infinitezerone.minibgm.core.network.oauth

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID

/** 一次代理会话的不可变上下文：上游主机、会话令牌、环回端口。 */
private data class ProxySession(
    val upstreamHost: String,
    val token: String,
    val port: Int,
)

/** 会话 Cookie 名；值每次启动随机生成，用于把环回端口限制给本应用的 WebView。 */
private const val SESSION_COOKIE_NAME = "minibgm_inapp_web"

/**
 * 上游不可达时的兜底页内容。
 *
 * 刻意不提环回地址、上游域名或 client_id：用户看到的应该是一句人话，而不是实现细节。
 */
private val UPSTREAM_FAILURE_HTML =
    """
    <!doctype html><html lang="zh"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>页面加载失败</title>
    <style>
    body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
    gap:10px;font:15px/1.6 system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
    color:#3c3c43;background:#fff;text-align:center;padding:24px}
    .title{font-size:17px;font-weight:600}
    .hint{color:#8a8a8e;font-size:13px}
    @media (prefers-color-scheme: dark){body{color:#d1d1d6;background:#121212}.hint{color:#8e8e93}}
    </style></head>
    <body>
    <div class="title">页面加载失败</div>
    <div class="hint">请检查网络后重试，或改用系统浏览器打开</div>
    </body></html>
    """.trimIndent()

/**
 * 环回反向代理实现：把 bgm 系页面经原生 ECH 客户端（[HttpClient] 使用 EchHttpClientEngine）
 * 转发给内置 WebView，页面里指向上游域名的绝对链接会被重写成环回地址。
 *
 * 只接受携带本次会话令牌 Cookie 的请求——环回端口对本机其他应用可见，
 * 无令牌会把「以本应用身份访问 bgm.tv」白送出去。
 */
class LocalOAuthProxyServer(
    private val client: HttpClient,
) : OAuthProxyService {
    private val log = bgmLogger("Bgm/OAuthProxy")
    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private var currentPort: Int? = null

    @Volatile
    private var session: ProxySession? = null

    override val isRunning: Boolean
        get() = serverSocket?.isClosed == false && proxyJob?.isActive == true

    override val port: Int?
        get() = currentPort

    @Synchronized
    override fun start(upstreamHost: String): Int {
        val host = upstreamHost.trim().lowercase().ifBlank { BGM_WEB_PROXY_HOST }
        val existing = serverSocket
        if (existing != null && !existing.isClosed && session?.upstreamHost == host && currentPort != null) {
            return currentPort!!
        }
        stop()
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val boundPort = server.localPort
        val active =
            ProxySession(
                upstreamHost = host,
                token = UUID.randomUUID().toString().replace("-", ""),
                port = boundPort,
            )
        serverSocket = server
        currentPort = boundPort
        session = active
        log.i { "[PROXY:START] listening on 127.0.0.1:$boundPort -> https://$host" }

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        proxyJob =
            scope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val socket = server.accept()
                        launch { handleSocket(socket, active) }
                    } catch (e: Exception) {
                        if (!server.isClosed) {
                            log.w { "[PROXY:ACCEPT_ERROR] ${e.message}" }
                        }
                        break
                    }
                }
            }
        return boundPort
    }

    @Synchronized
    override fun stop() {
        if (serverSocket != null) {
            log.i { "[PROXY:STOP] shutting down" }
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
        proxyJob?.cancel()
        proxyJob = null
        currentPort = null
        session = null
    }

    override fun toLoopbackUrl(url: String): String? {
        val active = session ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (!host.equals(active.upstreamHost, ignoreCase = true)) return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "http://127.0.0.1:${active.port}$path$query"
    }

    override fun sessionCookie(): Pair<String, String>? = session?.let { SESSION_COOKIE_NAME to it.token }

    private fun hasValidSessionCookie(
        inboundHeaders: Map<String, String>,
        active: ProxySession,
    ): Boolean {
        val cookie =
            inboundHeaders.entries
                .firstOrNull { it.key.equals(HttpHeaders.Cookie, ignoreCase = true) }
                ?.value
                ?: return false
        return cookie.split(';').any { it.trim() == "$SESSION_COOKIE_NAME=${active.token}" }
    }

    /** 转发前剥掉会话令牌，避免把本应用的代理凭据泄漏给上游。 */
    private fun stripSessionCookie(value: String): String =
        value
            .split(';')
            .map { it.trim() }
            .filterNot { it.startsWith("$SESSION_COOKIE_NAME=") }
            .joinToString("; ")

    private fun writeForbidden(output: BufferedOutputStream) {
        output.write(
            "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.ISO_8859_1),
        )
        output.flush()
    }

    /**
     * 上游不可达时的兜底页。
     *
     * 必须由代理自己回一个响应：否则 WebView 会弹出 Chromium 的原始错误页，把
     * `http://127.0.0.1:<port>/...` 与 client_id 直接摊在用户面前（既难懂又泄露实现细节）。
     */
    private fun writeUpstreamFailure(output: BufferedOutputStream) {
        val body = UPSTREAM_FAILURE_HTML.toByteArray(Charsets.UTF_8)
        val header =
            "HTTP/1.1 502 Bad Gateway\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private suspend fun handleSocket(
        socket: Socket,
        active: ProxySession,
    ) {
        try {
            socket.soTimeout = 15000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]

            val inboundHeaders = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val headerLine = readLine(input) ?: break
                if (headerLine.isEmpty()) break
                val colonIdx = headerLine.indexOf(':')
                if (colonIdx > 0) {
                    val name = headerLine.substring(0, colonIdx).trim()
                    val value = headerLine.substring(colonIdx + 1).trim()
                    inboundHeaders[name] = value
                    if (name.equals(HttpHeaders.ContentLength, ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            if (!hasValidSessionCookie(inboundHeaders, active)) {
                log.w { "[PROXY:REJECT] missing session token for $rawPath" }
                writeForbidden(output)
                return
            }

            val bodyBytes =
                if (contentLength > 0) {
                    val body = ByteArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = input.read(body, readTotal, contentLength - readTotal)
                        if (count < 0) break
                        readTotal += count
                    }
                    body
                } else {
                    ByteArray(0)
                }

            val targetUrl = "https://${active.upstreamHost}$rawPath"
            log.d { "[PROXY:REQ] $method $rawPath -> $targetUrl" }

            val response =
                client.request(targetUrl) {
                    this.method = HttpMethod.parse(method)
                    inboundHeaders.forEach { (name, value) ->
                        val lower = name.lowercase()
                        if (lower in EXCLUDED_REQUEST_HEADERS) return@forEach
                        if (lower == "cookie") {
                            val forwarded = stripSessionCookie(value)
                            if (forwarded.isNotBlank()) header(name, forwarded)
                        } else if (lower == "referer") {
                            header(name, value.replace("http://127.0.0.1:${active.port}", "https://${active.upstreamHost}"))
                        } else {
                            header(name, value)
                        }
                    }
                    header(HttpHeaders.Host, active.upstreamHost)
                    if (bodyBytes.isNotEmpty()) {
                        setBody(bodyBytes)
                    }
                }

            val status = response.status
            val responseBytes = response.bodyAsBytes()
            val contentType = response.headers[HttpHeaders.ContentType].orEmpty()

            val finalBody =
                if (isTextOrHtml(contentType)) {
                    rewriteToLoopback(responseBytes.decodeToString(), active).encodeToByteArray()
                } else {
                    responseBytes
                }

            output.write("HTTP/1.1 ${status.value} ${status.description}\r\n".toByteArray(Charsets.ISO_8859_1))

            response.headers.forEach { name, values ->
                val lower = name.lowercase()
                if (lower !in EXCLUDED_RESPONSE_HEADERS) {
                    values.forEach { rawVal ->
                        val finalVal =
                            when (lower) {
                                "location" -> rewriteToLoopback(rawVal, active)
                                "set-cookie" -> sanitizeSetCookie(rawVal)
                                else -> rawVal
                            }
                        output.write("$name: $finalVal\r\n".toByteArray(Charsets.ISO_8859_1))
                    }
                }
            }
            output.write("Content-Length: ${finalBody.size}\r\n".toByteArray(Charsets.ISO_8859_1))
            output.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            output.write(finalBody)
            output.flush()
        } catch (e: Exception) {
            log.w { "[PROXY:ERROR] ${e.message}" }
            // 失败路径上还没有写过任何响应字节，这里补一个可读的兜底页
            runCatching {
                writeUpstreamFailure(BufferedOutputStream(socket.getOutputStream()))
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * 把上游域名地址改写成环回地址，让 WebView 的后续导航继续留在代理内。
     * 上游为 bgm.tv 时同时覆盖 bangumi.tv / chii.in 别名（旧行为）。
     */
    private fun rewriteToLoopback(
        text: String,
        active: ProxySession,
    ): String {
        var rewritten = text.replace("https://${active.upstreamHost}", "http://127.0.0.1:${active.port}")
        if (active.upstreamHost == BGM_WEB_PROXY_HOST) {
            rewritten =
                rewritten
                    .replace("https://bangumi.tv", "http://127.0.0.1:${active.port}")
                    .replace("https://chii.in", "http://127.0.0.1:${active.port}")
        }
        return rewritten
    }

    private fun readLine(input: BufferedInputStream): String? {
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

    private fun isTextOrHtml(contentType: String): Boolean {
        val lower = contentType.lowercase()
        return lower.contains("text/html") ||
            lower.contains("application/xhtml+xml") ||
            lower.contains("text/javascript") ||
            lower.contains("application/javascript") ||
            lower.contains("text/css")
    }

    private fun sanitizeSetCookie(cookie: String): String =
        cookie
            .replace(Regex("(?i);?\\s*domain=[^;]+"), "")
            .replace(Regex("(?i);?\\s*secure"), "")

    private companion object {
        val EXCLUDED_REQUEST_HEADERS =
            setOf(
                "host",
                "connection",
                "accept-encoding",
                "content-length",
            )
        val EXCLUDED_RESPONSE_HEADERS =
            setOf(
                "connection",
                "transfer-encoding",
                "content-length",
                "content-encoding",
            )
    }
}

actual fun createOAuthProxyService(client: HttpClient): OAuthProxyService = LocalOAuthProxyServer(client)
