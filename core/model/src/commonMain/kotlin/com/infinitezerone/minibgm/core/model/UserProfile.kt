package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAvatar(
    val large: String = "",
    val medium: String = "",
    val small: String = "",
) {
    val bestAvatar: String
        get() = large.ifBlank { medium.ifBlank { small } }
}

@Serializable
data class UserProfile(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    @SerialName("user_group")
    val userGroup: Int = 0,
    val avatar: UserAvatar? = null,
    val sign: String = "",
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}
