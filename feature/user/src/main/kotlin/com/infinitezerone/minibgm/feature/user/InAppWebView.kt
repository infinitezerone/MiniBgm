package com.infinitezerone.minibgm.feature.user

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
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
 */
internal fun applyInAppWebSessionCookie(session: InAppWebSession) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setCookie(session.url, "${session.cookieName}=${session.cookieValue}; Path=/")
    cookieManager.flush()
}

/**
 * 加载经环回代理渲染的页面（代理侧走原生 ECH 通道）。
 *
 * @param onInterceptor 命中自定义 scheme（如 `minibgm://` 登录回调）时返回 true 表示已消费
 * @param onWebViewCreated 暴露 WebView 实例，供调用方在离开页面时 destroy
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun InAppWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPageLoadingChanged: (Boolean) -> Unit = {},
    onInterceptor: ((Uri) -> Boolean)? = null,
    onWebViewCreated: (WebView) -> Unit = {},
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = INAPP_WEB_USER_AGENT
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val uri = request?.url ?: return false
                            return onInterceptor?.invoke(uri) ?: false
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            onPageLoadingChanged(true)
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            onPageLoadingChanged(false)
                        }
                    }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        modifier = modifier,
    )
}
