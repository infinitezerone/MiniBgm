package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 播放规则界面状态
 */
data class PlaybackRulesUiState(
    val rules: List<PlaybackSourceRule> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * 播放规则单发事件
 */
sealed interface PlaybackRulesUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : PlaybackRulesUiEvent
}

/**
 * 自定义播放规则管理 ViewModel
 */
class PlaybackRulesViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val _events = Channel<PlaybackRulesUiEvent>(Channel.BUFFERED)
    val events: Flow<PlaybackRulesUiEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<PlaybackRulesUiState> =
        settingsRepository.playbackRules
            .map { rules ->
                PlaybackRulesUiState(rules = rules, isLoading = false)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PlaybackRulesUiState(isLoading = true),
            )

    @OptIn(ExperimentalUuidApi::class)
    fun addRule(
        name: String,
        urlTemplate: String,
        description: String = "",
    ) {
        val trimmedName = name.trim()
        val trimmedUrl = urlTemplate.trim()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) {
            sendSnackbar("规则名称和 URL 模板不能为空")
            return
        }
        viewModelScope.launch {
            val rule =
                PlaybackSourceRule(
                    id = Uuid.random().toString(),
                    name = trimmedName,
                    urlTemplate = trimmedUrl,
                    description = description.trim(),
                    isEnabled = true,
                )
            settingsRepository.addPlaybackRule(rule)
            sendSnackbar("已添加规则：$trimmedName")
        }
    }

    fun updateRule(
        id: String,
        name: String,
        urlTemplate: String,
        description: String = "",
    ) {
        val trimmedName = name.trim()
        val trimmedUrl = urlTemplate.trim()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) {
            sendSnackbar("规则名称和 URL 模板不能为空")
            return
        }
        viewModelScope.launch {
            val existing = uiState.value.rules.firstOrNull { it.id == id }
            val rule =
                PlaybackSourceRule(
                    id = id,
                    name = trimmedName,
                    urlTemplate = trimmedUrl,
                    description = description.trim(),
                    isEnabled = existing?.isEnabled ?: true,
                )
            settingsRepository.updatePlaybackRule(rule)
            sendSnackbar("已更新规则：$trimmedName")
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            val target = uiState.value.rules.firstOrNull { it.id == id }
            settingsRepository.deletePlaybackRule(id)
            sendSnackbar("已删除规则${if (target != null) "：${target.name}" else ""}")
        }
    }

    fun toggleRule(
        id: String,
        isEnabled: Boolean,
    ) {
        viewModelScope.launch {
            settingsRepository.togglePlaybackRule(id, isEnabled)
        }
    }

    fun importRulesFromJson(jsonStr: String) {
        val trimmed = jsonStr.trim()
        if (trimmed.isBlank()) {
            sendSnackbar("导入内容不能为空")
            return
        }
        viewModelScope.launch {
            runCatching {
                // 支持单条规则或规则数组解析
                val importedList =
                    if (trimmed.startsWith("[")) {
                        json.decodeFromString<List<PlaybackSourceRule>>(trimmed)
                    } else {
                        listOf(json.decodeFromString<PlaybackSourceRule>(trimmed))
                    }
                if (importedList.isEmpty()) {
                    sendSnackbar("未解析到有效规则")
                } else {
                    settingsRepository.importPlaybackRules(importedList)
                    sendSnackbar("成功导入 ${importedList.size} 条规则")
                }
            }.onFailure {
                sendSnackbar("规则解析失败，请检查 JSON 格式")
            }
        }
    }

    private fun sendSnackbar(message: String) {
        viewModelScope.launch {
            _events.send(PlaybackRulesUiEvent.ShowSnackbar(message))
        }
    }
}
