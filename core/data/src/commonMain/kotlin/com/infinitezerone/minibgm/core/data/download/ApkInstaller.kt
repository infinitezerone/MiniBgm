package com.infinitezerone.minibgm.core.data.download

/**
 * APK 应用安装器接口。
 */
interface ApkInstaller {
    /** 是否具备未知应用安装权限 (Android 8.0+) */
    fun canRequestPackageInstalls(): Boolean

    /** 跳转系统未知应用安装权限授权页面 (Android 8.0+) */
    fun openInstallPermissionSettings()

    /**
     * 拉起系统安装器执行安装。
     *
     * @param apkPath 待安装 APK 本地文件绝对路径
     * @return 是否成功拉起系统安装器
     */
    fun installApk(apkPath: String): Boolean
}
