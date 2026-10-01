package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.bgmLogger
import java.io.File

/**
 * Cloudflare 边缘节点优选池 —— **池是缓存，不是常量**。
 *
 * 为什么候选只能来自观测：ECH 把真实 SNI 加密进 inner ClientHello，外层发出的 SNI 是 config 里的
 * public_name，Cloudflare 解密后按 inner SNI 路由。于是**任意一个可达的边缘 IP 都能承载任意
 * CF 托管域名**，IP 与域名的绑定关系对路由不再重要，唯一重要的是"这个 IP 从当前网络能不能握上
 * 手"。而这个答案只取决于用户所处的运营商与地区——任何写死的 IP 都必然随时间和地区腐烂，
 * 这也是旧实现里那份 3 条硬编码池注定要换掉的原因。
 *
 * 候选来源（都不产生额外网络行为）：
 * 1. [absorb]：本地 DNS 自己就会做的那次解析结果，白名单判毒后沉淀；
 * 2. [recordSuccess]：真实请求握手成功回传的 connectedAddr——质量最高的一类候选。
 *
 * 排序是纯本地的健康分，不需要任何测速：失败次数少的优先，同失败次数下最近成功过的优先。
 * 失败信号由 [AdaptiveDnsResolver] 从"成功节点前面的那些候选必然没能握上手"推导而来
 * （Rust 侧只在连接/握手阶段失败时才顺序尝试下一个候选，因此成功位置之前的候选可以安全判负）。
 *
 * 落盘是节流的：状态变化远多于值得写盘的变化，代价是进程死亡最多丢掉最后 15 秒的排序信息。
 */
internal object EchEdgePool {
    private const val FILE_NAME = "ech_edge_pool.txt"
    private const val FORMAT_HEADER = "ech-edge-pool-v1"
    private const val FIELD_SEPARATOR = '|'

    /** 池上限。超出后按健康分淘汰最差的——池是"当前网络可用候选"的快照，不是通讯录。 */
    private const val MAX_NODES = 32
    private const val MAX_CONSECUTIVE_FAILURES = 999

    /** 超过这个时间没成功过即视为不新鲜，触发兜底探测；节点本身不删除（可能只是暂时封禁）。 */
    private const val STALE_AFTER_MILLIS = 6L * 60 * 60 * 1000

    /** 长期既没成功也没被观测到的节点直接丢弃，避免池被历史噪音永久占满。 */
    private const val FORGET_AFTER_MILLIS = 14L * 24 * 60 * 60 * 1000

    /** 连续失败到这个数就不再算"新鲜节点"，此时应启动兜底探测。 */
    private const val UNHEALTHY_FAILURE_THRESHOLD = 3

    private const val PERSIST_THROTTLE_MILLIS = 15_000L

    private val logger = bgmLogger("Bgm/EchEdgePool")
    private val lock = Any()

    private data class Node(
        val ip: String,
        val lastSuccessAtMillis: Long,
        val lastObservedAtMillis: Long,
        val consecutiveFailures: Int,
    )

    private val nodes = LinkedHashMap<String, Node>()
    private var poolFile: File? = null
    private var lastPersistAtMillis = 0L
    private var dirty = false

    /** 挂载磁盘缓存目录；未调用时池只在内存中生效（功能不受影响，只是重启即失忆）。 */
    fun init(filesDir: File) {
        synchronized(lock) {
            poolFile = File(filesDir, FILE_NAME)
            nodes.clear()
            loadLocked()
        }
    }

    /** 按健康分排序的候选纯 IP（不含端口）。优先返回未熔断节点，避免坏节点霸占前排拖垮请求。 */
    fun snapshot(now: Long = TimeUtils.nowEpochMillis()): List<String> =
        synchronized(lock) {
            val ordered = orderLocked(now)
            val healthyCandidates = ordered.filter { healthRank(it, now) < 4 }
            if (healthyCandidates.isNotEmpty()) {
                healthyCandidates.map { it.ip }
            } else {
                ordered.map { it.ip }
            }
        }

    /** 池里是否存在"最近成功过且未被连续判死"的节点。为 false 时应启动兜底探测。 */
    fun hasFreshNodes(now: Long = TimeUtils.nowEpochMillis()): Boolean =
        synchronized(lock) {
            nodes.values.any { !isStale(it, now) && it.consecutiveFailures < UNHEALTHY_FAILURE_THRESHOLD }
        }

