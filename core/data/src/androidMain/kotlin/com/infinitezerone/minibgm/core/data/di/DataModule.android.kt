package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.data.cache.FileTorrentFeedDiskCache
import com.infinitezerone.minibgm.core.data.cache.TorrentFeedDiskCache
import com.infinitezerone.minibgm.core.data.crash.CrashLogRepository
import com.infinitezerone.minibgm.core.data.crash.FileCrashLogRepository
import com.infinitezerone.minibgm.core.data.crash.crashLogDir
import com.infinitezerone.minibgm.core.data.download.AndroidApkInstaller
import com.infinitezerone.minibgm.core.data.download.AndroidAssetDownloader
import com.infinitezerone.minibgm.core.data.download.AndroidTorrentIntentDispatcher
import com.infinitezerone.minibgm.core.data.download.AndroidTorrentStreamEngine
import com.infinitezerone.minibgm.core.data.download.ApkInstaller
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.download.TorrentIntentDispatcher
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.data.repository.AndroidTorrentRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.data.repository.UpdateRepository
import com.infinitezerone.minibgm.core.data.repository.UpdateRepositoryImpl
import com.infinitezerone.minibgm.core.data.seasonal.FileSeasonalDiskCache
import com.infinitezerone.minibgm.core.data.seasonal.SeasonalDiskCache
import com.infinitezerone.minibgm.core.data.util.ConnectivityManagerNetworkMonitor
import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformDataModule =
    module {
        single<NetworkMonitor> { ConnectivityManagerNetworkMonitor(androidContext()) }
        single<CrashLogRepository> { FileCrashLogRepository(crashLogDir(androidContext())) }
        single<SeasonalDiskCache> { FileSeasonalDiskCache(androidContext().cacheDir.resolve("seasonal_cache")) }
        single<TorrentFeedDiskCache> { FileTorrentFeedDiskCache(androidContext().cacheDir.resolve("torrent_feed_cache")) }
        single<AssetDownloader> {
            AndroidAssetDownloader(
                assetDownloadService = get(),
                // 开发者面板的「自定义加速镜像前缀」必须真实参与 GitHub 资产下载：
                // 此前只有「写设置 + 读出设置」两个端点，中间没有任何消费者，
                // 用户填了前缀后更新包与 BT 原生扩展依旧走默认公共镜像。
                // 读取刻意留在 lambda 内：本模块必须能被单独解析（platformDataModule 的
                // DI 契约测试不加载 SettingsRepository 所在的 dataModule）。
                customMirrorPrefixProvider = {
                    get<SettingsRepository>().developerSettings.first().customMirrorPrefixOverride
                },
            )
        }
        single<TorrentIntentDispatcher> { AndroidTorrentIntentDispatcher(androidContext()) }
        single<TorrentRepository> {
            AndroidTorrentRepository(
                context = androidContext(),
                assetDownloader = get(),
                assetDownloadService = get(),
                streamEngine = get(),
                torrentTaskDao = get(),
            )
        }
        single<TorrentStreamEngine> {
            AndroidTorrentStreamEngine(
                context = androidContext(),
                assetDownloader = get(),
            )
        }
        single<ApkInstaller> { AndroidApkInstaller(androidContext()) }
        single<UpdateRepository> {
            UpdateRepositoryImpl(
                updateService = get(),
                assetDownloader = get(),
                apkInstaller = get(),
                updatesDirectoryProvider = {
                    androidContext()
                        .cacheDir
                        .resolve("updates")
                        .apply { mkdirs() }
                        .absolutePath
                },
            )
        }
    }
