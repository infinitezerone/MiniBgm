package com.infinitezerone.minibgm.sync.work.reminders

/**
 * 单集开播通知的身份与聚合规则。
 *
 * 每个条目用独立 tag 保留最新一集；通知栏只在至少两条子通知时显示 group summary。
 * 这些规则保持为纯函数，避免 Android 通知细节掩盖身份碰撞问题。
 */
internal object AiringNotificationKeys {
    const val CHILD_ID = 47000
    const val SUMMARY_ID = 4702
    const val SUMMARY_TAG = "airing_summary"
    const val TAG_PREFIX = "airing_subject_"

    fun subjectTag(subjectId: Long): String = "$TAG_PREFIX$subjectId"

    fun shouldShowSummary(childCount: Int): Boolean = childCount >= 2
}
