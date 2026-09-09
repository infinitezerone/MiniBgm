package com.infinitezerone.minibgm

import android.app.Application
import android.content.ComponentCallbacks2
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.di.appModule
import com.infinitezerone.minibgm.feature.widget.WidgetSync
import com.infinitezerone.minibgm.sync.work.initializers.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MiniBgmApp :
    Application(),
    SingletonImageLoader.Factory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun newImageLoader(context: PlatformContext): ImageLoader = get()

    override fun onCreate() {
        super.onCreate()

        // 初始化统一日志门面（Kermit）：统一挂载 Android 平台日志输出器（Logcat）
        // Debug 开启 Debug 级别便于联调追踪；Release 提升至 Warn 级别杜绝信息泄漏与性能损耗
        Logger.setLogWriters(platformLogWriter())
        Logger.setMinSeverity(if (BuildConfig.DEBUG) Severity.Debug else Severity.Warn)

        startKoin {
            // release 下仅记录错误，避免 DI 结构信息进入公共日志
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MiniBgmApp)
            workManagerFactory()
            modules(appModule())
        }

        // 初始化后台同步与开播提醒调度（封装在 :sync:work 内部，按偏好动态配置）
        val userPreferences: UserPreferencesDataSource by inject()
        Sync.initialize(this@MiniBgmApp, userPreferences, appScope)

        // 登录会话建立（含冷启动时已登录）后同步一次云端「在看」收藏到本地库：
        // 时刻表「我追的」、待补更新、小组件与开播提醒都消费本地收藏流，
        // 本地无数据即表现为「没有在追的番」
        val authRepository: AuthRepository by inject()
        val collectionRepository: CollectionRepository by inject()
        appScope.launch {
            authRepository.isLoggedIn
                .distinctUntilChanged()
                .collect { loggedIn ->
                    if (loggedIn) collectionRepository.syncWatchingCollections()
                }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 当用户切换出应用返回桌面时，智能按需刷新一次小组件（内部带有 hasActiveWidgets 守卫）
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            appScope.launch {
                WidgetSync.requestUpdate(this@MiniBgmApp)
            }
        }
    }
}
