package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.UpdateRepository
import com.infinitezerone.minibgm.core.model.AppUpdateInfo
import com.infinitezerone.minibgm.core.model.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val mutableDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val downloadState: StateFlow<DownloadState> = mutableDownloadState.asStateFlow()

    var downloadUpdateApkCallCount: Int = 0
        private set

    var cancelDownloadCallCount: Int = 0
        private set

    var canRequestPackageInstallsResult: Boolean = true
    var openInstallPermissionSettingsCallCount: Int = 0
        private set

    var installUpdateApkCallCount: Int = 0
        private set
    var installUpdateApkResult: Boolean = true

    override suspend fun checkForUpdate(currentVersionName: String): AppResult<AppUpdateInfo> {
        checkForUpdateCallCount++
        return checkUpdateResult
    }

    override suspend fun downloadUpdateApk(
        downloadUrl: String,
        targetVersion: String,
        customMirrorPrefix: String?,
    ): DownloadState {
        downloadUpdateApkCallCount++
        val completed = DownloadState.Completed("/fake/path/minibgm-v$targetVersion.apk")
        mutableDownloadState.value = completed
        return completed
    }

    override fun cancelDownload() {
        cancelDownloadCallCount++
        mutableDownloadState.value = DownloadState.Idle
    }

    override fun canRequestPackageInstalls(): Boolean = canRequestPackageInstallsResult

    override fun openInstallPermissionSettings() {
        openInstallPermissionSettingsCallCount++
    }

    override fun installUpdateApk(apkPath: String?): Boolean {
        installUpdateApkCallCount++
        return installUpdateApkResult
    }
}
