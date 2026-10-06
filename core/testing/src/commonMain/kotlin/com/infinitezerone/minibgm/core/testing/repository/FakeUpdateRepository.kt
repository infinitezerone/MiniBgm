package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.UpdateRepository
import com.infinitezerone.minibgm.core.model.AppUpdateInfo

class FakeUpdateRepository : UpdateRepository {
    var checkUpdateResult: AppResult<AppUpdateInfo> =
        AppResult.Success(
            AppUpdateInfo(
                currentVersion = "0.2.8",
                latestVersion = "0.2.8",
                hasUpdate = false,
                releaseName = "v0.2.8",
                releaseNotes = "当前已是最新版本",
                releaseUrl = "https://github.com/infinitezerone/MiniBgm/releases/tag/v0.2.8",
            ),
        )

    var checkForUpdateCallCount: Int = 0
        private set

    override suspend fun checkForUpdate(currentVersionName: String): AppResult<AppUpdateInfo> {
        checkForUpdateCallCount++
        return checkUpdateResult
    }
}
