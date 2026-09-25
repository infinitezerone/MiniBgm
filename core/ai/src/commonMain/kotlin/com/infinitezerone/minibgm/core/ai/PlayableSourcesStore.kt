package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val current = _sources.value
        _sources.value = null
        return current
    }

    fun clear() {
        _sources.value = null
    }
}
