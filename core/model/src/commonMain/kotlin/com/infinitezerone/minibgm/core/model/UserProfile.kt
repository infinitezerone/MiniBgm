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
    /**
     * 注册时间（`/v0/me` 的 `reg_time`，ISO-8601 日期时间）。
     * 旧数据与拉取失败时为 null，UI 据此隐藏入站年限，不做兜底猜测。
     */
    @SerialName("reg_time")
    val regTime: String? = null,
) {
    val displayName: String
        get() = nickname.ifBlank { username }

    /**
     * 注册年份：从 [regTime] 里取首个 4 位连续数字。
     * 只做「提取 + 合理性区间」判断，不依赖具体日期格式——格式不符即返回 null（调用方隐藏该信息）。
     */
    val registeredYear: Int?
        get() {
            val raw = regTime.orEmpty()
            for (start in 0..(raw.length - 4)) {
                val year = raw.substring(start, start + 4).toIntOrNull() ?: continue
                if (raw[start].isDigit() && year in MIN_REGISTERED_YEAR..MAX_REGISTERED_YEAR) return year
            }
            return null
        }

    companion object {
        private const val MIN_REGISTERED_YEAR = 1900
        private const val MAX_REGISTERED_YEAR = 2100
    }
}
