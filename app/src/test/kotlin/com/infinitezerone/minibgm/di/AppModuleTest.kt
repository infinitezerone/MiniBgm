package com.infinitezerone.minibgm.di

import android.content.Context
import com.infinitezerone.minibgm.core.data.cache.DanmakuDiskCache
import com.infinitezerone.minibgm.core.data.crash.CrashLogRepository
import com.infinitezerone.minibgm.core.data.di.platformDataModule
import com.infinitezerone.minibgm.core.data.download.ApkInstaller
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.download.TorrentIntentDispatcher
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.data.repository.UpdateRepository
import com.infinitezerone.minibgm.core.data.seasonal.SeasonalDiskCache
import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import com.infinitezerone.minibgm.core.database.di.databaseModule
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import com.infinitezerone.minibgm.core.network.GitHubReleaseDto
import com.infinitezerone.minibgm.core.network.UpdateService
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeUpdateRepository
import com.infinitezerone.minibgm.feature.search.SearchViewModel
import com.infinitezerone.minibgm.feature.user.DownloadViewModel
import com.infinitezerone.minibgm.feature.user.HistoryViewModel
import com.infinitezerone.minibgm.feature.user.InAppWebViewModel
import com.infinitezerone.minibgm.feature.user.PlaybackRulesViewModel
import com.infinitezerone.minibgm.feature.user.UserCollectionsViewModel
import com.infinitezerone.minibgm.feature.user.UserViewModel
import com.infinitezerone.minibgm.feature.user.di.userModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import java.io.File
import java.lang.reflect.Proxy

/**
 * Koin 装配测试：
 * 1. 验证真实 platformDataModule 中的全部单例可成功解析（保证如 ApkInstaller、TorrentRepository 等无漏注册）；
 * 2. 验证真实 userModule 中的全部 ViewModel 可通过 Koin 反射闭环实例化；
 * 3. 验证领域契约解析冒烟。
 */
