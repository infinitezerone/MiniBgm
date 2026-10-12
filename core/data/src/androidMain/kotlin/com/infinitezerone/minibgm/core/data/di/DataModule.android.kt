package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.data.crash.CrashLogRepository
import com.infinitezerone.minibgm.core.data.crash.FileCrashLogRepository
import com.infinitezerone.minibgm.core.data.crash.crashLogDir
import com.infinitezerone.minibgm.core.data.download.AndroidApkInstaller
import com.infinitezerone.minibgm.core.data.download.AndroidAssetDownloader
import com.infinitezerone.minibgm.core.data.download.ApkInstaller
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.seasonal.FileSeasonalDiskCache
import com.infinitezerone.minibgm.core.data.seasonal.SeasonalDiskCache
import com.infinitezerone.minibgm.core.data.util.ConnectivityManagerNetworkMonitor
import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformDataModule =
    module {
        single<NetworkMonitor> { ConnectivityManagerNetworkMonitor(androidContext()) }
        single<CrashLogRepository> { FileCrashLogRepository(crashLogDir(androidContext())) }
        single<SeasonalDiskCache> { FileSeasonalDiskCache(androidContext().cacheDir.resolve("seasonal_cache")) }
        single<AssetDownloader> {
            AndroidAssetDownloader(
                assetDownloadService = get(),
            )
        }
        single<ApkInstaller> { AndroidApkInstaller(androidContext()) }
        single<com.infinitezerone.minibgm.core.data.repository.UpdateRepository> {
            com.infinitezerone.minibgm.core.data.repository.UpdateRepositoryImpl(
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
