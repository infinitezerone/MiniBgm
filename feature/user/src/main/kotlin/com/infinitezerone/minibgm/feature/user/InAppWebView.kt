package com.infinitezerone.minibgm.feature.user

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.infinitezerone.minibgm.core.model.InAppWebSession

/** 内置网页 UA：与登录页保持一致，避免 bgm 网页按老旧内核降级渲染。 */
private const val INAPP_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/**
 * 把会话 Cookie 写入 WebView。
 *
 * 环回代理只接受携带该令牌的请求，因此必须在加载任何页面之前写入：否则首个请求就被 403。
 * Cookie 按 host 归属（与端口无关），同名 Cookie 会被覆盖，不会在多轮会话间堆积。
 * 附带 HttpOnly 限制 JavaScript 访问，防止页面脚本读取代理会话令牌。
 */
internal fun applyInAppWebSessionCookie(session: InAppWebSession) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setCookie(session.url, "${session.cookieName}=${session.cookieValue}; Path=/; HttpOnly")
    cookieManager.flush()
}

/**
 * 加载经环回代理渲染的页面（代理侧走原生 ECH 通道）。
 *
 * @param onPageLoadingChanged 页面开始/结束加载状态回调
 * @param onProgressChanged 网页加载进度百分比（0..100）
 * @param onTitleReceived 提取到的网页真实标题
 * @param onUrlChanged 网页发生内部跳转时的实际地址更新
 * @param onCanGoBackChanged 网页历史记录是否支持后退
 * @param onInterceptor 命中自定义 scheme（如 `minibgm://` 登录回调）时返回 true 表示已消费
 * @param onWebViewCreated 暴露 WebView 实例，供调用方手动触发刷新/后退
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun InAppWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPageLoadingChanged: (Boolean) -> Unit = {},
    onProgressChanged: (Int) -> Unit = {},
    onTitleReceived: (String) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onCanGoBackChanged: (Boolean) -> Unit = {},
    onInterceptor: ((Uri) -> Boolean)? = null,
    onWebViewCreated: (WebView) -> Unit = {},
) {
    val currentOnPageLoadingChanged by rememberUpdatedState(onPageLoadingChanged)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTitleReceived by rememberUpdatedState(onTitleReceived)
    val currentOnUrlChanged by rememberUpdatedState(onUrlChanged)
    val currentOnCanGoBackChanged by rememberUpdatedState(onCanGoBackChanged)
    val currentOnInterceptor by rememberUpdatedState(onInterceptor)
    val currentOnWebViewCreated by rememberUpdatedState(onWebViewCreated)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = INAPP_WEB_USER_AGENT
                }
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onProgressChanged(
                            view: WebView?,
                            newProgress: Int,
                        ) {
                            currentOnProgressChanged(newProgress)
                        }

                        override fun onReceivedTitle(
                            view: WebView?,
                            title: String?,
                        ) {
                            super.onReceivedTitle(view, title)
                            if (!title.isNullOrBlank()) {
                                currentOnTitleReceived(title)
                            }
                        }

                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?,
                        ): Boolean {
                            val ctx = view?.context ?: return false
                            runCatching {
                                android.app.AlertDialog
                                    .Builder(ctx)
                                    .setMessage(message)
                                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                    .setOnCancelListener { result?.cancel() }
                                    .show()
                            }.onFailure { result?.cancel() }
                            return true
                        }

                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?,
                        ): Boolean {
                            val ctx = view?.context ?: return false
                            runCatching {
                                android.app.AlertDialog
                                    .Builder(ctx)
                                    .setMessage(message)
                                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                                    .setOnCancelListener { result?.cancel() }
                                    .show()
                            }.onFailure { result?.cancel() }
                            return true
                        }
                    }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val uri = request?.url ?: return false
                            return currentOnInterceptor?.invoke(uri) ?: false
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            currentOnPageLoadingChanged(true)
                            url?.let(currentOnUrlChanged)
                            currentOnCanGoBackChanged(view?.canGoBack() == true)
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            currentOnPageLoadingChanged(false)
                            url?.let(currentOnUrlChanged)
                            currentOnCanGoBackChanged(view?.canGoBack() == true)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                currentOnPageLoadingChanged(false)
                            }
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let(currentOnUrlChanged)
                            currentOnCanGoBackChanged(view?.canGoBack() == true)
                        }
                    }
                currentOnWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url && url.isNotBlank()) {
                webView.loadUrl(url)
            }
        },
        onRelease = { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.clearHistory()
            webView.destroy()
        },
        modifier = modifier,
    )
}
