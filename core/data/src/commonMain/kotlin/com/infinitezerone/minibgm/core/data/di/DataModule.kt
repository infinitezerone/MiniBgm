package com.infinitezerone.minibgm.core.data.di

import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.AuthRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepositoryImpl
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepositoryImpl
import com.infinitezerone.minibgm.core.data.util.UserDataCleaner
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.EpisodeDao
import com.infinitezerone.minibgm.core.database.dao.SubjectDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.network.AniListService
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiCommunityService
import com.infinitezerone.minibgm.core.network.BangumiDataService
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmTokenService
import com.infinitezerone.minibgm.core.network.TokenProvider
import org.koin.dsl.module

val dataModule =
    module {
        single<ScheduleRepository> {
            ScheduleRepositoryImpl(
                apiService = get<BangumiApiService>(),
                dataService = get<BangumiDataService>(),
                scheduleDao = get<AirScheduleDao>(),
                airEventDao = get<AirEventDao>(),
                anilistService = get<AniListService>(),
                userPreferences = get<UserPreferencesDataSource>(),
            )
        }
        single<SubjectRepository> {
            SubjectRepositoryImpl(
                apiService = get<BangumiApiService>(),
                subjectDao = get<SubjectDao>(),
                episodeDao = get<EpisodeDao>(),
            )
        }
        single<CollectionRepository> {
            CollectionRepositoryImpl(
                apiService = get<BangumiApiService>(),
                userCollectionDao = get<UserCollectionDao>(),
                userPreferences = get<UserPreferencesDataSource>(),
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
            )
        }
    }
