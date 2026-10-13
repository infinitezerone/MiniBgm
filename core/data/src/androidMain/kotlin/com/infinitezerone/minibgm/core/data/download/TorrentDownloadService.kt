package com.infinitezerone.minibgm.core.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 负责在系统前台执行与展示磁力/BT 下载进度的常驻前台服务。
 * 遵循 Android 14+ 前台服务与 dataSync 规范。
 */
class TorrentDownloadService :
    Service(),
    KoinComponent {
    private val logger = bgmLogger("Bgm/TorrentDownloadService")
    private val torrentRepository: TorrentRepository by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val strings: TorrentNotificationStrings by lazy {
        getKoin().getOrNull<TorrentNotificationStrings>() ?: DefaultTorrentNotificationStrings
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        observeTasks()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)

        when (action) {
            ACTION_START -> {
                val initialTitle = intent.getStringExtra(EXTRA_TITLE) ?: strings.initialTitle
                val initialNotification =
                    buildProgressNotification(
                        title = initialTitle,
                        statusText = strings.preparing,
                        percentage = 0,
                        speedBytesPerSec = 0L,
                        taskId = taskId,
                    )
                val foregroundType =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, foregroundType)
            }
            ACTION_PAUSE -> {
                if (!taskId.isNullOrBlank()) {
                    serviceScope.launch { torrentRepository.pauseDownload(taskId) }
                }
            }
            ACTION_RESUME -> {
                if (!taskId.isNullOrBlank()) {
                    serviceScope.launch { torrentRepository.resumeDownload(taskId) }
                }
            }
            ACTION_CANCEL -> {
                if (!taskId.isNullOrBlank()) {
                    serviceScope.launch { torrentRepository.cancelDownload(taskId) }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeTasks() {
        serviceScope.launch {
            torrentRepository.tasks.collect { tasks ->
                val activeTask =
                    tasks.firstOrNull {
                        it.status == TorrentTaskStatus.DOWNLOADING ||
                            it.status == TorrentTaskStatus.RESOLVING ||
                            it.status == TorrentTaskStatus.QUEUED
                    }

                if (activeTask != null) {
                    val statusText =
                        when (activeTask.status) {
                            TorrentTaskStatus.RESOLVING -> strings.resolving
                            TorrentTaskStatus.QUEUED -> strings.queued
                            else ->
                                strings.progress(
                                    activeTask.percentage,
                                    activeTask.formattedDownloadSpeed,
                                )
                        }
                    val notification =
                        buildProgressNotification(
                            title = activeTask.title,
                            statusText = statusText,
                            percentage = activeTask.percentage,
                            speedBytesPerSec = activeTask.downloadSpeedBytesPerSec,
                            taskId = activeTask.id,
                        )
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } else {
                    val completedTask = tasks.lastOrNull { it.status == TorrentTaskStatus.COMPLETED }
                    if (completedTask != null) {
                        val completeNotification = buildCompleteNotification(completedTask)
                        notificationManager.notify(NOTIFICATION_ID_COMPLETE, completeNotification)
                    }
                    // 无活跃任务时退出前台服务
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    strings.channelName,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = strings.channelDesc
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(
        title: String,
        statusText: String,
        percentage: Int,
        speedBytesPerSec: Long,
        taskId: String?,
    ): android.app.Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(contentPendingIntent)
            .setProgress(100, percentage, percentage <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (!taskId.isNullOrBlank()) {
                    val pauseIntent =
                        Intent(this@TorrentDownloadService, TorrentDownloadService::class.java).apply {
                            action = ACTION_PAUSE
                            putExtra(EXTRA_TASK_ID, taskId)
                        }
                    val pausePendingIntent =
                        PendingIntent.getService(
                            this@TorrentDownloadService,
                            1,
                            pauseIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    addAction(
                        android.R.drawable.ic_media_pause,
                        strings.actionPause,
                        pausePendingIntent,
                    )

                    val cancelIntent =
                        Intent(this@TorrentDownloadService, TorrentDownloadService::class.java).apply {
                            action = ACTION_CANCEL
                            putExtra(EXTRA_TASK_ID, taskId)
                        }
                    val cancelPendingIntent =
                        PendingIntent.getService(
                            this@TorrentDownloadService,
                            2,
                            cancelIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        strings.actionCancel,
                        cancelPendingIntent,
                    )
                }
            }.build()
    }

    private fun buildCompleteNotification(task: TorrentTask): android.app.Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(strings.completedTitle)
            .setContentText(task.title)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "minibgm_torrent_downloads"
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_ID_COMPLETE = 2002

        const val ACTION_START = "com.infinitezerone.minibgm.action.START_TORRENT_DOWNLOAD"
        const val ACTION_PAUSE = "com.infinitezerone.minibgm.action.PAUSE_TORRENT_DOWNLOAD"
        const val ACTION_RESUME = "com.infinitezerone.minibgm.action.RESUME_TORRENT_DOWNLOAD"
        const val ACTION_CANCEL = "com.infinitezerone.minibgm.action.CANCEL_TORRENT_DOWNLOAD"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"

        fun start(
            context: Context,
            taskId: String,
            title: String,
        ) {
            val intent =
                Intent(context, TorrentDownloadService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_TITLE, title)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
