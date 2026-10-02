package com.infinitezerone.minibgm.core.model

/** 即将播出的追番条目（供提醒与倒计时消费） */
data class UpcomingAiring(
    val subjectId: Long,
    val title: String,
    val titleCn: String,
    val episode: Int,
    /** UTC ISO-8601 时刻 */
    val airAtUtc: String,
    val kind: String,
    val coverUrl: String = "",
    val totalEpisodes: Int = 0,
) {
    val displayName: String
        get() = titleCn.ifBlank { title }

    /** 是否为全剧终 / 最终话 */
    val isFinale: Boolean
        get() = totalEpisodes > 0 && episode >= totalEpisodes
}
