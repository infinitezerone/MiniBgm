package com.infinitezerone.minibgm.di

import com.infinitezerone.minibgm.BuildConfig
import com.infinitezerone.minibgm.core.ai.di.aiModule
import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.data.di.dataModule
import com.infinitezerone.minibgm.core.data.di.platformDataModule
import com.infinitezerone.minibgm.core.database.di.databaseModule
import com.infinitezerone.minibgm.core.datastore.di.datastoreModule
import com.infinitezerone.minibgm.core.network.di.networkModule
import com.infinitezerone.minibgm.core.webview.di.webviewModule
import com.infinitezerone.minibgm.feature.assistant.di.assistantModule
import com.infinitezerone.minibgm.feature.schedule.di.scheduleModule
import com.infinitezerone.minibgm.feature.search.di.searchModule
import com.infinitezerone.minibgm.feature.subject.di.subjectModule
import com.infinitezerone.minibgm.feature.user.di.userModule
import com.infinitezerone.minibgm.feature.widget.di.widgetModule
import com.infinitezerone.minibgm.sync.work.di.syncWorkModule
import org.koin.dsl.module

/** 全 app 统一的 User-Agent 模板（API 请求与图片下载共用一个决策），版本随 BuildConfig 自动更新 */
val appUserAgent: String = "MiniBgm/${BuildConfig.VERSION_NAME} (android) (https://github.com/infinitezerone/MiniBgm)"

fun appModule(enableNetworkLogging: Boolean = BuildConfig.DEBUG) =
    module {
        includes(
            networkModule(
                enableNetworkLogging,
                userAgent = appUserAgent,
            ),
            databaseModule,
            datastoreModule,
            dataModule,
            platformDataModule,
            aiModule(userAgent = appUserAgent),
            scheduleModule,
            userModule,
            subjectModule,
            webviewModule,
            searchModule,
            assistantModule,
            syncWorkModule,
            imageLoaderModule,
            widgetModule,
        )
        single { BgmDispatchers() }
    }
