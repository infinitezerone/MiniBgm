package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectCharacter(
    val id: Long,
    val name: String,
    @SerialName("role_name") val roleName: String = "",
    val images: SubjectImages? = null,
    val actors: List<SubjectPerson> = emptyList(),
)