    /**
     * 吸收一批候选（DNS 解析结果或探测结果）。未知 IP 以"待验证"姿态入池，已知 IP 只刷新观测
     * 时间——观测不等于成功，不能因此提升健康分（否则一个被投毒的高频域名会顶掉真正的可用节点）。
     */
    fun absorb(
        candidates: Collection<String>,
        now: Long = TimeUtils.nowEpochMillis(),
    ) {
        if (candidates.isEmpty()) return
        synchronized(lock) {
            var changed = false
            candidates.forEach { raw ->
                val ip = admittedIp(raw) ?: return@forEach
                val existing = nodes[ip]
                if (existing == null) {
                    nodes[ip] =
                        Node(
                            ip = ip,
                            lastSuccessAtMillis = 0L,
                            lastObservedAtMillis = now,
                            consecutiveFailures = 0,
                        )
                    changed = true
                } else if (existing.lastObservedAtMillis != now) {
                    nodes[ip] = existing.copy(lastObservedAtMillis = now)
                    changed = true
                }
            }
            if (evictLocked(now)) changed = true
            // 新候选是稀缺资源，吸收后立即落盘，不走节流
            if (changed) persistLocked(now, force = true)
        }
    }

    /** 真实握手成功：清零失败计数并刷新成功时间——这是唯一能提升健康分的信号。 */
    fun recordSuccess(
        address: String,
        now: Long = TimeUtils.nowEpochMillis(),
    ) {
        val ip = admittedIp(address) ?: return
        synchronized(lock) {
            val next =
                Node(
                    ip = ip,
                    lastSuccessAtMillis = now,
                    lastObservedAtMillis = now,
                    consecutiveFailures = 0,
                )
            if (nodes[ip] == next) return
            nodes[ip] = next
            evictLocked(now)
            persistLocked(now, force = false)
        }
    }

    /** 连接/握手失败：只累加失败计数，不删除节点——被临时封禁的 IP 恢复后仍是好候选。 */
    fun recordFailure(
        address: String,
        now: Long = TimeUtils.nowEpochMillis(),
    ) {
        val ip = normalizeIp(address) ?: return
        synchronized(lock) {
            val existing = nodes[ip] ?: return
            nodes[ip] =
                existing.copy(
                    lastObservedAtMillis = now,
                    consecutiveFailures = (existing.consecutiveFailures + 1).coerceAtMost(MAX_CONSECUTIVE_FAILURES),
                )
            persistLocked(now, force = false)
        }
    }

    /** 强制落盘（应用退到后台等时机调用），补齐节流窗口内尚未写出的变更。 */
    fun flush() {
        synchronized(lock) {
            if (!dirty && nodes.isEmpty()) return
            val file = poolFile ?: return
            EchAtomicFile.write(file, serializeLocked(TimeUtils.nowEpochMillis()))
            dirty = false
        }
    }

    fun size(): Int = synchronized(lock) { nodes.size }

    /** 仅供测试：清空池与落盘状态，避免用例之间互相污染。 */
    fun resetForTest() {
        synchronized(lock) {
            nodes.clear()
            poolFile = null
            lastPersistAtMillis = 0L
            dirty = false
        }
    }

    // ── 内部实现 ──

