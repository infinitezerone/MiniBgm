package com.infinitezerone.minibgm.core.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 播放失败归因存储（进程内会话级）：
 * 播放器把失败按原因分类回传，来源列表据此将对应条目显示为"打不开"；
 * 同一地址恢复可播后自动清除。不落盘——来源是否可用是易变事实，不做持久断言。
 */
class PlaybackFailureStore {
    private val _recentFailures = MutableStateFlow<Map<String, String>>(emptyMap())

    /** key = 播放地址，value = 人已可读的失败原因 */
    val recentFailures: StateFlow<Map<String, String>> = _recentFailures.asStateFlow()

    fun markFailed(
        url: String,
        reason: String,
    ) {
        if (url.isBlank()) return
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

    fun markPlayable(url: String) {
        if (url.isBlank()) return
        _recentFailures.update { it - url }
    }

    companion object {
        private const val MAX_TRACKED = 100
    }
}
