package com.infinitezerone.minibgm.core.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 全局跨组件 Intent 常量与启动意图工厂（如小组件直达页面、通知直达等）
 */
object BgmNavIntents {
    const val EXTRA_OPEN_SCHEDULE = "open_schedule"
    const val EXTRA_SUBJECT_ID = "subject_id"
    const val EXTRA_TRIGGER_LOGIN = "trigger_login"

    fun createScheduleIntent(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_OPEN_SCHEDULE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()

    fun createSubjectIntent(
        context: Context,
        subjectId: Long,
    ): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_SUBJECT_ID, subjectId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()

    fun createLoginIntent(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_TRIGGER_LOGIN, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()

    /**
     * 使用 Custom Tabs 在应用内优雅打开网页；若不可用或失败则安全降级到系统默认浏览器。
     *
     * @param context 启动上下文（若非 Activity 会自动注入 FLAG_ACTIVITY_NEW_TASK）
     * @param url 目标网页地址
     * @param isAuth 是否用于 OAuth 授权登录（若为 true 则启用 Ephemeral 临时会话，不保留 Cookie/缓存）
     */
    fun launchWebUrl(
        context: Context,
        url: String,
        isAuth: Boolean = false,
    ) {
        if (url.isBlank()) return
        val uri = Uri.parse(url)
        try {
            val customTabsIntent =
                CustomTabsIntent
                    .Builder()
                    .apply {
                        setShowTitle(true)
                        if (isAuth) {
                            setEphemeralBrowsingEnabled(true)
                        }
                    }.build()

            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
        } catch (_: Exception) {
            try {
                val fallbackIntent =
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // Ignore if no browser / activity available
            }
        }
    }
}

/**
 * [Context] 扩展快捷调用 [BgmNavIntents.launchWebUrl]。
 */
fun Context.launchWebUrl(
    url: String,
    isAuth: Boolean = false,
) {
    BgmNavIntents.launchWebUrl(this, url, isAuth)
}
