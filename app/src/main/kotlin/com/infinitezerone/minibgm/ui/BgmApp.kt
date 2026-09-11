package com.infinitezerone.minibgm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.designsystem.theme.ProvideWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.core.navigation.rememberBgmNavState
import com.infinitezerone.minibgm.navigation.BgmNavHost
import com.infinitezerone.minibgm.ui.component.BgmFloatingNavigationBar
import com.infinitezerone.minibgm.ui.component.BgmNavigationRail

@Composable
fun BgmApp(
    snackbarHostState: SnackbarHostState,
    authRepository: AuthRepository,
    networkMonitor: NetworkMonitor,
    openSchedule: Boolean = false,
    onScheduleNavigated: () -> Unit = {},
    openSubjectId: Long? = null,
    onSubjectNavigated: () -> Unit = {},
    openUser: Boolean = false,
    onUserNavigated: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isAuthenticating by authRepository.isAuthenticating.collectAsStateWithLifecycle()
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    var wasOffline by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            wasOffline = true
            snackbarHostState.showSnackbar(
                message = "网络连接已断开，正在浏览本地离线数据",
                duration = SnackbarDuration.Indefinite,
            )
        } else if (wasOffline) {
            wasOffline = false
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "网络已恢复连接",
                duration = SnackbarDuration.Short,
            )
        }
    }

    val navState =
        rememberBgmNavState(
            startRoute = ScheduleRoute,
            topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet(),
        )

    // 通知点击直达"放送"Tab：消费标记后自动切到时间表
    LaunchedEffect(openSchedule) {
        if (openSchedule) {
            navState.navigateTo(ScheduleRoute)
            onScheduleNavigated()
        }
    }

    // 小组件单项点击直达番剧详情页：消费标记后直达对应番剧
    LaunchedEffect(openSubjectId) {
        val subjectId = openSubjectId
        if (subjectId != null && subjectId > 0L) {
            navState.navigateTo(SubjectDetailRoute(subjectId))
            onSubjectNavigated()
        }
    }

    // 小组件点击去登录直达"我的"Tab：消费标记后切到用户中心
    LaunchedEffect(openUser) {
        if (openUser) {
            navState.navigateTo(UserRoute)
            onUserNavigated()
        }
    }

    ProvideWindowAdaptiveInfo {
        val adaptiveInfo = LocalWindowAdaptiveInfo.current
        val isWideScreen = adaptiveInfo.isWide
        val isTopLevel = navState.currentKey in navState.topLevelKeys

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = modifier.fillMaxSize(),
        ) { innerPadding ->
            if (isWideScreen) {
                // 平板与折叠屏大屏：水平 Row 物理占位排版（NavigationRail 占独立宽度，完全不遮挡右侧内容与返回键）
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                ) {
                    BgmNavigationRail(
                        currentDestination = navState.currentTopLevelKey,
                        onDestinationSelected = { route -> navState.navigateTo(route) },
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    ) {
                        BgmNavHost(
                            navState = navState,
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (isAuthenticating) {
                            OAuthProcessingDialog()
                        }
                    }
                }
            } else {
                // 手机端：居中悬浮胶囊底栏（二级页面自动下沉隐藏，返回顶层平滑升起）
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                ) {
                    BgmNavHost(
                        navState = navState,
                        modifier = Modifier.fillMaxSize(),
                    )

                    AnimatedVisibility(
                        visible = isTopLevel,
                        enter =
                            slideInVertically(
                                initialOffsetY = { it * 2 },
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            ) + fadeIn(animationSpec = tween(200)),
                        exit =
                            slideOutVertically(
                                targetOffsetY = { it * 2 },
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            ) + fadeOut(animationSpec = tween(150)),
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 12.dp),
                    ) {
                        BgmFloatingNavigationBar(
                            currentDestination = navState.currentTopLevelKey,
                            onDestinationSelected = { route -> navState.navigateTo(route) },
                            isVertical = false,
                        )
                    }

                    if (isAuthenticating) {
                        OAuthProcessingDialog()
                    }
                }
            }
        }
    }
}
