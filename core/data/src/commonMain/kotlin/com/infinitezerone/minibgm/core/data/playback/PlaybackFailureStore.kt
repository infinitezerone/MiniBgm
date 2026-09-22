package com.infinitezerone.minibgm.core.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 一个播放源的近期健康度。
 *
 * [consecutiveFailures] 是**连续**失败次数：任一次成功即清零，所以它回答的是
 * "这个源现在还灵不灵"，而不是"历史上错过多少次"——后者会让一个早已修好的源长期背着污名。
 */
data class SourceHealth(
    val consecutiveFailures: Int,
    val lastReason: String? = null,
)

/**
 * 播放失败归因存储（进程内会话级）：
 * 播放器把失败按原因分类回传，来源列表据此将对应条目显示为"打不开"；
 * 同一地址恢复可播后自动清除。不落盘——来源是否可用是易变事实，不做持久断言。
 *
 * 除地址级的"打不开"标记外，另按**源**累计连续失败次数（[sourceHealth]）：
 * 同一个源连错几次就该在选源界面上被弱化，而用户下次打开 App 时站点是否已恢复无从得知，
 * 因此计数同样只活在本次会话——与上面同一条理由。
 */
class PlaybackFailureStore {
    private val _recentFailures = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _sourceHealth = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())

    /** key = 播放地址，value = 人已可读的失败原因 */
    val recentFailures: StateFlow<Map<String, String>> = _recentFailures.asStateFlow()

    /** key = 源标识（规则 id / 片单 id），value = 该源的连续失败状态 */
    val sourceHealth: StateFlow<Map<String, SourceHealth>> = _sourceHealth.asStateFlow()

    fun markFailed(
        url: String,
        reason: String,
        sourceId: String? = null,
    ) {
        if (!url.isBlank()) {
            _recentFailures.update { current ->
                val merged = current.toMutableMap()
                merged.remove(url)
                merged[url] = reason
                if (merged.size <= MAX_TRACKED) {
                    merged
                } else {
                    merged.entries
                        .toList()
                        .takeLast(MAX_TRACKED)
                        .associate { entry -> entry.key to entry.value }
                }
            }
        }
        // 地址为空也要记源级失败：源认不出来时丢掉的应当是"哪条地址"，而不是"哪个源不灵"
        if (sourceId.isNullOrBlank()) return
        _sourceHealth.update { current ->
            val previous = current[sourceId]
            current +
                (
                    sourceId to
                        SourceHealth(
                            consecutiveFailures = (previous?.consecutiveFailures ?: 0) + 1,
                            lastReason = reason,
                        )
                )
        }
    }

    fun markPlayable(
        url: String,
        sourceId: String? = null,
    ) {
        if (!url.isBlank()) {
            _recentFailures.update { it - url }
        }
        if (sourceId.isNullOrBlank()) return
        _sourceHealth.update { it - sourceId }
    }

    companion object {
        private const val MAX_TRACKED = 100
    }
}
