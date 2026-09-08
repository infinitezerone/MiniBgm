package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.common.AppResult
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

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        authRepo: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = false),
        scheduleRepo: FakeScheduleRepository = FakeScheduleRepository(),
        collectionRepo: FakeCollectionRepository = FakeCollectionRepository(),
        syncManager: FakeSyncManager = FakeSyncManager(),
    ): Pair<UserViewModel, FakeScheduleRepository> {
        val viewModel =
            UserViewModel(
                authRepository = authRepo,
                scheduleRepository = scheduleRepo,
                collectionRepository = collectionRepo,
                settingsRepository = FakeSettingsRepository(),
                syncManager = syncManager,
            )
        return viewModel to scheduleRepo
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
}
