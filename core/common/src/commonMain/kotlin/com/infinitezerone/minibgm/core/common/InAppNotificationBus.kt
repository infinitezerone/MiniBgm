package com.infinitezerone.minibgm.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 应用内轻量通知数据体
 */
data class InAppNotification(
    val title: String,
    val message: String,
    val subjectId: Long = 0L,
    val coverUrl: String = "",
)

/**
 * 全局应用内通知总线：
 * 用于在应用前台运行时，以主流应用顶部悬浮横幅形式向用户呈现重要广播（如全剧完结、囤番状态变更）。
 */
object InAppNotificationBus {
    @kotlin.concurrent.Volatile
    var isAppInForeground: Boolean = false

    private val _notifications = MutableSharedFlow<InAppNotification>(extraBufferCapacity = 2)
    val notifications: Flow<InAppNotification> = _notifications.asSharedFlow()

    fun post(notification: InAppNotification) {
        _notifications.tryEmit(notification)
    }

    fun post(
        title: String,
        message: String,
        subjectId: Long = 0L,
        coverUrl: String = "",
    ) {
        post(
            InAppNotification(
                title = title,
                message = message,
                subjectId = subjectId,
                coverUrl = coverUrl,
            ),
        )
    }
}
