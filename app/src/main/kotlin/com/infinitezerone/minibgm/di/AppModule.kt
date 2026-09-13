package com.infinitezerone.minibgm.di

import com.infinitezerone.minibgm.BuildConfig
import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.data.di.dataModule
import com.infinitezerone.minibgm.core.data.di.platformDataModule
import com.infinitezerone.minibgm.core.database.di.databaseModule
import com.infinitezerone.minibgm.core.datastore.di.datastoreModule
import com.infinitezerone.minibgm.core.network.di.networkModule
import com.infinitezerone.minibgm.feature.agent.di.agentModule
import com.infinitezerone.minibgm.feature.schedule.di.scheduleModule
import com.infinitezerone.minibgm.feature.search.di.searchModule
import com.infinitezerone.minibgm.feature.subject.di.subjectModule
import com.infinitezerone.minibgm.feature.user.di.userModule
import com.infinitezerone.minibgm.feature.widget.di.widgetModule
import com.infinitezerone.minibgm.sync.work.di.syncWorkModule
import com.miniagent.harness.AndroidKeystoreCipher
import com.miniagent.harness.EncryptedCheckpointStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

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
            platformDataModule,
            scheduleModule,
            userModule,
            subjectModule,
            searchModule,
            agentModule,
            syncWorkModule,
            imageLoaderModule,
            widgetModule,
        )
        single { BgmDispatchers() }
        // agent 会话 checkpoint 的加密落盘（AndroidKeyStore 密钥，私有目录，随卸载销毁）
        single {
            EncryptedCheckpointStorage(
                root = File(androidContext().filesDir, "agent-checkpoints"),
                cipher = AndroidKeystoreCipher(),
            )
        }
    }
