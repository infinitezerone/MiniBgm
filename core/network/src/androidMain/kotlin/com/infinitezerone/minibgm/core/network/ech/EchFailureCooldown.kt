package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.TimeUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * ECH 握手失败后的冷却表。
 *
 * 握手失败的 host 仍被 DoH 标记为"支持 ECH"（那是对该域名能力的事实判断），
 * 于是每个请求都会先撞一次必然失败的握手再降级：RoutingEngine 要付两次 TLS，
 * ImageRoutingEngine 还要先把 4s 预算等满。冷却期内直接走明文，TTL 到期再试——
 * 既保留"网络恢复后自动回到 ECH"，又不会为一次网络抖动付永久代价。
 */
internal class EchFailureCooldown(
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = TimeUtils::nowEpochMillis,
) {
    private val markedAt = ConcurrentHashMap<String, Long>()

    /** 返回 true 表示这次标记让 host 真正进入（或重新进入）冷却，调用方可据此只记一条日志。 */
    fun mark(host: String): Boolean {
        val key = host.trim().lowercase()
        if (key.isBlank()) return false
        val now = nowMillis()
        val previous = markedAt.put(key, now)
        return previous == null || now - previous >= ttlMillis
    }

    /**
     * 命中规则与动态 ECH 注册一致：同一个注册域的子域共享这次失败
     * （同一 zone、同一批边缘节点），否则 [mark] 了 `acg.rip`，
     * `img.acg.rip` 仍会每个请求先撞一次必然失败的握手。
     */
    fun isCoolingDown(host: String): Boolean {
        val key = host.trim().lowercase()
        if (key.isBlank()) return false
        val now = nowMillis()
        var coolingDown = false
        for ((marked, at) in markedAt) {
            if (now - at >= ttlMillis) {
                markedAt.remove(marked, at)
                continue
            }
            if (key == marked || key.endsWith(".$marked")) coolingDown = true
        }
        return coolingDown
    }

    fun clear() {
        markedAt.clear()
    }
}
