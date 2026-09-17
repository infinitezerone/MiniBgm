package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.util.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncManager : SyncManager {
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: Flow<Boolean> = _isSyncing

    var syncRequestCount = 0
        private set

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }

    override fun requestSync() {
        syncRequestCount++
    }
}
