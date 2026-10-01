package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.bgmLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Cloudflare 边缘 IP 的兜底主动探测 —— **整条链路里唯一新增网络行为的环节**。
 *
 * 只在"池与本地解析都给不出任何候选"时才会被调用（见 [AdaptiveDnsResolver.resolveTargetAddrs]）：
 * 池是缓存，缓存空了就必须有一个能自己长出候选的源头，否则删掉硬编码 IP 池等于在冷启动 + DNS 全
 * 投毒的环境里直接失去连接能力。
 *
 * 三重限流，保证它不会变成常驻扫描器：
 * 1. 触发条件——池里没有任何新鲜节点，或本次请求一个候选都凑不出来；
 * 2. 单飞 + 冷却——同一时刻只允许一次探测，两次之间至少间隔 [COOLDOWN_MILLIS]；
 * 3. 硬预算——最多 [MAX_CANDIDATES] 个候选、每个握手 [PROBE_TIMEOUT_MILLIS]，并发 [MAX_CONCURRENCY]。
 *
 * 探测做的是 **TCP 443 握手**而不是带宽测速：被运营商黑洞的正是"这个 IP 段能不能建连"，
 * 带宽与该故障无关却要贵一个数量级。度量的是建连耗时，顺带用作池内排序的输入。
 *
 * **为什么不为它设计费网络门控、也不做用户开关（2026-10-01 决策）**：
 * 探测只做 `Socket.connect`，**不发 TLS ClientHello、不带任何载荷**，单次约 250 字节；
 * 24 个候选约 6KB，而典型用户每天只在首次启动（池过期）时触发一次。
 * 更关键的是故障本身就发生在移动网络（见 AdaptiveDnsResolver 记录的实测），
 * 在计费网络下关掉探测等于**在故障最常出现的环境里关掉唯一的救援路径**——
 * 用"省 6KB"换"移动数据下彻底不可恢复"是亏的。它也**不带来任何增量隐私成本**：
 * 连的仍是 Cloudflare 边缘，而这个 App 的每个请求本来就在连 Cloudflare。
 * 而做成开关则更糟：关掉后若池失效，连通性会静默退化，症状表现为"番剧加载不出来"，
 * 没人会把它跟一个冷门网络开关联系起来。因此这里刻意不引入任何网络类型判定。
 */
internal object CloudflareEdgeProbe {
    private const val PROBE_PORT = 443
    private const val PROBE_TIMEOUT_MILLIS = 1_200
    private const val MAX_CANDIDATES = 24
    private const val MAX_CONCURRENCY = 8
    private const val COOLDOWN_MILLIS = 10L * 60 * 1000

    private val logger = bgmLogger("Bgm/EchProbe")
    private val inFlight = AtomicBoolean(false)
    private val lastProbeAtMillis = AtomicLong(0L)

    /** 采样 + 探测，返回按建连耗时升序的存活地址；未通过限流判定时返回空列表。 */
    suspend fun probe(
        now: Long = TimeUtils.nowEpochMillis(),
        random: Random = Random.Default,
    ): List<String> {
        if (!beginProbe(now)) return emptyList()
        return try {
            val candidates =
                CloudflareRanges.sampleCandidates(
                    count = MAX_CANDIDATES,
                    random = random,
                    exclude = EchEdgePool.snapshot(now).toSet(),
                )
            if (candidates.isEmpty()) return emptyList()

            val startedAtNanos = System.nanoTime()
            val reachable = probeAll(candidates)
            val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
            logger.i {
                "[EDGE_PROBE:DONE] ${reachable.size}/${candidates.size} reachable in ${elapsedMillis}ms"
            }
            // 入池时只登记为"已观测"而非"已成功"：TCP 建连成立不等于 ECH 握手成立，
            // 真正的晋升留给首次成功请求去触发。
            if (reachable.isNotEmpty()) EchEdgePool.absorb(reachable, now)
            reachable
        } finally {
            inFlight.set(false)
        }
    }

    private suspend fun probeAll(candidates: List<String>): List<String> =
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENCY)
            candidates
                .map { ip ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val beganAtNanos = System.nanoTime()
                            if (probeOne(ip)) ip to (System.nanoTime() - beganAtNanos) else null
                        }
                    }
                }.awaitAll()
                .filterNotNull()
                .sortedBy { it.second }
                .map { it.first }
        }

    private fun probeOne(ip: String): Boolean =
        runCatching {
            Socket().use { socket ->
                // 字面量 IP 走 InetSocketAddress(String, Int) 不会触发 DNS 解析
                socket.connect(InetSocketAddress(ip, PROBE_PORT), PROBE_TIMEOUT_MILLIS)
            }
        }.isSuccess

    /**
     * 限流判定。任何一条不满足都直接放弃本次探测——放弃是安全的：调用方本来就没有候选，
     * 这里少探一次只会让这次请求按原有失败路径收场，不会引入新的失败。
     */
    private fun beginProbe(now: Long): Boolean {
        if (!inFlight.compareAndSet(false, true)) {
            logger.d { "[EDGE_PROBE:SKIP] another probe is already in flight" }
            return false
        }
        val previous = lastProbeAtMillis.get()
        if (previous != 0L && now - previous < COOLDOWN_MILLIS) {
            inFlight.set(false)
            logger.d { "[EDGE_PROBE:SKIP] cooling down for another ${COOLDOWN_MILLIS - (now - previous)}ms" }
            return false
        }
        // 先记账再探测：探测本身失败也要占用冷却，否则弱网下会退化成连续扫描
        lastProbeAtMillis.set(now)
        return true
    }
}
