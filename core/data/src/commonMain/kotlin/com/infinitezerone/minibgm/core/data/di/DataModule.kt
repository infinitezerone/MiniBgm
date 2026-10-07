package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.common.SecureSecretStore
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.AuthRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.PlaybackRuleSampleReplayer
import com.infinitezerone.minibgm.core.data.repository.PlaybackRuleSampleReplayerImpl
import com.infinitezerone.minibgm.core.data.repository.PlaybackSourceVerifier
import com.infinitezerone.minibgm.core.data.repository.PlaybackSourceVerifierImpl
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepositoryImpl
import com.infinitezerone.minibgm.core.data.util.UserDataCleaner
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiCommunityService
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmTokenService
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotService
import org.koin.dsl.module

val dataModule =
    module {
        single<CollectionRepository> {
            CollectionRepositoryImpl(
                apiService = get<BangumiApiService>(),
                userCollectionDao = get<UserCollectionDao>(),
                tokenProvider = get<TokenProvider>(),
                userPreferences = get<UserPreferencesDataSource>(),
            )
        }
        single<ScheduleRepository> {
            ScheduleRepositoryImpl(
                scheduleDao = get<AirScheduleDao>(),
                airEventDao = get<AirEventDao>(),
                anilistMappingDao = get<AniListMappingDao>(),
                snapshotService = get<ScheduleSnapshotService>(),
                userPreferences = get<UserPreferencesDataSource>(),
                collectionRepository = getOrNull<CollectionRepository>(),
                seasonalDiskCache = getOrNull<com.infinitezerone.minibgm.core.data.seasonal.SeasonalDiskCache>(),
            )
        }
        single<SubjectRepository> {
            SubjectRepositoryImpl(
                apiService = get<BangumiApiService>(),
            )
        }
        single<SearchRepository> {
            SearchRepositoryImpl(
                apiService = get<BangumiApiService>(),
                userPreferences = get<UserPreferencesDataSource>(),
            )
        }
        single<CommunityRepository> {
            CommunityRepositoryImpl(
                communityService = get<BangumiCommunityService>(),
            )
        }
        single<PlaybackResolverRepository> {
            PlaybackResolverRepositoryImpl(
                pageFetchService = get<PageFetchService>(),
                webViewCaptureService = getOrNull(),
            )
        }
        single<PlaybackSourceVerifier> {
            PlaybackSourceVerifierImpl(pageFetchService = get<PageFetchService>())
        }
        single<PlaybackRuleSampleReplayer> {
            PlaybackRuleSampleReplayerImpl(pageFetchService = get<PageFetchService>())
        }
        single<SettingsRepository> {
            SettingsRepositoryImpl(
                userPreferences = get<UserPreferencesDataSource>(),
                secureSecretStore = get<SecureSecretStore>(),
                communitySubscriptionService = getOrNull(),
            )
        }
        single {
            com.infinitezerone.minibgm.core.data.playback
                .PlaybackFailureStore()
        }
        single {
            UserDataCleaner(
                clearables =
                    listOf(
                        get<UserPreferencesDataSource>(),
                        get<CollectionRepository>(),
                    ),
            )
        }
        single<AuthRepository> {
            AuthRepositoryImpl(
                tokenService = get<BgmTokenService>(),
                tokenProvider = get<TokenProvider>(),
                userPreferences = get<UserPreferencesDataSource>(),
                authConfig = get<BgmAuthConfig>(),
                apiService = get<BangumiApiService>(),
                userDataCleaner = get<UserDataCleaner>(),
                oAuthProxyService = getOrNull(),
            )
        }
        single<com.infinitezerone.minibgm.core.data.repository.AssistantRepository> {
            com.infinitezerone.minibgm.core.data.repository.AssistantRepositoryImpl(
                assistantMessageDao = get(),
                assistantSessionDao = get(),
            )
        }
        single<com.infinitezerone.minibgm.core.data.repository.UpdateRepository> {
            com.infinitezerone.minibgm.core.data.repository.UpdateRepositoryImpl(
                updateService = get(),
            )
        }
    }
