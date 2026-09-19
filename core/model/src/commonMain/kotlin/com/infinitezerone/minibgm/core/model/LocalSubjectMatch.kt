package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/** 本地别名索引命中：来自 Room 缓存的排期条目（bangumi-data 已同步的近期番剧） */
@Serializable
data class LocalSubjectMatch(
    val bgmId: Long,
    val title: String,
    val titleCn: String,
) {
    val displayName: String
        get() = titleCn.ifBlank { title }
}
