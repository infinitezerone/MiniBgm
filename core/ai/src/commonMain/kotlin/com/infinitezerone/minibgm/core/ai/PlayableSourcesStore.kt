package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 暂存 AI 智能体工具执行时解析出的播放资源列表。
 * 使客户端可直接从 Store 获取工具产物，无需模型充当 JSON 搬运工。
 */
class PlayableSourcesStore {
    private val _sources = MutableStateFlow<PlayableEpisodeList?>(null)
    val sources: StateFlow<PlayableEpisodeList?> = _sources.asStateFlow()

    fun set(list: PlayableEpisodeList) {
        _sources.value = list
    }

    fun pop(): PlayableEpisodeList? {
        // 读与清必须经 update 原子完成：分开的 get+set 与 StateFlow 注释的线程安全承诺不符，
        // 并发读会双取或丢数据
        var popped: PlayableEpisodeList? = null
        _sources.update { current ->
            popped = current
            null
        }
        return popped
    }

    fun clear() {
        _sources.value = null
    }
}
