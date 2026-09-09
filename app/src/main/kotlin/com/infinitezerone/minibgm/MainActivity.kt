package com.infinitezerone.minibgm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.navigation.BgmNavIntents
import com.infinitezerone.minibgm.ui.BgmApp
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val authRepository: AuthRepository by inject()
    private val networkMonitor: NetworkMonitor by inject()
    private val snackbarHostState = SnackbarHostState()

    /** 通知点击携带的"直达时间表"标记，消费后复位 */
    private var openSchedule by mutableStateOf(false)

    /** 小组件条目点击携带的"直达番剧详情"条目 ID，消费后复位 */
    private var openSubjectId by mutableStateOf<Long?>(null)

    /** 小组件点击"去登录"携带的"直达个人中心"标记，消费后复位 */
    private var openUser by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        handleIntent(intent)
        setContent {
            MiniBgmTheme {
                BgmApp(
                    snackbarHostState = snackbarHostState,
                    authRepository = authRepository,
                    networkMonitor = networkMonitor,
                    openSchedule = openSchedule,
                    onScheduleNavigated = { openSchedule = false },
                    openSubjectId = openSubjectId,
                    onSubjectNavigated = { openSubjectId = null },
                    openUser = openUser,
                    onUserNavigated = { openUser = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        handleOAuthIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_OPEN_SCHEDULE, false) == true) {
            openSchedule = true
        }
        val subjectId = intent?.getLongExtra(EXTRA_SUBJECT_ID, -1L) ?: -1L
        if (subjectId > 0L) {
            openSubjectId = subjectId
        }
        if (intent?.getBooleanExtra(EXTRA_TRIGGER_LOGIN, false) == true) {
            openUser = true
            lifecycleScope.launch {
                runCatching {
                    val authorizeUrl = authRepository.beginLogin()
                    CustomTabsIntent
                        .Builder()
                        .setEphemeralBrowsingEnabled(true)
                        .build()
                        .launchUrl(this@MainActivity, Uri.parse(authorizeUrl))
                }.onFailure {
                    snackbarHostState.showSnackbar("启动登录失败，请重试")
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SCHEDULE = BgmNavIntents.EXTRA_OPEN_SCHEDULE
        const val EXTRA_SUBJECT_ID = BgmNavIntents.EXTRA_SUBJECT_ID
        const val EXTRA_TRIGGER_LOGIN = BgmNavIntents.EXTRA_TRIGGER_LOGIN
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
