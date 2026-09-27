package com.infinitezerone.minibgm.sync.work.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infinitezerone.minibgm.core.common.bgmLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 开机自启与时区校准接收器：在设备重启、用户手动调整时间或切换时区后，
 * 重新在系统 [android.app.AlarmManager] 中恢复注册开播提醒闹钟。
 */
class AiringBootReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val log = bgmLogger("Bgm/Alarm/BootReceiver")
    private val scheduler: AiringAlarmScheduler by inject()

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                log.i { "[BOOT_RECEIVER] restoring airing alarms on $action" }
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        scheduler.rescheduleAll()
                    } catch (e: Throwable) {
                        log.e(e) { "[BOOT_RECEIVER] failed restoring alarms" }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