    private fun loadLocked() {
        val file = poolFile ?: return
        val text = EchAtomicFile.read(file) ?: return
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != FORMAT_HEADER) {
            logger.w { "[EDGE_POOL:LOAD] unexpected header, starting from an empty pool" }
            return
        }
        lines.drop(1).forEach { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size != 4) return@forEach
            val ip = normalizeIp(parts[0]) ?: return@forEach
            val lastSuccess = parts[1].toLongOrNull() ?: return@forEach
            val lastObserved = parts[2].toLongOrNull() ?: return@forEach
            val failures = parts[3].toIntOrNull() ?: return@forEach
            nodes[ip] =
                Node(
                    ip = ip,
                    lastSuccessAtMillis = lastSuccess.coerceAtLeast(0L),
                    lastObservedAtMillis = lastObserved.coerceAtLeast(0L),
                    consecutiveFailures = failures.coerceIn(0, MAX_CONSECUTIVE_FAILURES),
                )
        }
        logger.i { "[EDGE_POOL:LOAD] restored ${nodes.size} node(s) from disk cache" }
    }

    private fun serializeLocked(now: Long): String =
        buildString {
            append(FORMAT_HEADER).append('\n')
            orderLocked(now).forEach { node ->
                append(node.ip)
                    .append(FIELD_SEPARATOR)
                    .append(node.lastSuccessAtMillis)
                    .append(FIELD_SEPARATOR)
                    .append(node.lastObservedAtMillis)
                    .append(FIELD_SEPARATOR)
                    .append(node.consecutiveFailures)
                    .append('\n')
            }
        }

    private fun persistLocked(
        now: Long,
        force: Boolean,
    ) {
        val file = poolFile ?: return
        if (!force && now - lastPersistAtMillis < PERSIST_THROTTLE_MILLIS) {
            dirty = true
            return
        }
        EchAtomicFile.write(file, serializeLocked(now))
        lastPersistAtMillis = now
        dirty = false
    }

    private fun orderLocked(now: Long): List<Node> =
        nodes.values.sortedWith(
            compareBy<Node> { healthRank(it, now) }
                .thenBy { it.consecutiveFailures }
                .thenByDescending { it.lastSuccessAtMillis }
                .thenByDescending { it.lastObservedAtMillis },
        )

    /**
     * 健康分层：
     * Rank 0: 黄金节点 —— 曾成功、0 失败且未过期；
     * Rank 1: 优质新节点 —— 从未失败的新发现候选（0 失败）；
     * Rank 2: 轻度抖动节点 —— 曾成功过，仅连续失败 1~2 次；
     * Rank 3: 待观察新节点 —— 未曾成功但仅失败 1~2 次；
     * Rank 4: 熔断隔离节点 —— 连续失败 >= 3 次（无论历史记录如何，一律打入冷宫）。
     */
    private fun healthRank(
        node: Node,
        now: Long,
    ): Int =
        when {
            node.lastSuccessAtMillis > 0 && node.consecutiveFailures == 0 && !isStale(node, now) -> 0
            node.consecutiveFailures == 0 -> 1
            node.lastSuccessAtMillis > 0 && node.consecutiveFailures < UNHEALTHY_FAILURE_THRESHOLD -> 2
            node.consecutiveFailures < UNHEALTHY_FAILURE_THRESHOLD -> 3
            else -> 4
        }

    private fun isStale(
        node: Node,
        now: Long,
    ): Boolean = now - node.lastSuccessAtMillis > STALE_AFTER_MILLIS

    private fun evictLocked(now: Long): Boolean {
        var changed = false
        val iterator = nodes.entries.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next().value
            val neverSeenRecently = now - node.lastObservedAtMillis > FORGET_AFTER_MILLIS
            val neverSucceededRecently = now - node.lastSuccessAtMillis > FORGET_AFTER_MILLIS
            // 连续失败 10 次及以上的死节点直接清除，绝不留存拖垮调度
            val permanentlyDead = node.consecutiveFailures >= 10
            if ((neverSeenRecently && neverSucceededRecently) || permanentlyDead) {
                iterator.remove()
                changed = true
            }
        }
        if (nodes.size > MAX_NODES) {
            orderLocked(now).drop(MAX_NODES).forEach { node ->
                nodes.remove(node.ip)
                changed = true
            }
        }
        return changed
    }

    /**
     * 准入判定：只有落在 Cloudflare 网段内的地址才允许进池。
     *
     * 这道闸必须设在池里而不是调用方——`EchHttpClientEngine` 对**所有** host 都会回传
     * connectedAddr，而 App 还会访问大量与 Cloudflare 无关的服务（图片 CDN、AI 提供方、
     * 第三方站点）。真机实测里就出现过把 Fastly 的 151.101.1.229 收进池的情况：它占着候选
     * 名额，却永远不会对目标域名生效。
     *
     * 判定只看**地址本身**、不看是哪个 host 连上的：任意可达的 CF 边缘都能承载任意 CF 托管
     * 域名（见类注释），所以哪怕是访问别的服务时顺带发现的 CF 地址，同样是有价值的候选。
     */
    private fun admittedIp(address: String): String? = normalizeIp(address)?.takeIf { CloudflareRanges.contains(it) }

    /** 把 `ip:port` / `[v6]:port` 归一成纯地址；无法识别时返回 null。 */
    internal fun normalizeIp(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null
        val pure =
            when {
                trimmed.startsWith('[') -> trimmed.substringBefore(']').removePrefix("[")
                trimmed.count { it == ':' } == 1 -> trimmed.substringBeforeLast(':')
                else -> trimmed
            }
        return pure.trim().takeIf { it.isNotEmpty() }
    }
}
