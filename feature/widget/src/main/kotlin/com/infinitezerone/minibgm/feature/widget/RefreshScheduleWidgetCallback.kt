package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import org.koin.core.context.GlobalContext

/**
 * 桌面小组件右上角无感刷新回调：
 * 用户在桌面点击刷新图标时在后台触发时间表同步与小组件重建，不弹出/不干扰前台应用。
 */
class RefreshScheduleWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val koin = GlobalContext.getOrNull()
        runCatching {
            koin?.getOrNull<ScheduleRepository>()?.refreshSchedules()
        }
        ScheduleWidget().update(context, glanceId)
    }
}
