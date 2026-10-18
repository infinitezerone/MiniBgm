package com.infinitezerone.minibgm.feature.user

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 观看记录一次性拉取上限：与仓库侧 `keepCount = 200` 的修剪水位对齐，避免后半段永远不可见 */
private const val PLAYBACK_HISTORY_LIMIT = 200

data class HistoryUiState(
    /** 原始分集记录（按观看时间倒序）；界面主体用 [groups]，这里保留给测试与后续统计 */
    val history: List<PlaybackHistory> = emptyList(),
    /** 按番剧聚合后的列表，顺序由组内最近观看时间决定 */
    val groups: List<HistoryGroup> = emptyList(),
    /** 已展开分集的番剧 ID；展开态放进 UiState 而不是 Composable 的 remember，滚出屏幕再回来不会丢 */
    val expandedSubjectIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
)

sealed interface HistoryUiEvent {
    data class ShowSnackbar(
        val message: String? = null,
        @StringRes val messageRes: Int = 0,
    ) : HistoryUiEvent
}

class HistoryViewModel(
    private val playbackHistoryRepository: PlaybackHistoryRepository,
) : ViewModel() {
    private val _events = Channel<HistoryUiEvent>(Channel.BUFFERED)
    val events: Flow<HistoryUiEvent> = _events.receiveAsFlow()

    private val expandedSubjectIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<HistoryUiState> =
        combine(
            playbackHistoryRepository.observeRecentHistory(limit = PLAYBACK_HISTORY_LIMIT),
            expandedSubjectIds,
        ) { history, expanded ->
            HistoryUiState(
                history = history,
                groups = buildHistoryGroups(history),
                expandedSubjectIds = expanded,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(isLoading = true),
        )

    /** 展开/收起某部番的分集列表；未知番剧（<=0）没有可展开的分组 */
    fun toggleSubjectExpanded(subjectId: Long) {
        if (subjectId <= 0L) return
        expandedSubjectIds.update { expanded ->
            if (subjectId in expanded) expanded - subjectId else expanded + subjectId
        }
    }

    fun deleteHistory(episodeId: Long) {
        if (episodeId <= 0L) return
        viewModelScope.launch {
            playbackHistoryRepository.deleteHistory(episodeId)
            _events.send(HistoryUiEvent.ShowSnackbar(messageRes = R.string.feature_user_history_deleted))
        }
    }

    /** 删除某部番的全部观看记录（聚合卡片上的整组删除） */
    fun deleteSubjectHistory(subjectId: Long) {
        if (subjectId <= 0L) return
        viewModelScope.launch {
            playbackHistoryRepository.deleteHistoryBySubject(subjectId)
            expandedSubjectIds.update { it - subjectId }
            _events.send(HistoryUiEvent.ShowSnackbar(messageRes = R.string.feature_user_history_subject_deleted))
        }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch {
            playbackHistoryRepository.clearAllHistory()
            expandedSubjectIds.value = emptySet()
            _events.send(HistoryUiEvent.ShowSnackbar(messageRes = R.string.feature_user_history_cleared))
        }
    }
}
