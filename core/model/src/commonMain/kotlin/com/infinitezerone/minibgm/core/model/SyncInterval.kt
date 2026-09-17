package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 播放源后台自动同步频率
 */
@Serializable
enum class SyncInterval(
    val hours: Long,
    val displayName: String,
) {
    DAILY(24, "每天"),
    WEEKLY(168, "每周（推荐）"),
    MONTHLY(720, "每月"),
    MANUAL_ONLY(0, "仅手动更新"),
}
