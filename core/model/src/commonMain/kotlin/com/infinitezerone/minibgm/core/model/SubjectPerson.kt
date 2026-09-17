package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SubjectPerson(
    val id: Long,
    val name: String,
    val type: Int = 0,
    val relation: String = "",
    val images: SubjectImages? = null,
)
