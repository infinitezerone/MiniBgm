package com.infinitezerone.minibgm

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.di.appModule
import com.infinitezerone.minibgm.sync.work.initializers.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class MiniBgmApp :
    Application(),
    SingletonImageLoader.Factory {
    private val appScope = CoroutineScope(Dispatchers.Main.immediate)

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB 磁盘缓存
                    .build()
            }.crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            // release 下仅记录错误，避免 DI 结构信息进入公共日志
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MiniBgmApp)
            workManagerFactory()
            modules(appModule())
        }

        // 周期任务统一在冷启动时确保注册：KEEP 策略幂等，是否真正提醒由 Worker 内部按偏好决策
        Sync.enqueueAiringReminders(this@MiniBgmApp)

        // 监听用户偏好，执行智能冷启动同步与动态注册 WorkManager 周期任务
        val userPreferences: UserPreferencesDataSource by inject()
        appScope.launch {
            val initialPrefs = userPreferences.userPreferences.first()
            val isNeverSynced = initialPrefs.bangumiDataLastSyncTimestamp == 0L
            val isAutoSyncEnabled = initialPrefs.syncInterval != SyncInterval.MANUAL_ONLY
            val intervalMillis = TimeUnit.HOURS.toMillis(initialPrefs.syncInterval.hours)
            val isExpired = (TimeUtils.nowEpochMillis() - initialPrefs.bangumiDataLastSyncTimestamp) > intervalMillis

            // 智能节流：仅在首次冷启动（新安装未同步）或距上次同步已超过周期且未设为手动模式时，才发起冷启动补偿同步
            if (isAutoSyncEnabled && (isNeverSynced || isExpired)) {
                Sync.enqueueStartupSync(this@MiniBgmApp)
            }

            userPreferences.userPreferences
                .map { it.syncInterval }
                .distinctUntilChanged()
                .collect { interval ->
                    Sync.reconfigure(this@MiniBgmApp, interval)
                }
        }
    }
}
