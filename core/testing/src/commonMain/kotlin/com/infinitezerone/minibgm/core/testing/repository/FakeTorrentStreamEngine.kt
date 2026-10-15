package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.magnetInfoHash
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 假 BT 流化引擎，用于测试。
 */
class FakeTorrentStreamEngine(
    initialInstalled: Boolean = false,
) : TorrentStreamEngine {
    var installed: Boolean = initialInstalled
    var shouldFailInstall: Boolean = false
    var shouldFailStream: Boolean = false

    var mockTargetFilePath: String? = null

    /** 置为 true 时 startStream 永不返回，用于验证起播等待超时守卫（死种不得无界挂起）。 */
    var hangOnStartStream: Boolean = false
    private var activeInfoHash: String? = null
    private var downloadSessionProtected: Boolean = false

    private val _playerSessionActive = MutableStateFlow(false)

    /** 播放器消费状态镜像；用 `setPlayerSessionActive` 驱动，仓库据此在播放结束后唤醒排队任务 */
    override val playerSessionActive: StateFlow<Boolean> = _playerSessionActive.asStateFlow()

    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val streamState: StateFlow<TorrentStreamState> = _streamState.asStateFlow()

    override fun getActiveTargetFilePath(): String? = mockTargetFilePath

    override fun getActiveInfoHash(): String? = activeInfoHash

    override fun setDownloadSessionProtected(protected: Boolean) {
        downloadSessionProtected = protected
    }

    override fun isDownloadSessionProtected(): Boolean = downloadSessionProtected

    override fun setPlayerSessionActive(active: Boolean) {
        _playerSessionActive.value = active
    }

    override fun isPlayerSessionActive(): Boolean = _playerSessionActive.value

    override fun releasePlayerSession() {
        _playerSessionActive.value = false
        if (!downloadSessionProtected) {
            activeInfoHash = null
            _streamState.value = TorrentStreamState.Idle
        }
    }

    fun emitStreamState(state: TorrentStreamState) {
        _streamState.value = state
    }

    override suspend fun isPluginInstalled(): Boolean = installed

    override suspend fun installPlugin(): Result<Unit> {
        if (shouldFailInstall) {
            _streamState.value = TorrentStreamState.Error("Mock install failed")
            return Result.failure(RuntimeException("Mock install failed"))
        }
        _streamState.value = TorrentStreamState.InstallingPlugin(0.5f)
        installed = true
        _streamState.value = TorrentStreamState.Idle
        return Result.success(Unit)
    }

    override suspend fun startStream(
        title: String,
        magnetUrl: String,
        torrentUrl: String?,
        saveDirPath: String?,
        episodeHint: String?,
    ): Result<String> {
        if (!installed) {
            _streamState.value = TorrentStreamState.PluginRequired()
            return Result.failure(IllegalStateException("Plugin not installed"))
        }
        if (shouldFailStream) {
            _streamState.value = TorrentStreamState.Error("Stream mock failed")
            return Result.failure(RuntimeException("Stream mock failed"))
        }
        if (hangOnStartStream) {
            awaitCancellation()
        }
        activeInfoHash = magnetInfoHash(magnetUrl).ifBlank { null }
        val url = "http://127.0.0.1:8899/stream?hash=fake"
        _streamState.value =
            TorrentStreamState.Ready(
                streamUrl = url,
                title = title,
                totalBytes = 100_000_000L,
            )
        return Result.success(url)
    }

    override suspend fun stopStream(force: Boolean) {
        if (!force && (downloadSessionProtected || _playerSessionActive.value)) {
            return
        }
        downloadSessionProtected = false
        _playerSessionActive.value = false
        activeInfoHash = null
        _streamState.value = TorrentStreamState.Idle
    }

    private var simulateUninstalled: Boolean = false

    override suspend fun setDebugSimulateUninstalled(simulateUninstalled: Boolean) {
        this.simulateUninstalled = simulateUninstalled
        if (simulateUninstalled) {
            installed = false
            downloadSessionProtected = false
            activeInfoHash = null
            _streamState.value = TorrentStreamState.Idle
        }
    }

    override fun isDebugSimulateUninstalled(): Boolean = simulateUninstalled
}
