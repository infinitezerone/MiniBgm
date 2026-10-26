package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectCharacter(
    val id: Long,
    val name: String,
    val relation: String = "",
    @SerialName("role_name") val roleName: String = "",
    val images: SubjectImages? = null,
    val actors: List<SubjectPerson> = emptyList(),
) {
    /** 归一化真实角色定位：优先消费 Bangumi API 原生字段 `relation`（“主角”/“配角”/“客串”），回退到旧字段 `roleName` */
    val resolvedRoleName: String
        get() = relation.ifBlank { roleName }
}
