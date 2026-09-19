package com.infinitezerone.minibgm.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.WebViewCaptureService
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 单次 WebView 深度解析的硬预算：超时即返回已捕获内容 */
private const val SESSION_BUDGET_MS = 30_000L

/** 页面加载完成后再等待迟发媒体请求的宽限 */
private const val AFTER_FINISH_GRACE_MS = 6_000L

/** 最多采纳的候选媒体请求数（与正则档一致） */
private const val MAX_CANDIDATES = 12

private val MEDIA_REQUEST_URL =
    Regex("""https?://[^\s"'()<>]+?\.(?:m3u8|mp4|mkv|flv|webm|ts)(?:\?[^\s"'()<>]*)?""", RegexOption.IGNORE_CASE)

/**
 * WebView 深度解析会话（第 5 档：确定性运行时捕获，无 AI 参与）。
 *
 * 沙箱纪律：专用 WebView 实例、会话开始清空全部 Cookie（App 内无其他 WebView 且从不在此登录，
 * 构造上保证零登录态）、禁文件/内容访问、不注册任何 JS bridge、单会话、调用方显式触发。
 * 播放地址的真值只来自网络捕获（shouldInterceptRequest），页面文本中的地址一律不采信。
 */
class WebViewCaptureServiceImpl(
    private val context: Context,
) : WebViewCaptureService {
    private val logger = bgmLogger("Bgm/WebViewCapture")
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun capturePlayableSources(pageUrl: String): List<PlayableSource> =
        suspendCancellableCoroutine { continuation ->
            val captured = LinkedHashMap<String, Map<String, String>>()
            var webview: WebView? = null
            var settled = false

            fun settle() {
                if (settled) return
                settled = true
                val sources =
                    runCatching {
                        captured.entries.take(MAX_CANDIDATES).map { (url, headers) ->
                            PlayableSource(
                                url = url,
                                kind = PlaylistEntryKind.DIRECT,
                                label = "",
                                episodeSort = 0f,
                                siteName = hostOf(pageUrl),
                                pageUrl = pageUrl,
                                // 播放头直接取自捕获到的媒体请求自身（Referer/UA），比反推可靠
                                headers = headers,
                            )
                        }
                    }.getOrElse {
                        logger.w { "捕获结果映射异常（按空处理）: ${it.message}" }
                        emptyList()
                    }
                mainHandler.post {
                    webview?.destroy()
                    webview = null
                    if (continuation.isActive) continuation.resume(sources)
                }
            }

            fun record(request: WebResourceRequest) {
                // WebView 内部线程上的回调：任何异常都会炸进程，全部吞掉——
                // 深度解析是可选兜底档，失败就是"没有结果"
                runCatching {
                    val url = request.url.toString()
                    if (!MEDIA_REQUEST_URL.matches(url)) return
                    synchronized(captured) {
                        if (captured.containsKey(url)) return@synchronized
                        val headers =
                            buildMap {
                                request.requestHeaders["Referer"]?.let { put("Referer", it) }
                                request.requestHeaders["User-Agent"]?.let { put("User-Agent", it) }
                                request.requestHeaders["Origin"]?.let { put("Origin", it) }
                            }
                        captured[url] = headers
                        logger.d { "捕获媒体请求 ${request.requestHeaders["Referer"]?.let { "（带 Referer）" } ?: ""}: $url" }
                    }
                }.onFailure { e -> logger.w { "捕获回调异常（已忽略）: ${e.message}" } }
            }

            mainHandler.post {
                runCatching {
                    // 零登录态：会话开始清空全部 Cookie（本进程唯一 WebView，且从不在此登录）
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()

                    webview =
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.blockNetworkImage = true
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): android.webkit.WebResourceResponse? {
                                        record(request)
                                        return null
                                    }

                                    override fun onPageFinished(
                                        view: WebView,
                                        finishedUrl: String,
                                    ) {
                                        mainHandler.postDelayed({ settle() }, AFTER_FINISH_GRACE_MS)
                                    }
                                }
                            loadUrl(pageUrl)
                        }
                }.onFailure { e ->
                    logger.w { "WebView 会话创建失败 $pageUrl: ${e.message}" }
                    settle()
                }
            }
            mainHandler.postDelayed({ settle() }, SESSION_BUDGET_MS)
            continuation.invokeOnCancellation {
                mainHandler.post {
                    webview?.destroy()
                    webview = null
                }
            }
        }

    private fun hostOf(url: String): String = url.substringAfter("://", url).substringBefore('/').substringBefore('?')
}
