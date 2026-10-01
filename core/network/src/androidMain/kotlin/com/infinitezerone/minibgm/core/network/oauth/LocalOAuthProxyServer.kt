package com.infinitezerone.minibgm.core.network.oauth

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
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

    /**
     * 校验请求是否来自本应用的 WebView。
     *
     * 规则（遵循 W3C Fetch Metadata 标准）：
     * 1. 携带合法会话令牌的请求恒放行；
     * 2. 现代 Chromium WebView 发出的子资源请求（图片/脚本/样式/字体等）均带有 `Sec-Fetch-Dest` 头。
     *    当目标并非主文档（Sec-Fetch-Dest != "document"）且为只读安全方法（GET/HEAD）时放行，
     *    彻底解决跨域或子资源未附加 Cookie 导致的页面渲染残缺；
     * 3. 任何主文档页面跳转（document / navigate）或非只读操作（POST/PUT 等）未带令牌一律 403 阻断，
     *    防止外部应用借道进行未授权操作。
     */
    private fun hasValidSessionCookie(
        inboundHeaders: Map<String, String>,
        active: ProxySession,
        method: String,
    ): Boolean {
        val cookie =
            inboundHeaders.entries
                .firstOrNull { it.key.equals(HttpHeaders.Cookie, ignoreCase = true) }
                ?.value
        val hasToken = cookie?.split(';')?.any { it.trim() == "$SESSION_COOKIE_NAME=${active.token}" } == true
        if (hasToken) return true

        val isSafeMethod = method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)
        val fetchDest = inboundHeaders.entries.firstOrNull { it.key.equals("sec-fetch-dest", ignoreCase = true) }?.value
        val isSubResource = fetchDest != null && !fetchDest.equals("document", ignoreCase = true)
        return isSafeMethod && isSubResource
    }

    private fun isAllowedUpstreamHost(
        targetHost: String,
        active: ProxySession,
    ): Boolean {
        val host = targetHost.lowercase()
        if (host == active.upstreamHost.lowercase()) return true
        return ALLOWED_PROXY_DOMAINS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
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

    private fun writePayloadTooLarge(output: BufferedOutputStream) {
        output.write(
            "HTTP/1.1 413 Payload Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.ISO_8859_1),
        )
        output.flush()
    }

    /**
     * 处理 HTTP 代理的 CONNECT：在客户端与目标之间双向转抄字节流。
     *
     * 隧道内的 TLS 由 WebView 自行协商、代理不参与，因此这条路径**不会经过原生 ECH 通道**。
     * 它只用于第三方域名——bgm 系的 https 链接已在 HTML 改写时降级为 http，改走代理转发。
     */
    private suspend fun tunnelRawTcp(
        target: String,
        clientSocket: Socket,
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
    ) {
        val host = target.substringBeforeLast(':', target)
        val port = target.substringAfterLast(':', "443").toIntOrNull() ?: 443
        // 必须带连接超时：目标 IP 被运营商黑洞时，裸 `Socket(host, port)` 会挂到系统默认超时
        // （可达两分钟），WebView 侧表现为页面长时间空白且无法取消。
        val upstream =
            runCatching {
                Socket().apply { connect(InetSocketAddress(host, port), TUNNEL_CONNECT_TIMEOUT_MILLIS) }
            }.getOrNull()
        if (upstream == null) {
            log.w { "[PROXY:TUNNEL_FAIL] $target" }
            clientOutput.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            clientOutput.flush()
            return
        }
        log.d { "[PROXY:TUNNEL] $target" }
        clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        clientOutput.flush()
        // 隧道内不该有读超时：连接建立时设的 15s 只用于"读请求头不被拖死"，
        // 带进隧道会让长连接（Turnstile 的心跳/复用）在空闲 15s 后被我们主动掐断。
        runCatching { clientSocket.soTimeout = 0 }
        runCatching { upstream.soTimeout = 0 }
        try {
            coroutineScope {
                val toUpstream =
                    launch(Dispatchers.IO) {
                        try {
                            pump(clientInput, upstream.getOutputStream())
                        } finally {
                            runCatching { upstream.shutdownOutput() }
                        }
                    }
                val toClient =
                    launch(Dispatchers.IO) {
                        try {
                            pump(upstream.getInputStream(), clientOutput)
                        } finally {
                            runCatching { clientSocket.shutdownOutput() }
                        }
                    }
                // 任意一端断开或异常时，协同取消并结束隧道
                select<Unit> {
                    toUpstream.onJoin {}
                    toClient.onJoin {}
                }
                toUpstream.cancel()
                toClient.cancel()
            }
        } finally {
            runCatching { upstream.close() }
            runCatching { clientSocket.close() }
        }
    }

    /**
     * 隧道内的单向转抄。
     *
     * 不能用 `InputStream.copyTo(OutputStream)`：它不会 flush，而隧道两侧的写端是
     * [BufferedOutputStream]。TLS 握手的前几个报文（ServerHello 等）只有几百字节，
     * 不足缓冲容量就会一直躺在缓冲区里发不出去，表现为浏览器侧 `SSL handshake failed` /
     * `ERR_CONNECTION_CLOSED`（实测在 15s 读超时后才失败）。
     */
    private fun pump(
        from: InputStream,
        to: OutputStream,
    ) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = from.read(buffer)
            if (count < 0) break
            to.write(buffer, 0, count)
            to.flush()
        }
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
            val rawTarget = parts[1]

            // 作为 WebView 的 HTTP 代理使用时，请求行是 absolute-form（`GET http://host/path`）——
            // 这正是选中代理方案的原因：WebView 的拦截回调拿不到 POST body，而走代理时网络层
            // 的完整请求会原样到达这里。直连环回端口的旧形态仍是 origin-form（`GET /path`）。
            val resolved =
                if (rawTarget.startsWith("http://", ignoreCase = true)) {
                    val uri = runCatching { URI(rawTarget) }.getOrNull()
                    val host = uri?.host
                    if (host == null) {
                        writeForbidden(output)
                        return
                    }
                    host to "${uri.rawPath.orEmpty().ifEmpty { "/" }}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
                } else {
                    active.upstreamHost to rawTarget
                }
            val targetHost = resolved.first
            val rawPath = resolved.second

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

            // HTTP 代理的 CONNECT：为 https 建裸 TCP 隧道。WebView 自己完成 TLS，这里不做 MITM
            // （那需要伪造证书）。bgm 系的 https 链接已在改写阶段降级为 http、改走代理转发，
            // 因此走到这里的都是第三方（Turnstile、自动填充等），它们本就依赖系统网络。
            if (method.equals("CONNECT", ignoreCase = true)) {
                tunnelRawTcp(rawTarget, socket, input, output)
                return
            }

            // 限制 targetHost 白名单：防止开放代理
            if (!isAllowedUpstreamHost(targetHost, active)) {
                log.w { "[PROXY:REJECT_HOST] forbidden upstream host: $targetHost" }
                writeForbidden(output)
                return
            }

            if (!hasValidSessionCookie(inboundHeaders, active, method)) {
                log.w { "[PROXY:REJECT] missing or invalid session token for $method $rawPath" }
                writeForbidden(output)
                return
            }

            if (contentLength > MAX_REQUEST_BODY_BYTES) {
                log.w { "[PROXY:BODY_TOO_LARGE] length $contentLength exceeds $MAX_REQUEST_BODY_BYTES" }
                writePayloadTooLarge(output)
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

            val targetUrl = "https://$targetHost$rawPath"
            log.d { "[PROXY:REQ] $method $rawPath -> $targetUrl" }

            // 请求体的 Content-Type 交给 setBody 处理（见下），这里只取出来备用
            val originalContentType =
                inboundHeaders.entries
                    .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                    ?.value
                    ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
            if (bodyBytes.isNotEmpty()) {
                log.d { "[PROXY:BODY] $method $rawPath content-type=${originalContentType ?: "<none>"} bytes=${bodyBytes.size}" }
            }

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
                            // 页面以 http 加载（origin 需求），而上游是 https：还原后再发出去
                            header(name, restoreSecureScheme(value))
                        } else if (lower == "origin") {
                            header(name, restoreSecureScheme(value))
                        } else {
                            header(name, value)
                        }
                    }
                    header(HttpHeaders.Host, targetHost)
                    if (bodyBytes.isNotEmpty()) {
                        // 必须显式带上原始 Content-Type：setBody(ByteArray) 会把它重置为
                        // application/octet-stream，上游会因此拒收（415 Unsupported Media Type）。
                        setBody(ByteArrayContent(bodyBytes, originalContentType ?: ContentType.Application.OctetStream))
                    }
                }

            val status = response.status
            val responseBytes = response.bodyAsBytes()
            val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
            log.d { "[PROXY:RESP] ${status.value} ${status.description} $method $rawPath ct=$contentType" }

            val finalBody =
                if (isTextOrHtml(contentType)) {
                    rewriteSecureLinks(responseBytes.decodeToString()).encodeToByteArray()
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
                                "location" -> rewriteSecureLinks(rawVal)
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
     * 把页面里指向 bgm 系的 **https** 绝对链接降级为 http。
     *
     * 页面本身以 http 加载（origin 必须与站点一致，第三方校验才放行）。若保留 https 链接，
     * WebView 会为它发起 CONNECT 隧道——那条路径既不经过原生 ECH 通道，也绕不过域名封锁。
     * 降级为 http 后，请求会以 absolute-form 交给本机代理，继续走 ECH。
     */
    private fun rewriteSecureLinks(text: String): String = BGM_HTTPS_LINK.replace(text) { match -> "http://${match.groupValues[1]}" }

    /**
     * 把指回 bgm 系的 http 链接还原成 https。
     *
     * 页面为了保住 origin 以 http 加载，而上游是 https——请求头里的 referer/origin 若不还原，
     * 服务端看到的是一个 http 来源，校验可能拒绝。
     */
    private fun restoreSecureScheme(value: String): String = BGM_HTTP_LINK.replace(value) { match -> "https://${match.groupValues[1]}" }

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
            .split(';')
            .map { it.trim() }
            .filterNot { part ->
                part.startsWith("domain=", ignoreCase = true) ||
                    part.equals("secure", ignoreCase = true)
            }.joinToString("; ")

    private companion object {
        /** CONNECT 隧道的建连超时；超出即回 502，把失败暴露给 WebView 而不是静默悬挂。 */
        const val TUNNEL_CONNECT_TIMEOUT_MILLIS = 10_000

        /** 限制代理请求体最大为 10MB，防止恶意大报文消耗内存导致 OOM。 */
        const val MAX_REQUEST_BODY_BYTES = 10 * 1024 * 1024

        /** 代理允许转发的目标主域白名单（含其所有子域名），防止沦为任意目标的开放代理。 */
        val ALLOWED_PROXY_DOMAINS = listOf("bgm.tv", "bangumi.tv", "chii.in")

        val EXCLUDED_REQUEST_HEADERS =
            setOf(
                "host",
                "connection",
                "accept-encoding",
                "content-length",
                // 由 setBody 的 ByteArrayContent 携带，避免被覆盖成 octet-stream
                "content-type",
            )
        val EXCLUDED_RESPONSE_HEADERS =
            setOf(
                "connection",
                "transfer-encoding",
                "content-length",
                "content-encoding",
            )

        /** 匹配指向 bgm 系的 https 绝对链接，分组 1 为完整 host（含子域）。 */
        val BGM_HTTPS_LINK =
            Regex(
                """https://((?:[a-z0-9-]+\.)*(?:bgm\.tv|bangumi\.tv|chii\.in))""",
                RegexOption.IGNORE_CASE,
            )

        /** 匹配指向 bgm 系的 http 绝对链接，用于把请求头还原成上游的 https 形态。 */
        val BGM_HTTP_LINK =
            Regex(
                """http://((?:[a-z0-9-]+\.)*(?:bgm\.tv|bangumi\.tv|chii\.in))""",
                RegexOption.IGNORE_CASE,
            )
    }
}

actual fun createOAuthProxyService(client: HttpClient): OAuthProxyService = LocalOAuthProxyServer(client)
