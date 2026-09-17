package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectRelation(
    val id: Long,
    val type: Int = 2,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String = "",
    val relation: String = "",
    val images: SubjectImages? = null,
    val rating: Rating? = null,
    @SerialName("rating_score") val ratingScore: Double = 0.0,
) {
    val displayName: String
        get() = nameCn.ifBlank { name }

    val score: Double
        get() = if (ratingScore > 0.0) ratingScore else (rating?.score ?: 0.0)
}
