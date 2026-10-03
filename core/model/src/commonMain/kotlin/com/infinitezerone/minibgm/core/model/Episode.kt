package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 格式化分集话数编号。
 * 整数转为整数字符串（如 1.0f -> "1"），小数保留（如 6.5f -> "6.5"），<= 0 默认返回 "1"。
 */
fun Float.toEpisodeLabel(): String {
    if (this <= 0f) return "1"
    val whole = toInt()
    return if (this == whole.toFloat()) whole.toString() else toString()
}

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
    /** 是否为正篇分集（type == 0） */
    val isMain: Boolean
        get() = type == 0

    /** 统一分集话数：优先使用 ep（若 > 0），否则回退到 sort */
    val episodeNumber: Float
        get() = if (ep > 0f) ep else sort

    /** 统一分集整型编号：供仅支持整数的打卡/计数逻辑使用 */
    val episodeInt: Int
        get() = episodeNumber.toInt()

    /** 统一分集话数格式化文本（如 "1", "12", "6.5"） */
    val formattedNumber: String
        get() = episodeNumber.toEpisodeLabel()

    val displayTitle: String
        get() = nameCn.ifBlank { name.ifBlank { "第 $formattedNumber 话" } }
}
