package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserCollection(
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("subject_type") val subjectType: Int = 2,
    val userId: Long = 0L,
    val rate: Int = 0,
    val type: Int = 1,
    val comment: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("ep_status") val epStatus: Int = 0,
    @SerialName("vol_status") val volStatus: Int = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    val subject: Subject? = null,
)

enum class CollectionType(
    val value: Int,
    val label: String,
) {
    WISH(1, "想看"),
    COLLECT(2, "看过"),
    DOING(3, "在看"),
    ON_HOLD(4, "搁置"),
    DROPPED(5, "抛弃"),
    ;

    fun getVerb(subjectType: SubjectType): String =
        when (this) {
            WISH -> subjectType.actionWish
            DOING -> subjectType.actionDoing
            COLLECT -> subjectType.actionCollect
            ON_HOLD -> "搁置"
            DROPPED -> "抛弃"
        }

    companion object {
        fun fromValue(value: Int): CollectionType = entries.firstOrNull { it.value == value } ?: DOING
    }
}
