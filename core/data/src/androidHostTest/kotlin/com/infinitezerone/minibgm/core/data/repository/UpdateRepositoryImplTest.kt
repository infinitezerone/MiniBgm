package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.network.GitHubReleaseAssetDto
import com.infinitezerone.minibgm.core.network.GitHubReleaseDto
import com.infinitezerone.minibgm.core.network.UpdateService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryImplTest {
    private class FakeUpdateService(
        var releaseDto: GitHubReleaseDto = GitHubReleaseDto(),
        var shouldThrow: Boolean = false,
    ) : UpdateService {
        override suspend fun getLatestRelease(): GitHubReleaseDto {
            if (shouldThrow) throw RuntimeException("Network timeout")
            return releaseDto
        }
    }

    @Test
    fun isNewerVersion_correctlyComparesSemverStrings() {
        assertTrue(UpdateRepositoryImpl.isNewerVersion("0.2.8", "0.2.9"))
        assertTrue(UpdateRepositoryImpl.isNewerVersion("0.2.8", "0.3.0"))
        assertTrue(UpdateRepositoryImpl.isNewerVersion("0.2.8", "1.0.0"))
        assertTrue(UpdateRepositoryImpl.isNewerVersion("v0.2.8", "v0.3.0"))
        assertTrue(UpdateRepositoryImpl.isNewerVersion("0.2.8", "0.2.8.1"))

        assertFalse(UpdateRepositoryImpl.isNewerVersion("0.2.8", "0.2.8"))
        assertFalse(UpdateRepositoryImpl.isNewerVersion("v0.2.8", "0.2.8"))
        assertFalse(UpdateRepositoryImpl.isNewerVersion("0.3.0", "0.2.8"))
        assertFalse(UpdateRepositoryImpl.isNewerVersion("1.0.0", "0.9.9"))
        assertFalse(UpdateRepositoryImpl.isNewerVersion("", "0.1.0"))
        assertFalse(UpdateRepositoryImpl.isNewerVersion("0.1.0", ""))
    }

    @Test
    fun checkForUpdate_whenNewerReleaseExists_returnsHasUpdateTrueAndPicksApkAsset() =
        runTest {
            val fakeService =
                FakeUpdateService(
                    releaseDto =
                        GitHubReleaseDto(
                            tagName = "v0.3.0",
                            name = "v0.3.0 大盘重构",
                            body = "修复排期问题并增加全景大盘",
                            htmlUrl = "https://github.com/infinitezerone/MiniBgm/releases/tag/v0.3.0",
                            publishedAt = "2026-10-06T10:00:00Z",
                            assets =
                                listOf(
                                    GitHubReleaseAssetDto(
                                        name = "MiniBgm-v0.3.0-arm64-v8a-release.apk",
                                        browserDownloadUrl = "https://example.com/download/arm64-release.apk",
                                        size = 15000000L,
                                    ),
                                    GitHubReleaseAssetDto(
                                        name = "MiniBgm-v0.3.0-release.apk",
                                        browserDownloadUrl = "https://example.com/download/app-release.apk",
                                        size = 25000000L,
                                    ),
                                ),
                        ),
                )
            val repo = UpdateRepositoryImpl(fakeService)

            val result = repo.checkForUpdate("0.2.8")
            assertTrue(result is AppResult.Success)
            val info = (result as AppResult.Success).data
            assertTrue(info.hasUpdate)
            assertEquals("0.3.0", info.latestVersion)
            assertEquals("0.2.8", info.currentVersion)
            assertEquals("v0.3.0 大盘重构", info.releaseName)
            assertEquals("修复排期问题并增加全景大盘", info.releaseNotes)
            assertEquals("https://github.com/infinitezerone/MiniBgm/releases/tag/v0.3.0", info.releaseUrl)
            // 优先匹配包含 release / universal 的 apk
            assertNotNull(info.downloadUrl)
            assertTrue(info.downloadUrl!!.contains("release.apk"))
        }

    @Test
    fun checkForUpdate_whenCurrentIsLatest_returnsHasUpdateFalse() =
        runTest {
            val fakeService =
                FakeUpdateService(
                    releaseDto =
                        GitHubReleaseDto(
                            tagName = "v0.2.8",
                            name = "v0.2.8",
                            htmlUrl = "https://github.com/infinitezerone/MiniBgm/releases/tag/v0.2.8",
                        ),
                )
            val repo = UpdateRepositoryImpl(fakeService)

            val result = repo.checkForUpdate("0.2.8")
            assertTrue(result is AppResult.Success)
            val info = (result as AppResult.Success).data
            assertFalse(info.hasUpdate)
            assertEquals("0.2.8", info.latestVersion)
        }

    @Test
    fun checkForUpdate_whenNetworkFails_returnsAppResultError() =
        runTest {
            val fakeService = FakeUpdateService(shouldThrow = true)
            val repo = UpdateRepositoryImpl(fakeService)

            val result = repo.checkForUpdate("0.2.8")
            assertTrue(result is AppResult.Error)
        }
}
