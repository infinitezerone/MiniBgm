package com.infinitezerone.minibgm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.ui.BgmApp
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val authRepository: AuthRepository by inject()
    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        handleOAuthIntent(intent)
        setContent {
            MiniBgmTheme {
                BgmApp(
                    snackbarHostState = snackbarHostState,
                    authRepository = authRepository,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "minibgm" && data.host == "oauth" && data.path == "/callback") {
            val error = data.getQueryParameter("error")
            if (error != null) {
                lifecycleScope.launch {
                    val message = if (error == "access_denied") "已取消授权登录" else "授权失败：$error"
                    snackbarHostState.showSnackbar(message)
                }
                return
            }
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            // 深链回调是 app 级事件（不依赖任何页面存活），登录结果经全局 Snackbar 反馈
            lifecycleScope.launch {
                authRepository
                    .completeLogin(code, state)
                    .onSuccess { snackbarHostState.showSnackbar("登录成功 🎉") }
                    .onError { _, message -> snackbarHostState.showSnackbar(message) }
            }
        }
    }
}
