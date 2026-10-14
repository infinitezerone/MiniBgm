package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.download.ApkInstaller
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.model.AppUpdateInfo
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.network.UpdateService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job

interface UpdateRepository {
    /**
     * 检查客户端是否有新版本发布。
     *
     * @param currentVersionName 当前运行的应用版本名（如 "0.2.8"）
     * @return 包含更新信息的 [AppResult.Success]，或网络/解析失败的 [AppResult.Error]
     */
    suspend fun checkForUpdate(currentVersionName: String): AppResult<AppUpdateInfo>

    /** 观察当前安装包下载状态 */
    val downloadState: StateFlow<DownloadState>

    /**
     * 开始下载目标版本的 APK 安装包。
     *
     * @param downloadUrl 原始下载直链
     * @param targetVersion 目标版本名
     * @param customMirrorPrefix 用户自定义加速镜像前缀（可选）
     */
    suspend fun downloadUpdateApk(
        downloadUrl: String,
        targetVersion: String,
        customMirrorPrefix: String? = null,
    ): DownloadState

    /** 取消当前更新下载 */
    fun cancelDownload()

    /** 检查是否已具备安装未知应用权限 (Android 8.0+) */
    fun canRequestPackageInstalls(): Boolean

    /** 跳转至系统安装未知应用权限授权页面 (Android 8.0+) */
    fun openInstallPermissionSettings()

    /**
     * 拉起系统安装器执行覆盖安装。
     *
     * @param apkPath 已下载 APK 本地绝对路径，为空时自动取最近下载的已完成 APK
     * @return 是否成功唤起安装器
     */
    fun installUpdateApk(apkPath: String? = null): Boolean
}

internal class UpdateRepositoryImpl(
    private val updateService: UpdateService,
    private val assetDownloader: AssetDownloader? = null,
    private val apkInstaller: ApkInstaller? = null,
    private val updatesDirectoryProvider: (() -> String)? = null,
) : UpdateRepository {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private var lastCompletedApkPath: String? = null
    private var downloadJob: Job? = null

    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    override suspend fun checkForUpdate(currentVersionName: String): AppResult<AppUpdateInfo> =
        runCatching {
            val release = updateService.getLatestRelease()
            val latestVersion = release.tagName.removePrefix("v")
            val hasUpdate = isNewerVersion(current = currentVersionName, target = latestVersion)

            // 优先匹配 universal 或通用 release.apk，其次匹配 arm64 / 任意 apk
            val apkAsset =
                release.assets.firstOrNull {
                    it.name.endsWith(
                        ".apk",
                        ignoreCase = true,
                    ) &&
                        it.name.contains("universal", ignoreCase = true)
                }
                    ?: release.assets.firstOrNull {
                        it.name.endsWith(".apk", ignoreCase = true) &&
                            it.name.contains("release", ignoreCase = true)
                    }
                    ?: release.assets.firstOrNull {
                        it.name.endsWith(".apk", ignoreCase = true) &&
                            it.name.contains("arm64", ignoreCase = true)
                    }
                    ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

            AppUpdateInfo(
                currentVersion = currentVersionName,
                latestVersion = latestVersion,
                hasUpdate = hasUpdate,
                releaseName = (release.name ?: release.tagName).ifBlank { "v$latestVersion" },
                releaseNotes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl.ifBlank { "https://github.com/infinitezerone/MiniBgm/releases/latest" },
                downloadUrl = apkAsset?.browserDownloadUrl,
                downloadSize = apkAsset?.size ?: 0L,
                publishedAt = release.publishedAt.orEmpty(),
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it, it.message ?: "检查更新失败") },
        )

    override suspend fun downloadUpdateApk(
        downloadUrl: String,
        targetVersion: String,
        customMirrorPrefix: String?,
    ): DownloadState {
        val downloader = assetDownloader ?: return DownloadState.Failed("未配置下载器")
        val dir = updatesDirectoryProvider?.invoke() ?: ""
        val destinationPath = if (dir.isNotBlank()) "$dir/minibgm-v$targetVersion.apk" else "minibgm-v$targetVersion.apk"

        cancelDownload()

        return coroutineScope {
            val currentJob = coroutineContext.job
            downloadJob = currentJob
            try {
                var finalState: DownloadState = DownloadState.Idle
                downloader
                    .download(
                        rawUrl = downloadUrl,
                        destinationPath = destinationPath,
                        customMirrorPrefix = customMirrorPrefix,
                    ).collect { state ->
                        _downloadState.value = state
                        if (state is DownloadState.Completed || state is DownloadState.Failed) {
                            finalState = state
                        }
                    }
                if (finalState is DownloadState.Completed) {
                    lastCompletedApkPath = (finalState as DownloadState.Completed).filePath
                }
                finalState
            } catch (e: CancellationException) {
                _downloadState.value = DownloadState.Idle
                throw e
            } finally {
                if (downloadJob == currentJob) {
                    downloadJob = null
                }
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    override fun canRequestPackageInstalls(): Boolean = apkInstaller?.canRequestPackageInstalls() ?: true

    override fun openInstallPermissionSettings() {
        apkInstaller?.openInstallPermissionSettings()
    }

    override fun installUpdateApk(apkPath: String?): Boolean {
        val targetPath = apkPath ?: lastCompletedApkPath ?: return false
        return apkInstaller?.installApk(targetPath) ?: false
    }

    internal companion object {
        /**
         * 语义化版本对比：如果 target 版本高于 current 版本则返回 true。
         * 支持形如 "0.2.8"、"v0.3.0"、"1.0.0-beta" 等格式。
         */
        fun isNewerVersion(
            current: String,
            target: String,
        ): Boolean {
            val curClean = current.trim().removePrefix("v").substringBefore("-")
            val tarClean = target.trim().removePrefix("v").substringBefore("-")
            if (curClean.isBlank() || tarClean.isBlank()) return false
            val curParts = curClean.split(".").mapNotNull { it.toIntOrNull() }
            val tarParts = tarClean.split(".").mapNotNull { it.toIntOrNull() }
            val length = maxOf(curParts.size, tarParts.size)
            for (i in 0 until length) {
                val c = curParts.getOrElse(i) { 0 }
                val t = tarParts.getOrElse(i) { 0 }
                if (t > c) return true
                if (t < c) return false
            }
            return false
        }
    }
}
