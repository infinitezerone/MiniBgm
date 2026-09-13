package com.infinitezerone.minibgm.core.datastore.di

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.datastore.AgentConfigDataSource
import com.infinitezerone.minibgm.core.datastore.AndroidCryptoManager
import com.infinitezerone.minibgm.core.datastore.AuthBlobSerializer
import com.infinitezerone.minibgm.core.datastore.AuthTokensDataSource
import com.infinitezerone.minibgm.core.datastore.CryptoManager
import com.infinitezerone.minibgm.core.datastore.KeystoreTokenProvider
import com.infinitezerone.minibgm.core.datastore.UserPreferences
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.datastore.UserPreferencesSerializer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val datastoreModule =
    module {
        single<UserPreferencesDataSource> {
            val userPrefsDataStore =
                DataStoreFactory.create(
                    serializer = UserPreferencesSerializer,
                    produceFile = { androidContext().dataStoreFile("user_preferences.json") },
                    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { UserPreferences() }),
                )
            UserPreferencesDataSource(userPrefsDataStore)
        }

        single<CryptoManager> { AndroidCryptoManager() }

        // OAuth token 独立加密存储；损坏时按未登录处理（典型场景：备份恢复到新设备，Keystore 密钥不可迁移）
        single<AuthTokensDataSource> {
            val authTokensDataStore =
                DataStoreFactory.create(
                    serializer = AuthBlobSerializer,
                    produceFile = { androidContext().dataStoreFile("auth_tokens.json") },
                    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { "" }),
                )
            AuthTokensDataSource(dataStore = authTokensDataStore, crypto = get())
        }

        // Agent 模型配置独立加密存储；损坏时按未配置处理
        single<AgentConfigDataSource> {
            val agentConfigDataStore =
                DataStoreFactory.create(
                    serializer = AuthBlobSerializer,
                    produceFile = { androidContext().dataStoreFile("agent_model_config.json") },
                    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { "" }),
                )
            AgentConfigDataSource(dataStore = agentConfigDataStore, crypto = get())
        }

        single<TokenProvider> { KeystoreTokenProvider(get()) }
    }
