package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 应用内网页会话 ViewModel 的契约测试。
 *
 * 它在登录接管页与浏览页之间复用：这里锁住「会话来自仓库」「代理不可用时返回 null（走浏览器降级）」
 * 「授权码交换结果通过回调上报」三条与 UI 行为直接相关的约定。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InAppWebViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun beginLoginSession_returnsLoopbackSessionFromRepository() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = InAppWebViewModel(authRepo)

            val session = viewModel.beginLoginSession()

            assertNotNull(session)
            assertEquals(1, authRepo.beginInAppLoginCallCount)
            assertTrue(session!!.url.startsWith("http://127.0.0.1"))
            assertTrue(session.cookieValue.isNotBlank())
        }

    @Test
    fun beginLoginSession_whenProxyUnavailable_returnsNull() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            authRepo.inAppWebSession = null
            val viewModel = InAppWebViewModel(authRepo)

            assertNull(viewModel.beginLoginSession())
        }

    @Test
    fun beginBrowseSession_delegatesToRepository() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = InAppWebViewModel(authRepo)
            val tokenPageUrl = "https://next.bgm.tv/demo/access-token"

            val session = viewModel.beginBrowseSession(tokenPageUrl)

            assertEquals(1, authRepo.beginInAppBrowseCallCount)
            assertEquals(tokenPageUrl, session?.url)
        }

    @Test
    fun beginBrowserLogin_delegatesToRepository() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = InAppWebViewModel(authRepo)

            val url = viewModel.beginBrowserLogin()

            assertTrue(url.contains("bgm.tv/oauth/authorize"))
            assertEquals(1, authRepo.beginLoginCallCount)
        }

    @Test
    fun completeLogin_reportsSuccessThroughCallback() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = InAppWebViewModel(authRepo)
            var reportedSuccess: Boolean? = null

            viewModel.completeLogin("code", "state") { success, _ -> reportedSuccess = success }
            advanceUntilIdle()

            assertEquals(true, reportedSuccess)
            assertEquals(1, authRepo.completeLoginCallCount)
        }

    @Test
    fun completeLogin_reportsFailureMessageThroughCallback() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            authRepo.completeLoginResult = AppResult.Error(IllegalStateException("state 校验失败"))
            val viewModel = InAppWebViewModel(authRepo)
            var reportedMessage: String? = null

            viewModel.completeLogin("code", "forged") { success, error ->
                if (!success) reportedMessage = error
            }
            advanceUntilIdle()

            assertEquals("state 校验失败", reportedMessage)
        }

    @Test
    fun stopSession_stopsRepositorySession() {
        val authRepo = FakeAuthRepository(initialLoggedIn = false)
        val viewModel = InAppWebViewModel(authRepo)

        viewModel.stopSession()

        assertEquals(1, authRepo.stopInAppWebCallCount)
    }

    @Test
    fun beginLoginSession_cachesActiveSessionUntilStopped() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = InAppWebViewModel(authRepo)

            val session1 = viewModel.beginLoginSession()
            val session2 = viewModel.beginLoginSession()

            assertEquals(session1, session2)
            assertEquals(1, authRepo.beginInAppLoginCallCount)

            viewModel.stopSession()
            val session3 = viewModel.beginLoginSession()

            assertNotNull(session3)
            assertEquals(2, authRepo.beginInAppLoginCallCount)
        }

    @Test
    fun beginBrowseSession_cachesActiveSessionUntilStopped() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = InAppWebViewModel(authRepo)
            val targetUrl = "https://next.bgm.tv/demo"

            val session1 = viewModel.beginBrowseSession(targetUrl)
            val session2 = viewModel.beginBrowseSession(targetUrl)

            assertEquals(session1, session2)
            assertEquals(1, authRepo.beginInAppBrowseCallCount)

            viewModel.stopSession()
            viewModel.beginBrowseSession(targetUrl)

            assertEquals(2, authRepo.beginInAppBrowseCallCount)
        }
}
