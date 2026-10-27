package com.infinitezerone.minibgm.core.data.download

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** 下载通知渠道与渠道组：前后台服务、App 启动迁移共用同一入口。 */
object TorrentNotificationChannels {
    const val GROUP_ID = "group_downloads"
    const val CHANNEL_ID = "minibgm_torrent_downloads"

    fun ensure(
        context: Context,
        strings: TorrentNotificationStrings,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, strings.channelGroupName),
        )
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                strings.channelName,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_ID
                description = strings.channelDesc
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }
}
