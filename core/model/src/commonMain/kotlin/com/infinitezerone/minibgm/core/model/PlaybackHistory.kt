package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 观看记录领域模型（按分集粒度聚合）：
 * 记录用户在内置播放器中观看到某部番剧某集的具体位置、时长与元数据，支持断点续播与历史管理。
 */
@Serializable
data class PlaybackHistory(
    val episodeId: Long,
    val subjectId: Long,
    val subjectName: String,
    val episodeName: String,
    val episodeSort: Float = 0f,
    val coverUrl: String = "",
    val streamUrl: String = "",
    /** 产生该直链的播放源标识；"direct" 表示伪源（历史直链/本地/BT），空表示旧数据 */
    val sourceId: String = "",
    /** 播放源展示名（规则名）；伪源为空，播放器面板回退到「默认直链」 */
    val sourceName: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** 观看进度百分比 (0.0 .. 1.0) */
    val progress: Float
        get() =
            if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

    /** 播放进度达到 90% 以上或距离结尾不足 30 秒视作已看完 */
    val isCompleted: Boolean
        get() =
            if (durationMs > 0L) {
                progress >= 0.9f || (durationMs - positionMs) in 1..30_000L
            } else {
                false
            }
}
