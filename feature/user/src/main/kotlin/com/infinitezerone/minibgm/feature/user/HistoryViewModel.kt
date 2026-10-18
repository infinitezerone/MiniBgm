package com.infinitezerone.minibgm.feature.user

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 观看记录列表一次性拉取上限：与仓库侧 `keepCount = 200` 的修剪水位保持同量级 */
private const val PLAYBACK_HISTORY_LIMIT = 100

data class HistoryUiState(
    val history: List<PlaybackHistory> = emptyList(),
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

    val uiState: StateFlow<HistoryUiState> =
        playbackHistoryRepository
            .observeRecentHistory(limit = PLAYBACK_HISTORY_LIMIT)
            .map { history ->
                HistoryUiState(
                    history = history,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HistoryUiState(isLoading = true),
            )

    fun deleteHistory(episodeId: Long) {
        if (episodeId <= 0L) return
        viewModelScope.launch {
            playbackHistoryRepository.deleteHistory(episodeId)
            _events.send(HistoryUiEvent.ShowSnackbar(messageRes = R.string.feature_user_history_deleted))
        }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch {
            playbackHistoryRepository.clearAllHistory()
            _events.send(HistoryUiEvent.ShowSnackbar(messageRes = R.string.feature_user_history_cleared))
        }
    }
}
