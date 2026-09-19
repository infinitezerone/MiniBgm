package com.infinitezerone.minibgm.core.navigation

import com.infinitezerone.minibgm.core.common.intent.ExternalPlayerTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun launchExternalPlayer_whenPlayerNotInstalled_returnsFalseAndInvokesCallback() {
        val dummyContext = android.content.ContextWrapper(null)
        val target = ExternalPlayerTarget("mpv", "is.xyz.mpv", "https://example.com/ep1.m3u8")
        var callbackAppName: String? = null
        var callbackPackageName: String? = null

        val launched =
            StreamingAppLauncher.launchExternalPlayer(dummyContext, target) { appName, packageName ->
                callbackAppName = appName
                callbackPackageName = packageName
            }

        assertFalse(launched)
        assertEquals("mpv", callbackAppName)
        assertEquals("is.xyz.mpv", callbackPackageName)
    }

    @Test
    fun launchExternalPlayer_withBlankVideoUrl_returnsFalseWithoutCallback() {
        val dummyContext = android.content.ContextWrapper(null)
        var callbackInvoked = false

        val launched =
            StreamingAppLauncher.launchExternalPlayer(
                dummyContext,
                ExternalPlayerTarget("VLC", "org.videolan.vlc", "   "),
            ) { _, _ ->
                callbackInvoked = true
            }

        assertFalse(launched)
        assertFalse(callbackInvoked)
    }

    @Test
    fun externalPlayerTarget_describesActionViewIntent() {
        val target =
            ExternalPlayerTarget(
                appName = "VLC",
                packageName = "org.videolan.vlc",
                videoUrl = "https://example.com/ep1.mp4",
            )

        assertEquals("android.intent.action.VIEW", target.action)
        assertEquals("video/*", target.mimeType)
        assertEquals("https://example.com/ep1.mp4", target.videoUrl)
        assertEquals("org.videolan.vlc", target.packageName)
    }
}
