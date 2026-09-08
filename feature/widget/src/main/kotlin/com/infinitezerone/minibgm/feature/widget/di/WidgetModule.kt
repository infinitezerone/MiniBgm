package com.infinitezerone.minibgm.feature.widget.di

import android.content.Context
import com.infinitezerone.minibgm.core.data.util.SyncCompletionObserver
import com.infinitezerone.minibgm.feature.widget.WidgetSync
import com.infinitezerone.minibgm.feature.widget.WidgetUpdateWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val widgetModule =
    module {
        workerOf(::WidgetUpdateWorker)
        single<SyncCompletionObserver> { WidgetSyncCompletionObserver(appContext = get()) }
    }

/** 同步成功后驱动桌面小组件重建；WidgetSync 内部带 hasActiveWidgets 守卫，桌面无组件时短路 */
class WidgetSyncCompletionObserver(
    private val appContext: Context,
) : SyncCompletionObserver {
    override suspend fun onSyncSucceeded() {
        WidgetSync.requestUpdate(appContext)
    }
}
