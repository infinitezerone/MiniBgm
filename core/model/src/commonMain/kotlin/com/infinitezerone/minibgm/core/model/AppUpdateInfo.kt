package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 客户端版本更新领域模型。
 *
 * @param currentVersion 当前运行的应用版本（如 "0.2.8"）
 * @param latestVersion 服务端/Release 最新版本（如 "0.3.0"）
 * @param hasUpdate 是否存在可用更新（latestVersion > currentVersion）
 * @param releaseName Release 标题（如 "v0.3.0 · 当季大盘与排期升级"）
 * @param releaseNotes 更新日志描述正文
 * @param releaseUrl GitHub Release 详情主页 URL
 * @param downloadUrl APK 直接下载链接（若匹配到 release asset 则非空）
 * @param downloadSize 产物文件大小（字节数，0 表示未提供）
 * @param publishedAt 发布时间（ISO-8601 字符串）
 */
@Serializable
data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val hasUpdate: Boolean,
    val releaseName: String = "",
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val downloadUrl: String? = null,
    val downloadSize: Long = 0L,
    val publishedAt: String = "",
)
