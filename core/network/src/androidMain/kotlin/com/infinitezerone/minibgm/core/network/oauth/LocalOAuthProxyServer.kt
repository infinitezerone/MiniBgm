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

class LocalOAuthProxyServer(
    private val client: HttpClient,
) : OAuthProxyService {
    private val log = bgmLogger("Bgm/OAuthProxy")
    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private var currentPort: Int? = null

    override val isRunning: Boolean
        get() = serverSocket?.isClosed == false && proxyJob?.isActive == true

    override val port: Int?
        get() = currentPort

    @Synchronized
    override fun start(): Int {
        val existing = serverSocket
        if (existing != null && !existing.isClosed && currentPort != null) {
            return currentPort!!
        }
        stop()
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = server
        val boundPort = server.localPort
        currentPort = boundPort
        log.i { "[PROXY:START] listening on 127.0.0.1:$boundPort" }

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        proxyJob =
            scope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val socket = server.accept()
                        launch { handleSocket(socket, boundPort) }
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
    }

    private suspend fun handleSocket(
        socket: Socket,
        localPort: Int,
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

            val targetUrl = "https://bgm.tv$rawPath"
            log.d { "[PROXY:REQ] $method $rawPath -> $targetUrl" }

            val response =
                client.request(targetUrl) {
                    this.method = HttpMethod.parse(method)
                    inboundHeaders.forEach { (name, value) ->
                        val lower = name.lowercase()
                        if (lower !in EXCLUDED_REQUEST_HEADERS) {
                            if (lower == "referer") {
                                header(name, value.replace("127.0.0.1:$localPort", "bgm.tv"))
                            } else {
                                header(name, value)
                            }
                        }
                    }
                    header(HttpHeaders.Host, "bgm.tv")
                    if (bodyBytes.isNotEmpty()) {
                        setBody(bodyBytes)
                    }
                }

            val status = response.status
            val responseBytes = response.bodyAsBytes()
            val contentType = response.headers[HttpHeaders.ContentType].orEmpty()

            val finalBody =
                if (isTextOrHtml(contentType)) {
                    val text = responseBytes.decodeToString()
                    val replaced =
                        text
                            .replace("https://bgm.tv", "http://127.0.0.1:$localPort")
                            .replace("https://bangumi.tv", "http://127.0.0.1:$localPort")
                    replaced.encodeToByteArray()
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
                                "location" -> {
                                    if (rawVal.startsWith("https://bgm.tv") || rawVal.startsWith("https://bangumi.tv")) {
                                        rawVal
                                            .replace("https://bgm.tv", "http://127.0.0.1:$localPort")
                                            .replace("https://bangumi.tv", "http://127.0.0.1:$localPort")
                                    } else {
                                        rawVal
                                    }
                                }
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
        } finally {
            runCatching { socket.close() }
        }
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
