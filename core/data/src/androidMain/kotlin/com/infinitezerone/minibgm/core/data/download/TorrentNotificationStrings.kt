package com.infinitezerone.minibgm.core.data.download

/**
 * 磁力/BT 前台服务通知文案提供契约。
 *
 * 遵循依赖倒置原则（DIP）：底层 core:data 声明接口与默认中文回退，
 * 由宿主 app 模块通过 Koin 注入基于静态 R.string 资源的本地化实现。
 * 彻底消除反射查找 getIdentifier 与资源缩减 (isShrinkResources) 盲区。
 */
interface TorrentNotificationStrings {
    val channelName: String
    val channelDesc: String
    val initialTitle: String
    val preparing: String
    val resolving: String
    val queued: String

    fun progress(
        percent: Int,
        speed: String,
    ): String

    val completedTitle: String
    val actionPause: String
    val actionCancel: String
}

object DefaultTorrentNotificationStrings : TorrentNotificationStrings {
    override val channelName: String = "磁力与视频下载"
    override val channelDesc: String = "展示后台磁力与视频文件下载进度"
    override val initialTitle: String = "正在下载资源"
    override val preparing: String = "准备下载…"
    override val resolving: String = "正在解析磁力元数据…"
    override val queued: String = "排队中…"

    override fun progress(
        percent: Int,
        speed: String,
    ): String = "$percent% · $speed"

    override val completedTitle: String = "下载完成"
    override val actionPause: String = "暂停"
    override val actionCancel: String = "取消"
}
