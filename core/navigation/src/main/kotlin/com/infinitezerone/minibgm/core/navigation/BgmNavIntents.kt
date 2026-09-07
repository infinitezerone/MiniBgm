package com.infinitezerone.minibgm.core.navigation

import android.content.Context
import android.content.Intent

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
}
