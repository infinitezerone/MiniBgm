package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.data.repository.AgentConfigRepository
import com.infinitezerone.minibgm.core.data.repository.AgentConfigRepositoryImpl
import com.infinitezerone.minibgm.core.data.util.ConnectivityManagerNetworkMonitor
import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformDataModule =
    module {
        single<NetworkMonitor> { ConnectivityManagerNetworkMonitor(androidContext()) }
        single<AgentConfigRepository> { AgentConfigRepositoryImpl(get()) }
    }
