package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 角色详细信息模型（对应 GET /v0/characters/{id}）
 */
@Serializable
data class CharacterDetail(
    val id: Long,
    val name: String,
    val type: Int = 1,
    val images: SubjectImages? = null,
    val summary: String = "",
    val gender: String? = null,
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("birth_mon") val birthMon: Int? = null,
    @SerialName("birth_day") val birthDay: Int? = null,
    @SerialName("blood_type") val bloodType: Int? = null,
    val stat: CharacterStat? = null,
) {
    /** 格式化生日信息，例如 "12月5日" */
    val birthdayText: String?
        get() =
            when {
                birthMon != null && birthDay != null -> "${birthMon}月${birthDay}日"
                birthMon != null -> "${birthMon}月"
                else -> null
            }

    /** 格式化性别文字 */
    val genderText: String?
        get() =
            when (gender?.lowercase()) {
                "female" -> "女"
                "male" -> "男"
                else -> gender
            }
}

/**
 * 角色/人物社区统计数据
 */
@Serializable
data class CharacterStat(
    val comments: Int = 0,
    val collects: Int = 0,
)
