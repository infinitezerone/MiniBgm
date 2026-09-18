package com.infinitezerone.minibgm.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingAppLauncherTest {
    @Test
    fun launchResult_modelsAreInstantiable() {
        val inApp = StreamingLaunchResult.LaunchedInApp("哔哩哔哩", "tv.danmaku.bili")
        assertEquals("哔哩哔哩", inApp.appName)
        assertEquals("tv.danmaku.bili", inApp.packageName)

        val notInstalled = StreamingLaunchResult.AppNotInstalled("巴哈姆特動畫瘋", "https://ani.gamer.com.tw")
        assertEquals("巴哈姆特動畫瘋", notInstalled.appName)
        assertEquals("https://ani.gamer.com.tw", notInstalled.webUrl)

        val fallback = StreamingLaunchResult.FallbackWeb("https://example.com")
        assertEquals("https://example.com", fallback.webUrl)
    }

    @Test
    fun launch_withBlankUrl_returnsFallbackWeb() {
        val dummyContext = android.content.ContextWrapper(null)
        val result = StreamingAppLauncher.launch(dummyContext, "")
        org.junit.Assert.assertTrue(result is StreamingLaunchResult.FallbackWeb)
        assertEquals("", (result as StreamingLaunchResult.FallbackWeb).webUrl)
    }

    @Test
    fun isAppInstalled_whenContextThrowsException_returnsFalse() {
        val dummyContext = android.content.ContextWrapper(null)
        val installed = StreamingAppLauncher.isAppInstalled(dummyContext, "tv.danmaku.bili")
        org.junit.Assert.assertFalse(installed)
    }

    @Test
    fun findInstalledPackage_whenNoneInstalled_returnsNull() {
        val dummyContext = android.content.ContextWrapper(null)
        val pkg = StreamingAppLauncher.findInstalledPackage(dummyContext, listOf("tv.danmaku.bili", "com.bilibili.app.in"))
        org.junit.Assert.assertNull(pkg)
    }
}
