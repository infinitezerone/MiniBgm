package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.model.TorrentStreamState
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

    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val streamState: StateFlow<TorrentStreamState> = _streamState.asStateFlow()

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
    ): Result<String> {
        if (!installed) {
            _streamState.value = TorrentStreamState.PluginRequired()
            return Result.failure(IllegalStateException("Plugin not installed"))
        }
        if (shouldFailStream) {
            _streamState.value = TorrentStreamState.Error("Stream mock failed")
            return Result.failure(RuntimeException("Stream mock failed"))
        }
        val url = "http://127.0.0.1:8899/stream?hash=fake"
        _streamState.value =
            TorrentStreamState.Ready(
                streamUrl = url,
                title = title,
                totalBytes = 100_000_000L,
            )
        return Result.success(url)
    }

    override suspend fun stopStream() {
        _streamState.value = TorrentStreamState.Idle
    }
}
