package com.infinitezerone.minibgm.sync.work.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.core.navigation.BgmNavIntents
import com.infinitezerone.minibgm.sync.work.R

/**
 * 开播提醒通知的 Android 侧实现：频道管理、权限检查与通知展示。
 * 逻辑判定在 [AiringReminderPlanner]，调度由 [AiringAlarmScheduler] 管理，本类负责构建与展示通知。
 */
class AiringReminderNotifier(
    private val context: Context,
) {
    fun notificationsEnabled(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        val permissionGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        return permissionGranted && preAirChannelEnabled()
    }

    private fun preAirChannelEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return true
        return notificationManager.getNotificationChannel(PRE_AIR_CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }

    /**
     * 单集临近/刚开播提醒。
     * 每个条目用独立 tag 保留最新一集；子通知进入同一 group，并在至少两条时显示 summary。
     */
    fun notifyImminent(
        upcoming: List<UpcomingAiring>,
        isAlreadyStarted: Boolean = false,
    ) {
        if (upcoming.isEmpty()) return
        createNotificationChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!notificationsEnabled()) {
            refreshPreAirSummary()
            return
        }

        for (item in upcoming) {
            val isFinale = item.isFinale
            val contentTitle =
                if (isFinale) {
                    context.getString(R.string.airing_finale_title, item.displayName)
                } else if (isAlreadyStarted) {
                    context.getString(R.string.airing_pre_air_title_started)
                } else {
                    context.getString(R.string.airing_pre_air_title)
                }
            val itemText =
                if (isFinale) {
                    context.getString(R.string.airing_finale_item, item.episode, item.totalEpisodes)
                } else if (isAlreadyStarted) {
                    context.getString(R.string.airing_pre_air_item_started, item.displayName, item.episode)
                } else {
                    context.getString(R.string.airing_pre_air_item, item.displayName, item.episode)
                }

            val notification =
                NotificationCompat
                    .Builder(context, PRE_AIR_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_airing)
                    .setContentTitle(contentTitle)
                    .setContentText(itemText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(itemText))
                    .setContentIntent(launchSubjectIntent(item.subjectId))
                    .setDeleteIntent(launchDismissIntent(item.subjectId))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(VIBRATION_PATTERN)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setGroup(GROUP_ID)
                    .setTimeoutAfter(PRE_AIR_NOTIFICATION_TTL_MS)
                    .setAutoCancel(true)
                    .build()

            post(
                manager = manager,
                tag = AiringNotificationKeys.subjectTag(item.subjectId),
                id = AiringNotificationKeys.CHILD_ID,
                notification = notification,
            )
        }

        refreshPreAirSummary()
    }

    /**
     * 以当前通知栏中的子通知为准刷新聚合头。
     * 0/1 条不显示 summary；2 条及以上显示，内容只做展示，不重新查业务数据。
     */
    fun refreshPreAirSummary() {
        val manager = NotificationManagerCompat.from(context)
        if (!notificationsEnabled()) {
            manager.cancel(AiringNotificationKeys.SUMMARY_TAG, AiringNotificationKeys.SUMMARY_ID)
            return
        }

        val active =
            runCatching { manager.activeNotifications }
                .getOrNull()
                ?: return
        val children =
            active
                .filter { it.tag?.startsWith(AiringNotificationKeys.TAG_PREFIX) == true }
                .sortedByDescending { it.postTime }

        if (!AiringNotificationKeys.shouldShowSummary(children.size)) {
            manager.cancel(AiringNotificationKeys.SUMMARY_TAG, AiringNotificationKeys.SUMMARY_ID)
            return
        }

        val title = context.getString(R.string.airing_pre_air_summary_title, children.size)
        val lines =
            children
                .take(MAX_SUMMARY_LINES)
                .mapNotNull { child ->
                    child.notification.extras
                        .getCharSequence(Notification.EXTRA_TEXT)
                        ?.toString()
                        ?.takeIf(String::isNotBlank)
                }
        val style = NotificationCompat.InboxStyle()
        style.setBigContentTitle(title)
        lines.forEach(style::addLine)
        if (children.size > MAX_SUMMARY_LINES) {
            style.addLine(
                context.getString(
                    R.string.airing_pre_air_summary_more,
                    children.size - MAX_SUMMARY_LINES,
                ),
            )
        }

        val summary =
            NotificationCompat
                .Builder(context, PRE_AIR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_airing)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.airing_pre_air_summary_text))
                .setStyle(style)
                .setContentIntent(launchScheduleIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setGroup(GROUP_ID)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setSilent(true)
                .setTimeoutAfter(PRE_AIR_NOTIFICATION_TTL_MS)
                .setAutoCancel(false)
                .build()

        post(
            manager = manager,
            tag = AiringNotificationKeys.SUMMARY_TAG,
            id = AiringNotificationKeys.SUMMARY_ID,
            notification = summary,
        )
    }

    private fun post(
        manager: NotificationManagerCompat,
        tag: String,
        id: Int,
        notification: android.app.Notification,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(tag, id, notification)
        }
    }

    private fun launchSubjectIntent(subjectId: Long): PendingIntent? {
        val launch =
            BgmNavIntents.createSubjectIntent(context, subjectId).apply {
                data = Uri.parse("minibgm://subject/$subjectId")
            }
        if (launch.component == null && launch.action == null) return null
        return PendingIntent.getActivity(
            context,
            AiringNotificationKeys.CHILD_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun launchDismissIntent(subjectId: Long): PendingIntent? {
        val dismiss =
            Intent(context, AiringAlarmReceiver::class.java).apply {
                action = ACTION_PRE_AIR_NOTIFICATION_DISMISSED
                data = Uri.parse("minibgm://subject/$subjectId/dismiss")
            }
        return PendingIntent.getBroadcast(
            context,
            AiringNotificationKeys.CHILD_ID,
            dismiss,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun launchScheduleIntent(): PendingIntent? {
        val launch =
            BgmNavIntents.createScheduleIntent(context).apply {
                data = Uri.parse("minibgm://schedule")
            }
        if (launch.component == null && launch.action == null) return null
        return PendingIntent.getActivity(
            context,
            AiringNotificationKeys.SUMMARY_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val GROUP_ID = "group_airing"
        const val PRE_AIR_CHANNEL_ID = "airing_pre_air"
        const val ACTION_PRE_AIR_NOTIFICATION_DISMISSED =
            "com.infinitezerone.minibgm.sync.work.action.AIRING_PRE_AIR_DISMISSED"

        private const val MAX_SUMMARY_LINES = 5
        private const val PRE_AIR_NOTIFICATION_TTL_MS = 12L * 60L * 60L * 1000L
        private val VIBRATION_PATTERN = longArrayOf(0, 250, 250, 250)
        private const val LEGACY_DAILY_CHANNEL_ID = "airing_reminders"
        private const val LEGACY_DAILY_NOTIFICATION_ID = 4701

        /**
         * 预注册所有通知渠道与分组（在 App 启动时调用，解决系统设置中看不到子渠道的懒加载问题）。
         */
        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = NotificationManagerCompat.from(context)
            context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(LEGACY_DAILY_CHANNEL_ID)
            manager.cancel(LEGACY_DAILY_NOTIFICATION_ID)

            // 1. 注册渠道组
            manager.createNotificationChannelGroup(
                NotificationChannelGroup(
                    GROUP_ID,
                    context.getString(R.string.airing_channel_group_name),
                ),
            )

            // 2. 单集即时开播提醒渠道 (HIGH, 带振动与锁屏公开)
            val preAirChannel =
                NotificationChannel(
                    PRE_AIR_CHANNEL_ID,
                    context.getString(R.string.airing_pre_air_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    group = GROUP_ID
                    description = context.getString(R.string.airing_pre_air_channel_desc)
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            manager.createNotificationChannel(preAirChannel)
        }
    }
}
