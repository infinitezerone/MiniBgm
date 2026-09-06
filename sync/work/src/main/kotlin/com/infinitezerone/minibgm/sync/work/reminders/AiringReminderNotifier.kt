package com.infinitezerone.minibgm.sync.work.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.sync.work.R

/**
 * 开播提醒通知的 Android 侧实现：频道管理、权限检查与汇总通知展示。
 * 逻辑判定在 [AiringReminderPlanner]，本类只负责"发"。
 */
class AiringReminderNotifier(
    private val context: Context,
) {
    fun notificationsEnabled(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun notify(upcoming: List<UpcomingAiring>) {
        val manager = NotificationManagerCompat.from(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.airing_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        val listText =
            upcoming.joinToString("\n") { item ->
                context.getString(R.string.airing_reminder_item, item.displayName, item.episode)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle(context.getString(R.string.airing_reminder_title, upcoming.size))
                .setStyle(NotificationCompat.BigTextStyle().bigText(listText))
                .setContentIntent(launchAppIntent())
                .setAutoCancel(true)
                .build()

        post(manager, NOTIFICATION_ID, notification)
    }

    /**
     * 开播前提醒：单集临近开播的实时通知。使用独立的高优先级频道，
     * 用户可在系统设置中对"每日汇总"与"开播前提醒"分别静音。
     */
    fun notifyImminent(upcoming: List<UpcomingAiring>) {
        val manager = NotificationManagerCompat.from(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    PRE_AIR_CHANNEL_ID,
                    context.getString(R.string.airing_pre_air_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        val listText =
            upcoming.joinToString("\n") { item ->
                context.getString(R.string.airing_pre_air_item, item.displayName, item.episode)
            }
        val notification =
            NotificationCompat
                .Builder(context, PRE_AIR_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(context.getString(R.string.airing_pre_air_title))
                .setStyle(NotificationCompat.BigTextStyle().bigText(listText))
                .setContentIntent(launchAppIntent())
                .setAutoCancel(true)
                .build()

        post(manager, PRE_AIR_NOTIFICATION_ID, notification)
    }

    private fun post(
        manager: NotificationManagerCompat,
        id: Int,
        notification: android.app.Notification,
    ) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            manager.notify(id, notification)
        }
    }

    private fun launchAppIntent(): PendingIntent {
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                // 与 :app MainActivity.EXTRA_OPEN_SCHEDULE 契约对齐（模块边界不允许直接引用）
                putExtra("open_schedule", true)
            }
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "airing_reminders"
        const val NOTIFICATION_ID = 4701
        const val PRE_AIR_CHANNEL_ID = "airing_pre_air"
        const val PRE_AIR_NOTIFICATION_ID = 4702
    }
}
