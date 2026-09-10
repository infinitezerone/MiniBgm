package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 人物/制作团队/声优详细信息模型（对应 GET /v0/persons/{id}）
 */
@Serializable
data class PersonDetail(
    val id: Long,
    val name: String,
    val type: Int = 1,
    val career: List<String> = emptyList(),
    val images: SubjectImages? = null,
    val img: String = "",
    val summary: String = "",
    val gender: String? = null,
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("birth_mon") val birthMon: Int? = null,
    @SerialName("birth_day") val birthDay: Int? = null,
    @SerialName("blood_type") val bloodType: Int? = null,
    val stat: CharacterStat? = null,
) {
    /** 最佳头像 URL */
    val bestAvatar: String
        get() {
            val raw = images?.bestImage?.ifBlank { img } ?: img
            return raw
                .replace("http://", "https://")
                .replace("lain.bgm.tv/pic/crt/l/", "lain.bgm.tv/r/400/pic/crt/l/")
        }

    /** 格式化职业身份标签，例如 "声优 / 歌手 / 音乐家" */
    val careerText: String
        get() =
            career
                .map {
                    when (it.lowercase()) {
                        "seiyu" -> "声优"
                        "artist" -> "歌手/艺术家"
                        "writer" -> "作家"
                        "illustrator" -> "插画师"
                        "actor" -> "演员"
                        else -> it
                    }
                }.joinToString(" · ")

    /** 格式化生日信息 */
    val birthdayText: String?
        get() =
            when {
                birthYear != null && birthMon != null && birthDay != null -> "${birthYear}年${birthMon}月${birthDay}日"
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
