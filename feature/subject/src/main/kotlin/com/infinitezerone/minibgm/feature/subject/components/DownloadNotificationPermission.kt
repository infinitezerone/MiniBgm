package com.infinitezerone.minibgm.feature.subject.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 返回一个「确保下载通知权限已就绪」的请求器。
 *
 * 通知栏是离线下载唯一的外部门控面（暂停 / 取消 / 进度），而 Android 13+ 未授予
 * `POST_NOTIFICATIONS` 时前台服务通知会被静默隐藏：用户看到「已加入下载队列」却
 * 在通知栏找不到任何进度与取消入口。因此首次下载前必须主动请求一次。
 *
 * 权限已授予或系统版本低于 13 时为空操作，不重复弹窗。
 */
@Composable
internal fun rememberDownloadNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
