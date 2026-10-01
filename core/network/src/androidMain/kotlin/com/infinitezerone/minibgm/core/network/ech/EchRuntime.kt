package com.infinitezerone.minibgm.core.network.ech

import java.io.File

/**
 * ECH 运行时（DNS 策略 + 优选池 + 兜底探测）对外的唯一装配入口。
 *
 * 存在的理由有两个：
 * 1. `:app` 是独立模块，看不到本模块的 `internal`；把装配收敛成一个公开门面，
 *    池与探测器的实现细节就不必跟着放宽可见性。
 * 2. ECH 运行时有两块状态需要同一个 `filesDir`，散在 app 里各 init 一次容易漏——
 *    漏掉 [EchEdgePool] 的表现是"功能正常但重启即失忆"，这种缺陷很难在联调中被发现。
 */
object EchRuntime {
    /** @param filesDir 磁盘缓存目录（与 ECH config 缓存共用） */
    fun initialize(filesDir: File) {
        EchConfigStore.init(filesDir)
        EchEdgePool.init(filesDir)
    }

    /** 优选池的落盘是节流的，应用退到后台时补一次强制刷写。 */
    fun flush() {
        EchEdgePool.flush()
    }
}
