package com.infinitezerone.minibgm.sync.work.reminders

import com.infinitezerone.minibgm.core.model.UpcomingAiring

/**
 * 开播提醒的纯决策逻辑：给定偏好与数据，决定本次要提醒哪些更新。
 * 独立于 Android 通知 API，保持可测；时间窗口过滤由仓库层负责。
 */
object AiringReminderPlanner {
    fun plan(
        enabled: Boolean,
        isLoggedIn: Boolean,
        lastNotifiedDate: String,
        today: String,
        currentHour: Int,
        reminderHour: Int,
        upcoming: List<UpcomingAiring>,
    ): List<UpcomingAiring> {
        if (!enabled) return emptyList()
        if (!isLoggedIn) return emptyList()
        // 未到用户设定的提醒时刻（Worker 每小时触发，早于设定时刻的触发静默跳过）
        if (currentHour < reminderHour) return emptyList()
        // 每日去重：同一天只提醒一次
        if (lastNotifiedDate == today) return emptyList()
        return upcoming
    }
}
