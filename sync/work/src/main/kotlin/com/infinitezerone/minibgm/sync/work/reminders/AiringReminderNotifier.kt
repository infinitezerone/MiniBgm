package com.infinitezerone.minibgm.sync.work.reminders

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** 每日汇总通知（固定 ID，点击跳转时刻表） */
    fun notify(upcoming: List<UpcomingAiring>) {
        if (upcoming.isEmpty()) return
        createNotificationChannels(context)
        val manager = NotificationManagerCompat.from(context)

        val listText =
            upcoming.joinToString("\n") { item ->
                context.getString(R.string.airing_reminder_item, item.displayName, item.episode)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_airing)
                .setContentTitle(context.getString(R.string.airing_reminder_title, upcoming.size))
                .setStyle(NotificationCompat.BigTextStyle().bigText(listText))
                .setContentIntent(launchScheduleIntent())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(VIBRATION_PATTERN)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()

        post(manager, NOTIFICATION_ID, notification)
    }

    /**
     * 单集临近/刚开播提醒。
     * 为每部番剧分配独立 Notification ID，互不顶掉覆盖；
     * 单集通知点击直达该番剧详情页面。
     */
    fun notifyImminent(
        upcoming: List<UpcomingAiring>,
        isAlreadyStarted: Boolean = false,
    ) {
        if (upcoming.isEmpty()) return
        createNotificationChannels(context)
        val manager = NotificationManagerCompat.from(context)

        for (item in upcoming) {
            val isFinale = item.isFinale
            val contentTitle =
                if (isFinale) {
                    "🎬《${item.displayName}》全剧完结！"
                } else if (isAlreadyStarted) {
                    context.getString(R.string.airing_pre_air_title_started)
                } else {
                    context.getString(R.string.airing_pre_air_title)
                }
            val itemText =
                if (isFinale) {
                    "第 ${item.episode} 话（最终话）现已播出，共 ${item.totalEpisodes} 话全部完结，可以一口气开刷啦！"
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
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(VIBRATION_PATTERN)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .build()

            post(manager, notificationIdForSubject(item.subjectId), notification)

            // 应用前台运行时同步发送应用内顶部胶囊横幅
            com.infinitezerone.minibgm.core.common.InAppNotificationBus.post(
                title = contentTitle,
                message = itemText,
                subjectId = item.subjectId,
                coverUrl = item.coverUrl,
            )
        }
    }

    private fun post(
        manager: NotificationManagerCompat,
        id: Int,
        notification: android.app.Notification,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(id, notification)
        }
    }

    private fun launchScheduleIntent(): PendingIntent? {
        val launch = BgmNavIntents.createScheduleIntent(context)
        if (launch.component == null && launch.action == null) return null
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun launchSubjectIntent(subjectId: Long): PendingIntent? {
        val launch = BgmNavIntents.createSubjectIntent(context, subjectId)
        if (launch.component == null && launch.action == null) return null
        return PendingIntent.getActivity(
            context,
            notificationIdForSubject(subjectId),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val GROUP_ID = "group_airing"
        const val CHANNEL_ID = "airing_reminders"
        const val NOTIFICATION_ID = 4701
        const val PRE_AIR_CHANNEL_ID = "airing_pre_air"
        private val VIBRATION_PATTERN = longArrayOf(0, 250, 250, 250)

        fun notificationIdForSubject(subjectId: Long): Int = (47000 + (subjectId % 10000)).toInt()

        /**
         * 预注册所有通知渠道与分组（在 App 启动时调用，解决系统设置中看不到子渠道的懒加载问题）。
         */
        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = NotificationManagerCompat.from(context)

            // 1. 注册渠道组
            manager.createNotificationChannelGroup(
                NotificationChannelGroup(
                    GROUP_ID,
                    context.getString(R.string.airing_channel_group_name),
                ),
            )

            // 2. 每日更新汇总渠道 (HIGH, 带振动与锁屏公开)
            val dailyChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.airing_reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    group = GROUP_ID
                    description = context.getString(R.string.airing_reminder_channel_desc)
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            manager.createNotificationChannel(dailyChannel)

            // 3. 单集即时开播提醒渠道 (HIGH, 带振动与锁屏公开)
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
