package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import org.koin.core.context.GlobalContext
import java.util.concurrent.atomic.AtomicBoolean

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
        // 防抖：同步进行中忽略重复点击，避免叠加网络请求
        if (!refreshInProgress.compareAndSet(false, true)) return
        try {
            val koin = GlobalContext.getOrNull()
            val result = koin?.getOrNull<ScheduleRepository>()?.refreshSchedules()
            if (result is AppResult.Error) {
                // 桌面后台没有 UI 反馈通道，文本 Toast 是仅存的感知手段；
                // 失败必须可见，避免小组件停留在旧数据上被误读为最新
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.widget_refresh_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        } finally {
            refreshInProgress.set(false)
        }
        ScheduleWidget().update(context, glanceId)
    }

    private companion object {
        val refreshInProgress = AtomicBoolean(false)
    }
}
