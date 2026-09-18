package com.infinitezerone.minibgm.feature.subject.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 播放器界面 UI 状态
 */
data class PlayerUiState(
    val subjectId: Long = 0L,
    val episodeId: Long = 0L,
    val streamUrl: String = "",
    val isWatched: Boolean = false,
    val autoMarked: Boolean = false,
    val error: String? = null,
)

/**
 * 播放器一次性单发事件
 */
sealed interface PlayerUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : PlayerUiEvent

    data class MarkedWatched(
        val epNumber: Int,
    ) : PlayerUiEvent
}

/**
 * 播放器 ViewModel：
 * 维护播放会话状态、处理自动打卡标记（观看进度达到阈值或播放完成时触发）、
 * 鉴权校验（未登录拦截）及错误提示。
 */
class PlayerViewModel(
    val subjectId: Long,
    val episodeId: Long,
    val initialStreamUrl: String,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val failureStore: PlaybackFailureStore? = null,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            PlayerUiState(
                subjectId = subjectId,
                episodeId = episodeId,
                streamUrl = initialStreamUrl,
            ),
        )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerUiEvent>(Channel.BUFFERED)
    val events: Flow<PlayerUiEvent> = _events.receiveAsFlow()

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var hasTriggeredAutoMark = false

    /**
     * 当视频播放进度达到 85% 或播放结束时调用：
     * 自动向 Bangumi 提交该集看过标记（一次播放会话仅触发一次）。
     */
    fun markWatched(epNumber: Int) {
        if (hasTriggeredAutoMark || _uiState.value.isWatched) return
        if (!isLoggedIn.value) {
            return
        }

        hasTriggeredAutoMark = true
        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    isWatched = true,
                    epNumber = epNumber,
                )
            result
                .onSuccess {
                    _uiState.update { it.copy(isWatched = true, autoMarked = true) }
                    _events.send(PlayerUiEvent.MarkedWatched(epNumber))
                    _events.send(PlayerUiEvent.ShowSnackbar("已自动标记为看过（第 $epNumber 话）"))
                }.onError { _, message ->
                    hasTriggeredAutoMark = false
                    _events.send(PlayerUiEvent.ShowSnackbar(message))
                }
        }
    }

    fun updateStreamUrl(newUrl: String) {
        _uiState.update { it.copy(streamUrl = newUrl, error = null) }
    }

    /** 播放成功建立（STATE_READY）：清除该地址的失败标记 */
    fun onPlaybackReady() {
        failureStore?.markPlayable(_uiState.value.streamUrl)
    }

    fun onPlaybackError(message: String) {
        _uiState.update { it.copy(error = message) }
        failureStore?.markFailed(_uiState.value.streamUrl, message)
    }
}
