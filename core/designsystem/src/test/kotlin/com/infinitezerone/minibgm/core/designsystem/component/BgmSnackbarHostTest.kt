package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class BgmSnackbarHostTest {
    @Test
    fun floatingBarClearance_is88Dp() {
        // 手机端居中悬浮胶囊底栏自身高度 68dp + 底部下沉外边距 12dp = 80dp。
        // 加 8dp 安全呼吸间距，合计 88dp，确保顶层页面 Snackbar 无论在手势条还是三大金刚键下均不遮挡。
        assertEquals(88.dp, BgmSnackbarDefaults.FloatingBarClearance)
    }

    @Test
    fun defaultBottomMargin_is12Dp() {
        // 二级详情页与宽屏模式下避让系统手势条/按键导航栏的基准呼吸间距
        assertEquals(12.dp, BgmSnackbarDefaults.DefaultBottomMargin)
    }

    @Test
    fun calculateBottomPadding_topLevelOnCompactPhone_returnsFloatingBarClearance() {
        val padding =
            BgmSnackbarDefaults.calculateBottomPadding(
                isTopLevel = true,
                isWideScreen = false,
            )
        assertEquals(88.dp, padding)
    }

    @Test
    fun calculateBottomPadding_topLevelOnCompactPhoneWithOffset_returnsFloatingBarClearancePlusOffset() {
        val padding =
            BgmSnackbarDefaults.calculateBottomPadding(
                isTopLevel = true,
                isWideScreen = false,
                bottomOffset = 8.dp,
            )
        assertEquals(96.dp, padding)
    }

    @Test
    fun calculateBottomPadding_topLevelOnWideScreenTablet_returnsDefaultBottomMargin() {
        // 平板与大屏模式底栏侧置为 NavigationRail，保留 12dp 基础避让手势条呼吸间距
        val padding =
            BgmSnackbarDefaults.calculateBottomPadding(
                isTopLevel = true,
                isWideScreen = true,
            )
        assertEquals(12.dp, padding)
    }

    @Test
    fun calculateBottomPadding_secondaryDetailScreenOnPhone_returnsDefaultBottomMargin() {
        // 二级详情页（如番剧详情）底栏已下沉隐藏，保留 12dp 基础避让手势条呼吸间距
        val padding =
            BgmSnackbarDefaults.calculateBottomPadding(
                isTopLevel = false,
                isWideScreen = false,
            )
        assertEquals(12.dp, padding)
    }

    @Test
    fun calculateBottomPadding_secondaryDetailScreenWithOffset_returnsDefaultBottomMarginPlusOffset() {
        val padding =
            BgmSnackbarDefaults.calculateBottomPadding(
                isTopLevel = false,
                isWideScreen = false,
                bottomOffset = 8.dp,
            )
        assertEquals(20.dp, padding)
    }

    @Test
    fun calculateBottomPadding_secondaryDetailScreenOnWideScreen_returnsDefaultBottomMarginPlusOffset() {
        val padding =
            BgmSnackbarDefaults.calculateBottomPadding(
                isTopLevel = false,
                isWideScreen = true,
                bottomOffset = 16.dp,
            )
        assertEquals(28.dp, padding)
    }
}
