package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    val id: Long,
    val type: Int = 0,
    val sort: Float = 0f,
    val ep: Float = 0f,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String = "",
    val duration: String = "",
    val airdate: String = "",
    val comment: Int = 0,
    val desc: String = "",
) {
    val displayTitle: String
        get() = nameCn.ifBlank { name.ifBlank { "第 ${sort.toInt()} 话" } }
}
