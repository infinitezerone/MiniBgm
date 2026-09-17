package com.infinitezerone.minibgm.core.data.util

import com.infinitezerone.minibgm.core.common.UserDataClearable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 集中编排私有数据清理流程的调度器。
 *
 * 遵循「清理责任去中心化，清理编排集中化」设计模式：
 * 各业务模块实现 [UserDataClearable] 自行管理私有数据表，
 * 本调度器负责在账号登出或全局注销时并发编排并容错执行所有清理任务。
 */
class UserDataCleaner(
    private val clearables: List<UserDataClearable>,
) {
    /** 并发清理指定用户的私有数据 */
    suspend fun clear(userId: Long) =
        coroutineScope {
            clearables
                .map { clearable ->
                    async { runCatching { clearable.clearUserData(userId) } }
                }.awaitAll()
        }

    /** 并发清理所有用户的私有数据 */
    suspend fun clearAll() =
        coroutineScope {
            clearables
                .map { clearable ->
                    async { runCatching { clearable.clearAllUserData() } }
                }.awaitAll()
        }
}
