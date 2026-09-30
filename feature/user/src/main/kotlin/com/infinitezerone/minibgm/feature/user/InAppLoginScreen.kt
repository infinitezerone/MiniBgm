package com.infinitezerone.minibgm.feature.user

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.infinitezerone.minibgm.core.model.InAppWebSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 登录成功提示的停留时长：既要可感知，又不拖延回到用户原本的页面。 */
private const val LOGIN_SUCCESS_HOLD_MS = 700L

/**
 * 应用内登录接管页（路由化，任意页面都能进入）。
 *
 * 授权页经本地环回代理渲染，代理侧走原生 ECH 通道，因此不需要跳转系统浏览器
 * （Custom Tabs 用的是系统网络栈，用不了 ECH）。环回代理不可用时降级为系统浏览器。
 */
@Composable
internal fun InAppLoginScreen(
    onBack: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    viewModel: InAppWebViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var session by remember { mutableStateOf<InAppWebSession?>(null) }
    var isStarting by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(true) }
    var isExchanging by remember { mutableStateOf(false) }
    var isSucceeded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun openInBrowser() {
        scope.launch {
            val url = viewModel.beginBrowserLogin()
            viewModel.stopSession()
            onOpenInBrowser(url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
            viewModel.stopSession()
        }
    }

    LaunchedEffect(Unit) {
        val started = viewModel.beginLoginSession()
        isStarting = false
        if (started != null) {
            applyInAppWebSessionCookie(started)
            session = started
        }
    }

    if (isSucceeded) {
        LaunchedEffect(Unit) {
            delay(LOGIN_SUCCESS_HOLD_MS)
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        InAppWebScaffold(
            title = "登录 Bangumi",
            subtitle = "无需跳转浏览器",
            isBusy = isStarting || isPageLoading || isExchanging,
            onClose = {
                viewModel.stopSession()
                onBack()
            },
            onOpenInBrowser = ::openInBrowser,
            onReload = { webView?.reload() },
            closeEnabled = !isExchanging && !isSucceeded,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val active = session
                if (active != null) {
                    InAppWebView(
                        url = active.url,
                        modifier = Modifier.fillMaxSize(),
                        onPageLoadingChanged = { isPageLoading = it },
                        onWebViewCreated = { webView = it },
                        onInterceptor = { uri ->
                            if (uri.scheme == "minibgm") {
                                isExchanging = true
                                viewModel.completeLogin(
                                    code = uri.getQueryParameter("code"),
                                    state = uri.getQueryParameter("state"),
                                ) { success, error ->
                                    isExchanging = false
                                    if (success) {
                                        isSucceeded = true
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(error ?: "登录失败，请重试")
                                        }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        },
                    )
                } else if (!isStarting) {
                    InAppWebUnavailable(
                        reason = "当前网络环境无法在应用内完成登录",
                        onOpenInBrowser = ::openInBrowser,
                        onClose = onBack,
                    )
                }

                if (isExchanging) {
                    InAppWebBusyOverlay(text = "正在登录…")
                }
                if (isSucceeded) {
                    InAppWebBusyOverlay(text = "登录成功", showSpinner = false)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