class AppModuleTest : KoinTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    private fun createStubContext(cacheDir: File): Context =
        object : android.content.ContextWrapper(null) {
            override fun getCacheDir(): File = cacheDir

            override fun getFilesDir(): File = cacheDir

            // AndroidTorrentRepository.resolveDownloadDir() 会走 getExternalFilesDir(null)；
            // 缺这个重写会在 JVM 上抛 "Method getExternalFilesDir in android.content.ContextWrapper
            // not mocked"，让下载页在构造期直接击穿本测试的 Koin 装配红线。
            override fun getExternalFilesDir(type: String?): File = File(cacheDir, "ext_files").apply { mkdirs() }

            override fun getApplicationContext(): Context = this

            override fun getPackageName(): String = "com.infinitezerone.minibgm"

            override fun getSystemService(name: String): Any? = null
        }

    @Test
    fun verifyRealPlatformDataModuleResolution() =
        runTest {
            val tempDir =
                java.nio.file.Files
                    .createTempDirectory("minibgm_koin_test")
                    .toFile()
            val stubContext = createStubContext(tempDir)

            startKoin {
                androidContext(stubContext)
                modules(
                    platformDataModule,
                    databaseModule,
                    module {
                        single<AssetDownloadService> {
                            object : AssetDownloadService {
                                override suspend fun streamAsset(
                                    url: String,
                                    rangeStart: Long,
                                    onChunk: suspend (ByteArray, Int, Long, Boolean) -> Unit,
                                ): Long = 0L
                            }
                        }
                        single<UpdateService> {
                            object : UpdateService {
                                override suspend fun getLatestRelease(): GitHubReleaseDto = GitHubReleaseDto()
                            }
                        }
                    },
                )
            }

            // 彻底机械断言 platformDataModule 中注册的所有定义，确保任何类（如 ApkInstaller、TorrentRepository）未漏注册
            assertNotNull(get<NetworkMonitor>())
            assertNotNull(get<CrashLogRepository>())
            assertNotNull(get<SeasonalDiskCache>())
            assertNotNull(get<DanmakuDiskCache>())
            assertNotNull(get<AssetDownloader>())
            assertNotNull(get<TorrentIntentDispatcher>())
            assertNotNull(get<TorrentRepository>())
            assertNotNull(get<TorrentStreamEngine>())
            assertNotNull(get<ApkInstaller>())
            assertNotNull(get<UpdateRepository>())

            tempDir.deleteRecursively()
        }

    @Test
    fun verifyRealUserModuleResolutionWithPlatformDataModule() =
        runTest {
            val tempDir =
                java.nio.file.Files
                    .createTempDirectory("minibgm_user_test")
                    .toFile()
            val stubContext = createStubContext(tempDir)

            startKoin {
                androidContext(stubContext)
                modules(
                    platformDataModule,
                    databaseModule,
                    userModule,
                    module {
                        single<AssetDownloadService> {
                            object : AssetDownloadService {
                                override suspend fun streamAsset(
                                    url: String,
                                    rangeStart: Long,
                                    onChunk: suspend (ByteArray, Int, Long, Boolean) -> Unit,
                                ): Long = 0L
                            }
                        }
                        single<UpdateService> {
                            object : UpdateService {
                                override suspend fun getLatestRelease(): GitHubReleaseDto = GitHubReleaseDto()
                            }
                        }
                        single<AuthRepository> { FakeAuthRepository() }
                        single<ScheduleRepository> { FakeScheduleRepository() }
                        single<CollectionRepository> { FakeCollectionRepository() }
                        single<SettingsRepository> { FakeSettingsRepository() }
                        single<com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository> {
                            com.infinitezerone.minibgm.core.testing.repository
                                .FakePlaybackHistoryRepository()
                        }
                        single<com.infinitezerone.minibgm.core.data.util.SyncManager> {
                            com.infinitezerone.minibgm.core.testing.repository
                                .FakeSyncManager()
                        }
                        single<com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository> {
                            Proxy.newProxyInstance(
                                com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository::class.java.classLoader,
                                arrayOf(com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository::class.java),
                            ) { _, _, _ -> emptyList<Any>() } as com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
                        }
                    },
                )
            }

            // 验证真实 userModule 中的每个 ViewModel 均可通过真实 platformDataModule + 领域仓库完整解析，
            // 若任何 ViewModel 参数类型未被定义，将立刻抛出 NoDefinitionFoundException 熔断
            assertNotNull(get<UserViewModel>())
            assertNotNull(get<UserCollectionsViewModel>())
            assertNotNull(get<PlaybackRulesViewModel>())
            assertNotNull(get<InAppWebViewModel>())
            assertNotNull(get<DownloadViewModel>())
            assertNotNull(get<HistoryViewModel>())

            tempDir.deleteRecursively()
        }

    @Test
    fun verifyUserViewModelWithAuthRepository() =
        runTest {
            val fakeAuth = FakeAuthRepository(initialLoggedIn = false)
            val fakeSchedule = FakeScheduleRepository()
            val fakeCollection = FakeCollectionRepository()
            val fakeSync =
                com.infinitezerone.minibgm.core.testing.repository
                    .FakeSyncManager()
            startKoin {
                modules(
                    module {
                        single<AuthRepository> { fakeAuth }
                        single<ScheduleRepository> { fakeSchedule }
                        single<CollectionRepository> { fakeCollection }
                        single<SettingsRepository> { FakeSettingsRepository() }
                        single<com.infinitezerone.minibgm.core.data.util.SyncManager> { fakeSync }
                        single<com.infinitezerone.minibgm.core.data.crash.CrashLogRepository> {
                            com.infinitezerone.minibgm.core.testing.repository
                                .FakeCrashLogRepository()
                        }
                        single<UpdateRepository> { FakeUpdateRepository() }
                        single { UserViewModel(get(), get(), get(), get(), get(), get(), get()) }
                    },
                )
            }

            val viewModel: UserViewModel = get()
            assertNotNull(viewModel)
            assertEquals(false, viewModel.uiState.value.isLoggedIn)
        }

    @Test
    fun verifyRepositoriesResolution() =
        runTest {
            startKoin {
                modules(
                    module {
                        single<AuthRepository> { FakeAuthRepository() }
                        single<ScheduleRepository> { FakeScheduleRepository() }
                        single<SubjectRepository> { FakeSubjectRepository() }
                        single<SearchRepository> { FakeSearchRepository() }
                    },
                )
            }

            val authRepo: AuthRepository = get()
            val scheduleRepo: ScheduleRepository = get()
            val subjectRepo: SubjectRepository = get()
            val searchRepo: SearchRepository = get()

            assertNotNull(authRepo)
            assertNotNull(scheduleRepo)
            assertNotNull(subjectRepo)
            assertNotNull(searchRepo)
        }

    @Test
    fun verifySearchViewModelWithSearchRepository() =
        runTest {
            val fakeSearch = FakeSearchRepository()
            startKoin {
                modules(
                    module {
                        single<SearchRepository> { fakeSearch }
                        single<CollectionRepository> { FakeCollectionRepository() }
                        single<AuthRepository> { FakeAuthRepository() }
                        single<ScheduleRepository> { FakeScheduleRepository() }
                        single { SearchViewModel(get(), get(), get(), get()) }
                    },
                )
            }

            val viewModel: SearchViewModel = get()
            assertNotNull(viewModel)
            assertEquals("", viewModel.uiState.value.query)
        }

    @Test
    fun verifySyncManagerResolution() =
        runTest {
            val fakeSync =
                com.infinitezerone.minibgm.core.testing.repository
                    .FakeSyncManager()
            startKoin {
                modules(
                    module {
                        single<com.infinitezerone.minibgm.core.data.util.SyncManager> { fakeSync }
                    },
                )
            }

            val syncManager: com.infinitezerone.minibgm.core.data.util.SyncManager = get()
            assertNotNull(syncManager)
            assertEquals(false, syncManager.isSyncing.first())
        }
}
