package com.infinitezerone.minibgm.feature.user

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.infinitezerone.minibgm.core.model.InAppWebSession
import org.koin.androidx.compose.koinViewModel

/**
 * 应用内网页浏览页：把 bgm 系域名页面经环回代理渲染，复用原生 ECH 通道。
 *
 * 与登录页分开是因为它不做回调截获与换票：页面里的一切导航都留在 WebView 内。
 * 代理不可用（非 bgm 域名或启动失败）时降级为系统浏览器。
 */
@Composable
internal fun InAppWebScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    viewModel: InAppWebViewModel = koinViewModel(),
) {
    var session by remember { mutableStateOf<InAppWebSession?>(null) }
    var isStarting by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun openInBrowser() {
        viewModel.stopSession()
        onOpenInBrowser(url)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
            viewModel.stopSession()
        }
    }

    LaunchedEffect(url) {
        val started = viewModel.beginBrowseSession(url)
        isStarting = false
        if (started != null) {
            applyInAppWebSessionCookie(started)
            session = started
        }
    }

    InAppWebScaffold(
        title = title.ifBlank { "Bangumi 网页" },
        subtitle = null,
        isBusy = isStarting || isPageLoading,
        onClose = {
            viewModel.stopSession()
            onBack()
        },
        onOpenInBrowser = ::openInBrowser,
        onReload = { webView?.reload() },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val active = session
            if (active != null) {
                InAppWebView(
                    url = active.url,
                    modifier = Modifier.fillMaxSize(),
                    onPageLoadingChanged = { isPageLoading = it },
                    onWebViewCreated = { webView = it },
                )
            } else if (!isStarting) {
                InAppWebUnavailable(
                    reason = "该页面无法在应用内打开",
                    onOpenInBrowser = ::openInBrowser,
                    onClose = onBack,
                )
            }
        }
    }
}
