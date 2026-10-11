package com.infinitezerone.minibgm.core.model

/**
 * 弹幕匹配状态：
 * - [USER_OVERRIDE] 用户手动挑选过弹幕分集（覆盖表命中，真值最高）；
 * - [AUTO_MATCHED] 自动匹配到高置信分集（返回分集的集数与 bangumi episodeSort 对得上）；
 * - [FALLBACK] 无法置信匹配，回退默认取值（首个含分集的条目的第一集），候选透出待用户纠正。
 */
enum class DanmakuMatchState {
    USER_OVERRIDE,
    AUTO_MATCHED,
    FALLBACK,
}

/**
 * 弹幕分集候选：dandanplay search/episodes 返回的某个番剧条目下的某一集。
 */
data class DanmakuEpisodeCandidate(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeTitle: String,
)

/**
 * 弹幕分集匹配结果。
 *
 * @property chosen 实际采用的候选；覆盖表命中但候选列表已变化时可能为 null（仍按记录的 episodeId 取弹幕）
 * @property candidates 全部候选，供用户纠正挑选
 * @property state 匹配状态
 */
data class DanmakuMatch(
    val chosen: DanmakuEpisodeCandidate?,
    val candidates: List<DanmakuEpisodeCandidate>,
    val state: DanmakuMatchState,
)

/**
 * 弹幕取数结果：弹幕条目 + 匹配元信息。匹配失败时 [match] 为 null（历史 Fail-Open 行为）。
 */
data class DanmakuResult(
    val items: List<DanmakuItem>,
    val match: DanmakuMatch?,
)
