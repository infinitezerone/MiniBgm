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
import androidx.compose.ui.res.stringResource
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
    val defaultPageTitle = stringResource(R.string.feature_user_login_title)
    val loginFailedMessage = stringResource(R.string.feature_user_login_failed)
    var session by remember { mutableStateOf<InAppWebSession?>(null) }
    var isStarting by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(defaultPageTitle) }
    var isExchanging by remember { mutableStateOf(false) }
    var isSucceeded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun handleClose() {
        if (!isExchanging && !isSucceeded) {
            viewModel.stopSession()
            onBack()
        }
    }

    fun openInBrowser() {
        scope.launch {
            val url = viewModel.beginBrowserLogin()
            viewModel.stopSession()
            onOpenInBrowser(url)
        }
    }

    BackHandler(enabled = !isExchanging && !isSucceeded) {
        handleClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView = null
        }
    }

    LaunchedEffect(Unit) {
        val started = viewModel.beginLoginSession()
        isStarting = false
        if (started != null) {
            if (!isProxyOverrideSupported()) {
                // 不支持代理覆盖时无法走原生 ECH 环回代理，平滑降级到系统浏览器
                openInBrowser()
                return@LaunchedEffect
            }
            applyInAppWebSessionCookie(started)
            session = started
        }
    }

    if (isSucceeded) {
        LaunchedEffect(Unit) {
            delay(LOGIN_SUCCESS_HOLD_MS)
            viewModel.stopSession()
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        InAppWebScaffold(
            title = pageTitle,
            subtitle = "bgm.tv",
            progress = progress,
            isBusy = isStarting || isPageLoading || isExchanging,
            onClose = ::handleClose,
            onOpenInBrowser = ::openInBrowser,
            onReload = { webView?.reload() },
            closeEnabled = !isExchanging && !isSucceeded,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val active = session
                if (active != null) {
                    InAppWebView(
                        session = active,
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
                            if (newTitle.isNotBlank() && !newTitle.contains("http", ignoreCase = true)) {
                                pageTitle = newTitle
                            }
                        },
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
                                            snackbarHostState.showSnackbar(error ?: loginFailedMessage)
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
                        reason = stringResource(R.string.feature_user_login_unavailable),
                        onOpenInBrowser = ::openInBrowser,
                        onClose = onBack,
                    )
                }

                InAppWebLoadingPlaceholder(
                    visible = (isStarting || isPageLoading) && !isExchanging && !isSucceeded,
                    title = stringResource(R.string.feature_user_login_loading),
                )

                if (isExchanging) {
                    InAppWebBusyOverlay(text = stringResource(R.string.feature_user_login_signing_in))
                }
                if (isSucceeded) {
                    InAppWebBusyOverlay(text = stringResource(R.string.feature_user_login_success), showSpinner = false)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
