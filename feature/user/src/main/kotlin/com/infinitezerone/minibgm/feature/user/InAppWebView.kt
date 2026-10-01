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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.InAppWebSession
import java.util.concurrent.Executor

/** 内置网页 UA：与登录页保持一致，避免 bgm 网页按老旧内核降级渲染。 */
private const val INAPP_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/**
 * 把会话 Cookie 写入 WebView。
 *
 * 代理只接受携带该令牌的请求，因此必须在加载任何页面之前写入：否则首个请求就被 403。
 * 令牌写在**页面的真实 origin** 上：WebView 经代理发请求时，只有目标域名下的 Cookie 会被带上
 * （令牌随之进入 Cookie 头，代理校验后转发前剥掉，不会泄漏给上游）。
 * HttpOnly 限制 JavaScript 访问，防止页面脚本读取代理会话令牌。
 */
internal fun applyInAppWebSessionCookie(session: InAppWebSession) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setCookie(session.url, "${session.cookieName}=${session.cookieValue}; Path=/; HttpOnly")
    cookieManager.setCookie("http://bgm.tv", "${session.cookieName}=${session.cookieValue}; Domain=.bgm.tv; Path=/; HttpOnly")
    cookieManager.flush()
}

/**
 * 加载经环回代理渲染的页面（代理侧走原生 ECH 通道）。
 *
 * 页面以**真实站点的 http 地址**加载（见 [InAppWebSession.url]），因此凡是指向 bgm 系域名的
 * 请求都由 [forwardToLoopbackProxy] 交给本机环回代理；第三方资源（如 Turnstile）直连。
 * 这既保住了页面 origin（第三方脚本据此校验），又让流量继续走 ECH 通道。
 *
 * @param session 会话：加载地址、环回基址与会话令牌
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
    session: InAppWebSession,
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

    // 让 WebView 的流量经过本机环回代理：这是唯一能让 POST 完整到达代理的途径——
    // WebViewClient 的请求拦截回调不提供请求体，而代理在网络层收包，表单与 AJAX 都不受影响。
    // 页面因此可以保持真实域名（origin 正确、同源无 CORS），代理设置是全局的，离开时必须清除。
    DisposableEffect(session.proxyBaseUrl) {
        applyLoopbackProxy(session.proxyBaseUrl)
        onDispose { clearLoopbackProxy() }
    }

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
                loadUrl(session.url)
            }
        },
        update = { webView ->
            if (webView.url != session.url && session.url.isNotBlank()) {
                webView.loadUrl(session.url)
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

/** 判断当前设备的 WebView 是否支持 PROXY_OVERRIDE 特性。 */
internal fun isProxyOverrideSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

/**
 * 让 WebView 的流量经过本机环回代理。
 *
 * 页面以真实域名加载（origin 必须与站点一致，第三方校验才放行），而 bgm 系域名在该网络下的
 * 系统解析不可用，因此由代理接管、继续走原生 ECH 通道。选择代理而非请求拦截回调，是因为
 * 后者**不提供请求体**——表单与 AJAX 的 POST 都无法转发；代理在网络层收包，天然完整。
 *
 * @return 是否成功应用代理；若设备底层 WebView 不支持则返回 false
 */
internal fun applyLoopbackProxy(proxyBaseUrl: String): Boolean {
    if (!isProxyOverrideSupported()) {
        proxyLogger.w { "[INAPP_PROXY:UNSUPPORTED] PROXY_OVERRIDE is not supported on this device" }
        return false
    }
    val hostPort = proxyBaseUrl.removePrefix("http://").removePrefix("https://")
    return runCatching {
        val config: ProxyConfig =
            ProxyConfig
                .Builder()
                .addProxyRule(hostPort)
                // Turnstile 的挑战跑在 challenges.cloudflare.com 及其子域（如
                // brunhild.challenges.cloudflare.com）。通配写法只匹配子域，apex 必须单独列出，
                // 否则同一套挑战的两个 host 会走两条不同路径——这类"部分生效"最难排查。
                .addBypassRule("challenges.cloudflare.com")
                .addBypassRule("*.challenges.cloudflare.com")
                .build()
        ProxyController.getInstance().setProxyOverride(config, PROXY_EXECUTOR, PROXY_NOOP)
        proxyLogger.i { "[INAPP_PROXY:SET] $hostPort" }
        true
    }.getOrElse { error ->
        proxyLogger.e(error) { "[INAPP_PROXY:FAIL] $hostPort" }
        false
    }
}

/** 清除 WebView 代理设置。该设置是全局的，离开页面必须清理，否则会波及播放捕获用的 WebView。 */
internal fun clearLoopbackProxy() {
    if (isProxyOverrideSupported()) {
        runCatching {
            ProxyController.getInstance().clearProxyOverride(PROXY_EXECUTOR, PROXY_NOOP)
        }
    }
}

/** 代理设置回调的执行器：直接在当前线程执行，不需要额外调度。 */
private val PROXY_EXECUTOR: Executor =
    object : Executor {
        override fun execute(command: Runnable) = command.run()
    }

/** 代理设置完成回调：这里没有收尾动作。 */
private val PROXY_NOOP: Runnable =
    object : Runnable {
        override fun run() = Unit
    }

private val proxyLogger = bgmLogger("Bgm/InAppProxy")
