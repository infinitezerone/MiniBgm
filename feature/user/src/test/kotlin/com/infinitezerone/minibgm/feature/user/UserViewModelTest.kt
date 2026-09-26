package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.testing.data.sampleUserProfile
import com.infinitezerone.minibgm.core.testing.data.sampleUserProfileAlt
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSyncManager
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    ): Triple<UserViewModel, FakeScheduleRepository, FakeSettingsRepository> {
        val viewModel =
            UserViewModel(
                authRepository = authRepo,
                scheduleRepository = scheduleRepo,
                collectionRepository = collectionRepo,
                settingsRepository = settingsRepo,
                syncManager = syncManager,
            )
        return Triple(viewModel, scheduleRepo, settingsRepo)
    }

    @Test
    fun initialState_notLoggedIn() =
        runTest {
            val (viewModel, _) = createViewModel()

            val state = viewModel.uiState.first()
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

            val state = viewModel.uiState.first { !it.isLoggedIn }
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

            val state = viewModel.uiState.first { !it.isLoggedIn }
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
    fun syncBangumiDataNow_triggersScheduleRepository() =
        runTest {
            val scheduleRepo = FakeScheduleRepository()
            scheduleRepo.syncBangumiDataResult = AppResult.Success(Unit)
            val (viewModel, _) = createViewModel(scheduleRepo = scheduleRepo)

            var callbackSuccess = false
            viewModel.syncBangumiDataNow { success ->
                callbackSuccess = success
            }

            assertTrue(callbackSuccess)
            assertEquals(1, scheduleRepo.syncBangumiDataCallCount)
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

            var refreshDone = false
            viewModel.refresh { success ->
                refreshDone = success
            }

            assertTrue(refreshDone)
            assertEquals(1, authRepo.refreshProfileCallCount)
            // 初始加载 1 次 + 下拉刷新 1 次 = 共 2 次，无并发冗余请求
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

            // 验证未触发重新拉取
            assertEquals(1, collectionRepo.fetchCollectionCountsCallCount)
        }

    @Test
    fun beginLogin_delegatesToAuthRepository() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            val url = viewModel.beginLogin()

            assertTrue(url.contains("bgm.tv/oauth/authorize"))
            assertEquals(1, authRepo.beginLoginCallCount)
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
}
