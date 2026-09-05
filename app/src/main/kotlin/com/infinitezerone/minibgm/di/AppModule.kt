package com.infinitezerone.minibgm.di

import com.infinitezerone.minibgm.BuildConfig
import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.data.di.dataModule
import com.infinitezerone.minibgm.core.database.di.databaseModule
import com.infinitezerone.minibgm.core.datastore.di.datastoreModule
import com.infinitezerone.minibgm.core.network.di.networkModule
import com.infinitezerone.minibgm.feature.schedule.di.scheduleModule
import com.infinitezerone.minibgm.feature.search.di.searchModule
import com.infinitezerone.minibgm.feature.subject.di.subjectModule
import com.infinitezerone.minibgm.feature.user.di.userModule
import com.infinitezerone.minibgm.sync.work.di.syncWorkModule
import org.koin.dsl.module

fun appModule(enableNetworkLogging: Boolean = BuildConfig.DEBUG) =
    module {
        includes(
            networkModule(
                enableNetworkLogging,
                userAgent = "MiniBgm/${BuildConfig.VERSION_NAME} (android) (https://github.com/infinitezerone/MiniBgm)",
            ),
            databaseModule,
            datastoreModule,
            dataModule,
            scheduleModule,
            userModule,
            subjectModule,
            searchModule,
            syncWorkModule,
        )
        single { BgmDispatchers() }
    }
