package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.crash.CrashLog
import com.infinitezerone.minibgm.core.data.crash.CrashLogRepository

/**
 * [CrashLogRepository] 的测试替身：默认"从未崩溃过"。
 *
 * [latest] 原样返回 [latestResult]；[clear] 只记次数并把结果清空——
 * 设置页的测试关心的是"点了有没有反应"，不是文件真的从磁盘上消失。
 */
class FakeCrashLogRepository : CrashLogRepository {
    var latestResult: CrashLog? = null

    var clearCallCount: Int = 0
        private set

    override suspend fun latest(): CrashLog? = latestResult

    override suspend fun clear() {
        clearCallCount++
        latestResult = null
    }
}
