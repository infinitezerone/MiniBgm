package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.crash.CrashLog
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AppUpdateInfo
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleUserProfile
import com.infinitezerone.minibgm.core.testing.data.sampleUserProfileAlt
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCrashLogRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSyncManager
import com.infinitezerone.minibgm.core.testing.repository.FakeUpdateRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 测试替身的非凭据标记值；用符号常量传递，避免在源码里出现凭据形状的字面量 */
private const val STUB_TOKEN = "stub-token"

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        authRepo: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = false),
        scheduleRepo: FakeScheduleRepository = FakeScheduleRepository(),
        collectionRepo: FakeCollectionRepository = FakeCollectionRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        syncManager: FakeSyncManager = FakeSyncManager(),
        crashLogRepo: FakeCrashLogRepository = FakeCrashLogRepository(),
        updateRepo: FakeUpdateRepository = FakeUpdateRepository(),
    ): Triple<UserViewModel, FakeScheduleRepository, FakeSettingsRepository> {
        val viewModel =
            UserViewModel(
                authRepository = authRepo,
                scheduleRepository = scheduleRepo,
                collectionRepository = collectionRepo,
                settingsRepository = settingsRepo,
                syncManager = syncManager,
                crashLogRepository = crashLogRepo,
                updateRepository = updateRepo,
            )
        return Triple(viewModel, scheduleRepo, settingsRepo)
    }

    @Test
    fun loadLatestCrashLog_passesThroughRepositoryResult() =
        runTest {
            val crashRepo =
                FakeCrashLogRepository().apply {
                    latestResult = CrashLog(occurredAtMillis = 1_700_000_000_000L, content = "boom")
                }
            val (viewModel, _) = createViewModel(crashLogRepo = crashRepo)

            val log = viewModel.loadLatestCrashLog()

            assertEquals("boom", log?.content)
            assertEquals(1_700_000_000_000L, log?.occurredAtMillis)
        }

    @Test
    fun loadLatestCrashLog_whenNeverCrashed_returnsNull() =
        runTest {
            val (viewModel, _) = createViewModel()

            assertNull(viewModel.loadLatestCrashLog())
        }

    @Test
    fun clearCrashLogs_delegatesToRepository() =
        runTest {
            val crashRepo =
                FakeCrashLogRepository().apply {
                    latestResult = CrashLog(occurredAtMillis = 1L, content = "boom")
                }
            val (viewModel, _) = createViewModel(crashLogRepo = crashRepo)

            viewModel.clearCrashLogs()

            assertEquals(1, crashRepo.clearCallCount)
            assertNull(viewModel.loadLatestCrashLog())
        }

    @Test
    fun initialState_isLoading() =
        runTest {
            val (viewModel, _) = createViewModel()

            assertTrue(viewModel.uiState.value.isLoading)
        }

    @Test
    fun loadedState_notLoggedIn() =
        runTest {
            val (viewModel, _) = createViewModel()

            val state = viewModel.uiState.first { !it.isLoading }
            assertFalse(state.isLoading)
            assertFalse(state.isLoggedIn)
            assertNull(state.activeProfile)
            assertTrue(state.savedAccounts.isEmpty())
            assertEquals(SyncInterval.WEEKLY, state.syncInterval)
        }

    @Test
    fun loggedInState_emitsActiveProfileAndAccounts() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile, sampleUserProfileAlt),
                )
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            val state = viewModel.uiState.first { it.isLoggedIn && it.activeProfile != null }
            assertFalse(state.isLoading)
            assertTrue(state.isLoggedIn)
            assertNotNull(state.activeProfile)
            assertEquals("零一", state.activeProfile?.nickname)
            assertEquals(2, state.savedAccounts.size)
        }

    @Test
    fun switchAccount_updatesActiveProfile() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile, sampleUserProfileAlt),
                )
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            viewModel.switchAccount(sampleUserProfileAlt.id)

            val state = viewModel.uiState.first { it.activeProfile?.id == sampleUserProfileAlt.id }
            assertEquals(1, authRepo.switchAccountCallCount)
            assertEquals("马甲二号", state.activeProfile?.nickname)
            assertEquals(999L, state.activeProfile?.id)
        }

    @Test
    fun logoutSingleAccount_switchesToRemainingAccount() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile, sampleUserProfileAlt),
                )
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            viewModel.logout(sampleUserProfile.id)

            val state = viewModel.uiState.first { it.activeProfile?.id == sampleUserProfileAlt.id }
            assertEquals(1, authRepo.logoutCallCount)
            assertTrue(state.isLoggedIn)
            assertEquals("马甲二号", state.activeProfile?.nickname)
            assertEquals(1, state.savedAccounts.size)
        }

    @Test
    fun logoutCurrentAccount_clearsLoginStateWhenSingleAccount() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile),
                )
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            viewModel.logout()

            val state = viewModel.uiState.first { !it.isLoading && !it.isLoggedIn }
            assertEquals(1, authRepo.logoutCallCount)
            assertFalse(state.isLoggedIn)
            assertNull(state.activeProfile)
            assertTrue(state.savedAccounts.isEmpty())
        }

    @Test
    fun logoutAll_clearsAllAccounts() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile, sampleUserProfileAlt),
                )
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            viewModel.logoutAll()

            val state = viewModel.uiState.first { !it.isLoading && !it.isLoggedIn }
            assertEquals(1, authRepo.logoutAllCallCount)
            assertFalse(state.isLoggedIn)
            assertNull(state.activeProfile)
            assertTrue(state.savedAccounts.isEmpty())
        }

    @Test
    fun setSyncInterval_updatesState() =
        runTest {
            val (viewModel, _) = createViewModel()

            viewModel.setSyncInterval(SyncInterval.DAILY)

            val state = viewModel.uiState.first { it.syncInterval == SyncInterval.DAILY }
            assertEquals(SyncInterval.DAILY, state.syncInterval)
        }

    @Test
    fun setAiringReminderHour_updatesState() =
        runTest {
            val (viewModel, _) = createViewModel()

            viewModel.setAiringReminderHour(21)

            val state = viewModel.uiState.first { it.airingReminderHour == 21 }
            assertEquals(21, state.airingReminderHour)
        }

    @Test
    fun setAmoledDarkMode_updatesState() =
        runTest {
            val (viewModel, _, settingsRepo) = createViewModel()

            viewModel.setAmoledDarkMode(true)

            val state = viewModel.uiState.first { it.amoledDarkMode }
            assertTrue(state.amoledDarkMode)
            assertEquals(1, settingsRepo.setAmoledDarkModeCallCount)
        }

    @Test
    fun loggedInState_loadsCollectionCountsFromRepository() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(com.infinitezerone.minibgm.core.testing.data.sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            val state = viewModel.uiState.first { it.collectionCounts.isNotEmpty() }
            assertEquals(1, state.collectionCounts[com.infinitezerone.minibgm.core.model.CollectionType.DOING])
        }

    @Test
    fun loggedInState_emitsTrackingFootprint() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendTrackingFootprint(
                TrackingFootprint(
                    watchingCount = 8,
                    episodesWatched = 96,
                    monthActiveCount = 3,
                    lastActiveAtIso = "2026-09-26T14:30:00Z",
                ),
            )
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            val state = viewModel.uiState.first { it.trackingFootprint != null }
            assertEquals(8, state.trackingFootprint?.watchingCount)
            assertEquals(96, state.trackingFootprint?.episodesWatched)
            assertEquals(3, state.trackingFootprint?.monthActiveCount)
        }

    @Test
    fun isAuthenticating_updatesUiStateAccordingly() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            assertFalse(viewModel.uiState.value.isAuthenticating)

            authRepo.setAuthenticating(true)
            val authenticatingState = viewModel.uiState.first { it.isAuthenticating }
            assertTrue(authenticatingState.isAuthenticating)

            authRepo.setAuthenticating(false)
            val idleState = viewModel.uiState.first { !it.isAuthenticating }
            assertFalse(idleState.isAuthenticating)
        }

    @Test
    fun refresh_refreshesProfileAndCollectionCountsWhenLoggedIn() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(com.infinitezerone.minibgm.core.testing.data.sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            // 订阅驱动 counts 链的初始加载（WhileSubscribed：无订阅不拉取）
            viewModel.uiState.first { it.collectionCounts.isNotEmpty() }

            var refreshDone = false
            viewModel.refresh { success ->
                refreshDone = success
            }

            assertTrue(refreshDone)
            assertEquals(1, authRepo.refreshProfileCallCount)
            // 初始加载 1 次 + 下拉刷新（generation 递增触发 flatMapLatest 换挡）1 次 = 共 2 次
            assertEquals(2, collectionRepo.fetchCollectionCountsCallCount)
        }

    @Test
    fun refresh_reportsFailure_whenCollectionsSyncFails() =
        runTest {
            // 回归：追番收藏同步失败曾被静默丢弃，下拉刷新误报成功，
            // 用户会停留在过期的收藏数据上
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.syncWatchingResult = AppResult.Error(RuntimeException("offline"), "网络异常")
            collectionRepo.sendCollection(com.infinitezerone.minibgm.core.testing.data.sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            var refreshDone: Boolean? = null
            viewModel.refresh { success ->
                refreshDone = success
            }

            assertEquals(false, refreshDone)
        }

    @Test
    fun settingsChange_doesNotTriggerCollectionCountsReload() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(com.infinitezerone.minibgm.core.testing.data.sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            viewModel.uiState.first { it.collectionCounts.isNotEmpty() }
            val initialCalls = collectionRepo.fetchCollectionCountsCallCount
            assertEquals(1, initialCalls)

            // 修改设置项
            viewModel.setSyncInterval(SyncInterval.DAILY)
            viewModel.setAiringReminderHour(10)

            // 验证未触发重新拉取：settings 流不在 counts 换挡判据（profile + generation）里
            assertEquals(1, collectionRepo.fetchCollectionCountsCallCount)
        }

    @Test
    fun switchAccount_refetchesCollectionCountsForNewProfile() =
        runTest {
            // 回归：counts 拉取由 profile 驱动 flatMapLatest 换挡，切换账号自动重拉，
            // 无需手动触发（旧的 lastLoadedUserId 手动去重已删除）
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile, sampleUserProfileAlt),
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(com.infinitezerone.minibgm.core.testing.data.sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            viewModel.uiState.first { it.collectionCounts.isNotEmpty() }
            assertEquals(1, collectionRepo.fetchCollectionCountsCallCount)

            viewModel.switchAccount(sampleUserProfileAlt.id)
            advanceUntilIdle()

            assertEquals(2, collectionRepo.fetchCollectionCountsCallCount)
        }

    @Test
    fun logout_clearsCollectionCountsState() =
        runTest {
            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = sampleUserProfile,
                    initialAccounts = listOf(sampleUserProfile),
                )
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(com.infinitezerone.minibgm.core.testing.data.sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            viewModel.uiState.first { it.collectionCounts.isNotEmpty() }

            viewModel.logout()

            // null 档案 → flatMapLatest 换挡为清空分支
            val state = viewModel.uiState.first { !it.isLoggedIn && it.collectionCounts.isEmpty() }
            assertNull(state.activeProfile)
        }

    @Test
    fun loginWithPersonalAccessToken_delegatesToAuthRepositoryAndInvokesCallback() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val (viewModel, _) = createViewModel(authRepo = authRepo)
            var callbackSuccess = false

            viewModel.loginWithPersonalAccessToken("valid_token") { success, _ ->
                callbackSuccess = success
            }
            advanceUntilIdle()

            assertEquals(1, authRepo.loginWithPersonalAccessTokenCallCount)
            assertTrue(callbackSuccess)
        }

    @Test
    fun initialState_emitsDefaultAiConfig() =
        runTest {
            val (viewModel, _) = createViewModel()

            val state = viewModel.uiState.first()
            assertEquals(AiConfig(), state.aiConfig)
        }

    @Test
    fun setAiConfig_updatesSettingsRepositoryAndState() =
        runTest {
            val (viewModel, _, settingsRepo) = createViewModel()

            val customConfig =
                AiConfig(
                    provider = AiConfig.PROVIDER_GEMINI,
                    endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/",
                    apiKey = STUB_TOKEN,
                    model = "gemini-2.5-pro",
                )

            viewModel.setAiConfig(customConfig)

            val state = viewModel.uiState.first { it.aiConfig == customConfig }
            assertEquals(customConfig, state.aiConfig)
            assertEquals(1, settingsRepo.setAiConfigCallCount)
        }

    @Test
    fun initialState_emitsDefaultPipEnabledTrue() =
        runTest {
            val (viewModel, _) = createViewModel()

            val state = viewModel.uiState.first()
            assertTrue(state.pipEnabled)
        }

    @Test
    fun setPipEnabled_updatesSettingsRepositoryAndState() =
        runTest {
            val (viewModel, _, settingsRepo) = createViewModel()

            viewModel.setPipEnabled(false)

            val state = viewModel.uiState.first { !it.pipEnabled }
            assertFalse(state.pipEnabled)
            assertEquals(1, settingsRepo.setPipEnabledCallCount)
        }

    @Test
    fun setAiringNotificationOffsetMinutes_updatesSettingsRepositoryAndState() =
        runTest {
            val (viewModel, _, _) = createViewModel()

            assertEquals(-15, viewModel.uiState.first().airingNotificationOffsetMinutes)

            viewModel.setAiringNotificationOffsetMinutes(25)

            val updatedState = viewModel.uiState.first { it.airingNotificationOffsetMinutes == 25 }
            assertEquals(25, updatedState.airingNotificationOffsetMinutes)
            assertEquals(0, updatedState.notifyBeforeAirMinutes)
            assertEquals(25, updatedState.airDelayOffsetMinutes)
        }

    @Test
    fun checkForUpdate_delegatesToRepositoryAndReturnsResult() =
        runTest {
            val fakeUpdateRepo = FakeUpdateRepository()
            fakeUpdateRepo.checkUpdateResult =
                AppResult.Success(
                    AppUpdateInfo(
                        currentVersion = "0.2.8",
                        latestVersion = "0.3.0",
                        hasUpdate = true,
                        releaseName = "v0.3.0",
                        releaseNotes = "全新版本",
                        releaseUrl = "https://github.com/infinitezerone/MiniBgm/releases/tag/v0.3.0",
                    ),
                )
            val (viewModel, _, _) = createViewModel(updateRepo = fakeUpdateRepo)

            assertFalse(viewModel.uiState.value.isCheckingUpdate)

            val result = viewModel.checkForUpdate("0.2.8")
            assertEquals(1, fakeUpdateRepo.checkForUpdateCallCount)
            assertTrue(result is AppResult.Success)
            val info = (result as AppResult.Success).data
            assertTrue(info.hasUpdate)
            assertEquals("0.3.0", info.latestVersion)

            assertFalse(viewModel.uiState.value.isCheckingUpdate)
        }
}
