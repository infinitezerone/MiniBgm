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
}
