package com.infinitezerone.minibgm.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.TopLevelDestination
import com.infinitezerone.minibgm.core.navigation.rememberBgmNavState
import com.infinitezerone.minibgm.navigation.BgmNavHost

@Composable
fun BgmApp(
    snackbarHostState: SnackbarHostState,
    authRepository: AuthRepository,
    openSchedule: Boolean = false,
    onScheduleNavigated: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isAuthenticating by authRepository.isAuthenticating.collectAsStateWithLifecycle()

    val navState =
        rememberBgmNavState(
            startRoute = ScheduleRoute,
            topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet(),
        )

    // 通知点击直达"放送"Tab：消费标记后自动切到时间表
    androidx.compose.runtime.LaunchedEffect(openSchedule) {
        if (openSchedule) {
            navState.navigateTo(ScheduleRoute)
            onScheduleNavigated()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 仅当前可见目的地是顶层 Tab 根部时显示底部导航栏，进入二级页面时隐藏
            if (navState.currentKey in navState.topLevelKeys) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = destination.route == navState.currentTopLevelKey

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navState.navigateTo(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.labelText,
                                )
                            },
                            label = { Text(text = destination.labelText) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        BgmNavHost(
            navState = navState,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        )

        if (isAuthenticating) {
            OAuthProcessingDialog()
        }
    }
}
