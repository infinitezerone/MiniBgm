package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.data.crash.CrashLogRepository
import com.infinitezerone.minibgm.core.data.crash.FileCrashLogRepository
import com.infinitezerone.minibgm.core.data.crash.crashLogDir
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
    }
