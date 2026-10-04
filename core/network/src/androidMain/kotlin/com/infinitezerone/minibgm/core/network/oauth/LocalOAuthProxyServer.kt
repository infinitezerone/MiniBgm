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
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID

/** 一次代理会话的不可变上下文：上游主机、会话令牌、环回端口。 */
internal data class ProxySession(
    val upstreamHost: String,
    val token: String,
    val port: Int,
)

/**
 * 上游不可达时的兜底页内容。
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
internal class LocalOAuthProxyServer(
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

    override fun sessionCookie(): Pair<String, String>? = session?.let { OAuthSecurityGuard.SESSION_COOKIE_NAME to it.token }

    private suspend fun handleSocket(
        socket: Socket,
        active: ProxySession,
    ) {
        try {
            socket.soTimeout = 15000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = OAuthHttpCodec.readLine(input) ?: return
            val (method, rawTarget) = OAuthHttpCodec.parseRequestLine(requestLine) ?: return
            val (inboundHeaders, contentLength) = OAuthHttpCodec.parseHeaders(input)

            if (method.equals("CONNECT", ignoreCase = true)) {
                if (!OAuthSecurityGuard.isAllowedConnectHost(rawTarget, active.upstreamHost)) {
                    log.w { "[PROXY:REJECT_CONNECT] forbidden CONNECT target: $rawTarget" }
                    writeForbidden(output)
                    return
                }
                tunnelRawTcp(rawTarget, socket, input, output)
                return
            }

            val (targetHost, rawPath) =
                OAuthHttpCodec.resolveTarget(rawTarget, active.upstreamHost) ?: run {
                    writeForbidden(output)
                    return
                }

            if (!OAuthSecurityGuard.isAllowedUpstreamHost(targetHost, active.upstreamHost)) {
                log.w { "[PROXY:REJECT_HOST] forbidden upstream host: $targetHost" }
                writeForbidden(output)
                return
            }

            if (!OAuthSecurityGuard.hasValidSession(inboundHeaders, active.token, method)) {
                log.w { "[PROXY:REJECT] missing or invalid session token for $method $rawPath" }
                writeForbidden(output)
                return
            }

            if (contentLength > OAuthHttpCodec.MAX_REQUEST_BODY_BYTES) {
                log.w { "[PROXY:BODY_TOO_LARGE] length $contentLength exceeds ${OAuthHttpCodec.MAX_REQUEST_BODY_BYTES}" }
                writePayloadTooLarge(output)
                return
            }

            val bodyBytes = OAuthHttpCodec.readBody(input, contentLength)
            forwardUpstream(method, targetHost, rawPath, inboundHeaders, bodyBytes, output)
        } catch (e: Exception) {
            log.w { "[PROXY:ERROR] ${e.message}" }
            runCatching {
                writeUpstreamFailure(BufferedOutputStream(socket.getOutputStream()))
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun forwardUpstream(
        method: String,
        targetHost: String,
        rawPath: String,
        inboundHeaders: Map<String, String>,
        bodyBytes: ByteArray,
        output: BufferedOutputStream,
    ) {
        val targetUrl = "https://$targetHost$rawPath"
        log.d { "[PROXY:REQ] $method $rawPath -> $targetUrl" }

        val originalContentType = OAuthHttpCodec.parseContentType(inboundHeaders)
        val forwardedHeaders = OAuthHttpCodec.filterInboundHeaders(inboundHeaders)

        val response =
            client.request(targetUrl) {
                this.method = HttpMethod.parse(method)
                forwardedHeaders.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Host, targetHost)
                if (bodyBytes.isNotEmpty()) {
                    setBody(ByteArrayContent(bodyBytes, originalContentType ?: ContentType.Application.OctetStream))
                }
            }

        OAuthHttpCodec.writeResponse(output, response.status, response.headers, response.bodyAsBytes())
    }

    private suspend fun tunnelRawTcp(
        target: String,
        clientSocket: Socket,
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
    ) {
        val host = target.substringBeforeLast(':', target)
        val port = target.substringAfterLast(':', "443").toIntOrNull() ?: 443
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

    private fun writeForbidden(output: BufferedOutputStream) {
        output.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    private fun writePayloadTooLarge(output: BufferedOutputStream) {
        output.write("HTTP/1.1 413 Payload Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

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

    private companion object {
        const val TUNNEL_CONNECT_TIMEOUT_MILLIS = 10_000
    }
}

actual fun createOAuthProxyService(client: HttpClient): OAuthProxyService = LocalOAuthProxyServer(client)
