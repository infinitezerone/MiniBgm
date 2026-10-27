package com.infinitezerone.minibgm.core.data.notification

/** 开播提醒的通知渠道 ID：调度层与设置页共用，避免两处字符串漂移。 */
object AiringNotificationChannels {
    const val GROUP_ID = "group_airing"
    const val PRE_AIR = "airing_pre_air"
}
