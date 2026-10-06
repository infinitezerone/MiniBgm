package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AppUpdateInfo
import com.infinitezerone.minibgm.core.network.UpdateService

interface UpdateRepository {
    /**
     * 检查客户端是否有新版本发布。
     *
     * @param currentVersionName 当前运行的应用版本名（如 "0.2.8"）
     * @return 包含更新信息的 [AppResult.Success]，或网络/解析失败的 [AppResult.Error]
     */
    suspend fun checkForUpdate(currentVersionName: String): AppResult<AppUpdateInfo>
}

internal class UpdateRepositoryImpl(
    private val updateService: UpdateService,
) : UpdateRepository {
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
