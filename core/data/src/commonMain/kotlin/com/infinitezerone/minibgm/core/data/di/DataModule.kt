package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.common.SecureSecretStore
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.AuthRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.DanmakuRepository
import com.infinitezerone.minibgm.core.data.repository.DanmakuRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepositoryImpl
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
import com.infinitezerone.minibgm.core.data.repository.TorrentFeedRepository
import com.infinitezerone.minibgm.core.data.repository.TorrentFeedRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.resolver.KitsuMappingResolver
import com.infinitezerone.minibgm.core.data.repository.resolver.KitsuMappingResolverImpl
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
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun dataModule(danmakuDefaultEndpoint: String = ""): Module =
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
        single<KitsuMappingResolver> {
            KitsuMappingResolverImpl(
                pageFetchService = get<PageFetchService>(),
                anilistMappingDao = getOrNull<AniListMappingDao>(),
            )
        }
        single<PlaybackResolverRepository> {
            PlaybackResolverRepositoryImpl(
                pageFetchService = get<PageFetchService>(),
                webViewCaptureService = getOrNull(),
                kitsuMappingResolver = get<KitsuMappingResolver>(),
            )
        }
        single<TorrentFeedRepository> {
            TorrentFeedRepositoryImpl(
                pageFetchService = get<PageFetchService>(),
                diskCache = getOrNull(),
            )
        }
        single<DanmakuRepository> {
            DanmakuRepositoryImpl(
                // 弹幕走 HTTP 缓存实例：新鲜度由服务端 Cache-Control / ETag 决定，不再自研 TTL
                pageFetchService = get<PageFetchService>(named("danmaku")),
                danmakuOverrideDao = getOrNull<com.infinitezerone.minibgm.core.database.dao.DanmakuOverrideDao>(),
                settingsRepository = getOrNull<SettingsRepository>(),
                // 离线时只读缓存：不新增本地缓存层，改用 RFC 5861 的 max-stale / only-if-cached
                networkMonitor = getOrNull(),
                // 解析必须离开调用方线程；注入统一调度器让测试可替换
                dispatchers = getOrNull() ?: BgmDispatchers(),
                defaultEndpoint = danmakuDefaultEndpoint,
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
        single<PlaybackHistoryRepository> {
            PlaybackHistoryRepositoryImpl(
                playbackHistoryDao = get(),
            )
        }
        single {
            UserDataCleaner(
                clearables =
                    listOf(
                        get<UserPreferencesDataSource>(),
                        get<CollectionRepository>(),
                        get<PlaybackHistoryRepository>(),
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
    }

val dataModule: Module = dataModule()
