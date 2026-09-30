package com.infinitezerone.minibgm.feature.user

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.infinitezerone.minibgm.core.model.InAppWebSession
import kotlinx.coroutines.launch
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
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<InAppWebSession?>(null) }
    var isStarting by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(title.ifBlank { "Bangumi 网页" }) }
    var currentUrl by remember { mutableStateOf(url) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun handleClose() {
        viewModel.stopSession()
        onBack()
    }

    fun handleBack() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            handleClose()
        }
    }

    fun openInBrowser() {
        val target = resolveUpstreamUrl(currentUrl)
        viewModel.stopSession()
        onOpenInBrowser(target)
    }

    fun copyUrl() {
        val target = resolveUpstreamUrl(currentUrl)
        clipboardManager.setText(AnnotatedString(target))
        scope.launch {
            snackbarHostState.showSnackbar("链接已复制")
        }
    }

    BackHandler(enabled = true) {
        handleBack()
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

    val displayHost = extractDisplayHost(currentUrl)
    val subtitleText = "$displayHost • ECH 安全加密"

    Box(modifier = Modifier.fillMaxSize()) {
        InAppWebScaffold(
            title = pageTitle,
            subtitle = subtitleText,
            progress = progress,
            isBusy = isStarting || isPageLoading,
            canGoBack = canGoBack,
            onNavigateBack = ::handleBack,
            onClose = ::handleClose,
            onOpenInBrowser = ::openInBrowser,
            onCopyUrl = ::copyUrl,
            onReload = { webView?.reload() },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val active = session
                if (active != null) {
                    InAppWebView(
                        url = active.url,
                        modifier = Modifier.fillMaxSize(),
                        onPageLoadingChanged = { loading ->
                            isPageLoading = loading
                            if (!loading) progress = 100
                        },
                        onProgressChanged = { newProgress ->
                            progress = newProgress
                            if (newProgress >= 100) {
                                isPageLoading = false
                            }
                        },
                        onTitleReceived = { newTitle ->
                            if (newTitle.isNotBlank()) {
                                pageTitle = newTitle
                            }
                        },
                        onUrlChanged = { newUrl ->
                            currentUrl = newUrl
                        },
                        onCanGoBackChanged = { backAvailable ->
                            canGoBack = backAvailable
                        },
                        onWebViewCreated = { webView = it },
                    )
                } else if (!isStarting) {
                    InAppWebUnavailable(
                        reason = "该页面无法在应用内打开",
                        onOpenInBrowser = ::openInBrowser,
                        onClose = onBack,
                    )
                }

                InAppWebLoadingPlaceholder(
                    visible = isStarting || isPageLoading,
                    title = if (isStarting) "正在建立 ECH 安全连接…" else "正在载入页面…",
                    subtitle = "$displayHost • 端到端防封锁通道",
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
