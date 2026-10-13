package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.database.dao.TorrentTaskDao
import com.infinitezerone.minibgm.core.database.entity.TorrentTaskEntity

class FakeTorrentTaskDao : TorrentTaskDao {
    private val storage = mutableMapOf<String, TorrentTaskEntity>()

    override suspend fun getAllTasks(): List<TorrentTaskEntity> =
        synchronized(storage) {
            storage.values.toList()
        }

    override suspend fun upsertTask(task: TorrentTaskEntity) {
        synchronized(storage) {
            storage[task.id] = task
        }
    }

    override suspend fun upsertTasks(tasks: List<TorrentTaskEntity>) {
        synchronized(storage) {
            tasks.forEach { storage[it.id] = it }
        }
    }

    override suspend fun deleteTask(taskId: String) {
        synchronized(storage) {
            storage.remove(taskId)
        }
    }
}
