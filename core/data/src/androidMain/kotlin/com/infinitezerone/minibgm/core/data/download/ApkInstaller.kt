package com.infinitezerone.minibgm.core.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import java.io.File

class AndroidApkInstaller(
    private val context: Context,
) : ApkInstaller {
    private val logger = bgmLogger("Bgm/ApkInstaller")

    override fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    override fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val intent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
            }.onFailure { e ->
                logger.w { "无法跳转未知应用安装设置页: ${e.message}" }
            }
        }
    }

    override fun installApk(apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            logger.w { "APK 安装失败: 文件不存在或为空 $apkPath" }
            return false
        }

        return runCatching {
            val contentUri: Uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile,
                )

            val installIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            context.startActivity(installIntent)
            true
        }.getOrElse { e ->
            logger.e { "拉起系统安装器失败: ${e.message}" }
            false
        }
    }
}
