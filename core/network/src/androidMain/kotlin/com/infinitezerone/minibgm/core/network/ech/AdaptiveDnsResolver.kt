package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 安全 DNS 解析策略与 Cloudflare 边缘节点调度。
 *
 * 与改造前相比，这里**不再持有任何写死的边缘 IP**。原实现的两个 3 条硬编码池是"会腐烂的数据"：
 * App 自己就能用一次握手验证候选是否可用，写死的 IP 却只会随 CF 的调整与运营商的路由变化变成
 * 死候选——而池一旦全是死的，把它排得再聪明也没有意义。现在候选全部来自观测（见 [EchEdgePool]），
 * 池枯竭时由 [CloudflareEdgeProbe] 兜底。
 *
 * 候选的组装顺序（靠前者优先尝试）：
 * 1. ECH public_name 的解析结果——实测里"域名与自有 anycast IP 在 TCP 层被黑洞、而 public_name
 *    可达"是真实发生过的形态，此时唯一能用的入口就是它；
 * 2. 目标域名自身的解析结果（白名单判毒后）；
 * 3. 优选池按健康分排序的快照。
 */
internal object AdaptiveDnsResolver {
    private val logger = bgmLogger("Bgm/DnsResolver")

    /** 单次请求最多交给底层尝试的池内候选数，避免用坏节点摊薄请求预算。 */
    private const val MAX_POOL_CANDIDATES = 8

    /** public_name 之外的候选来源都可能为空，兜底探测需要能在后台跑，不阻塞请求。 */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backgroundProbeInFlight = AtomicBoolean(false)

    /**
     * 最近一次解析出的候选序列。用于在握手成功时反推"它前面那些候选没握上手"——
     * 见 [recordSuccess]。只保留少数几个 host，不需要淘汰策略。
     */
    private val resolvedCandidates = ConcurrentHashMap<String, List<String>>()

    /** 判断域名是否支持 ECH 握手。 */
    private fun isDomainOrSubdomain(
        host: String,
        domain: String,
    ): Boolean = host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)

    fun isEchEligible(host: String): Boolean =
        isDomainOrSubdomain(host, "bgm.tv") ||
            isDomainOrSubdomain(host, "bangumi.tv") ||
            isDomainOrSubdomain(host, "chii.in")

    /** 判断域名是否由 Cloudflare 提供服务。这些 host 才会拿到候选地址列表。 */
    fun isCloudflareHosted(host: String): Boolean = isEchEligible(host) || isDomainOrSubdomain(host, "anilist.co")

    /** 校验 IP 是否属于 Cloudflare 网段（判毒白名单的唯一依据）。 */
    fun isCloudflareIp(ip: String): Boolean = CloudflareRanges.contains(ip)

    /**
     * 判毒：走 Cloudflare 的域名，真实答案必然落在 CF 网段内，落不进去即视为被投毒。
     *
     * 改造前这条规则只作用于目标域名，public_name 域名走的是另一张 8 段硬编码黑名单——
     * 只有恰好命中那几段才算毒，漏网的投毒 IP 照收。而 public_name 恰恰是最容易被投毒的域名。
     * 现在两者共用同一条白名单，黑名单随之删除。
     */
    fun isPoisonedIp(ip: String): Boolean = !CloudflareRanges.contains(ip)

    private fun formatAddress(
        ip: String,
        port: Int,
    ): String = if (ip.contains(':')) "[$ip]:$port" else "$ip:$port"

    /**
     * 解析目标连接地址列表。返回 null 表示"不提供候选"——[com.infinitezerone.minibgm.core.network.ech.EchHttpClientEngine]
     * 会把 null 透传给 Rust 层，由它回退到自己的解析路径，这是最后的兜底。
     */
    suspend fun resolveTargetAddrs(
        host: String,
        port: Int,
    ): Array<String>? {
        if (!isCloudflareHosted(host)) {
            return null
        }

        val result = LinkedHashSet<String>()

        // **池排在 DNS 结果之前**：白名单只能判定"是不是 Cloudflare"，判不了"通不通"。
        // 实测（2026-10-01，移动网络）cloudflare-dns.com 的系统解析给出 104.16.249.249/248.249——
        // 货真价实的 CF 地址，却在 TCP 层被黑洞。它们若排在最前，会先把请求预算耗光，
        // 让后面已验证可达的池节点根本没机会被尝试。池里的节点是**实测握过手**的，可信度更高。
        EchEdgePool.snapshot().take(MAX_POOL_CANDIDATES).forEach { result.add(formatAddress(it, port)) }

        if (isEchEligible(host)) {
            // public_name 取自当前生效的 ECH 配置（内置种子）。它不再来自任何写死的域名常量。
            val publicName = EchConfigStore.activePublicName
            if (publicName == null) {
                logger.w { "[DNS] $host has no readable ECH public_name; skipping that entry point" }
            } else {
                val publicNameIps = resolveHostIps(publicName)
                if (publicNameIps.isNotEmpty()) {
                    EchEdgePool.absorb(publicNameIps)
                    publicNameIps.forEach { result.add(formatAddress(it, port)) }
                    logger.d { "[DNS] $host via ECH public_name $publicName -> $publicNameIps" }
                }
            }
        }

        val hostIps = resolveHostIps(host)
        if (hostIps.isNotEmpty()) {
            EchEdgePool.absorb(hostIps)
            hostIps.forEach { result.add(formatAddress(it, port)) }
            logger.d { "[DNS] $host system DNS -> $hostIps" }
        } else {
            logger.w { "[DNS] $host produced no Cloudflare-range answer (poisoned or unreachable)" }
        }

        if (result.isEmpty()) {
            // 池空 + 解析全毒：这次请求本来也无从发起，就地探测是唯一有意义的动作。
            // 探测自带硬预算（24 个候选 / 每个 1.2s / 并发 8），最坏情况可控。
            val recovered = CloudflareEdgeProbe.probe()
            recovered.forEach { result.add(formatAddress(it, port)) }
            logger.i { "[DNS] $host had zero candidates; probe recovered ${recovered.size}" }
        } else if (!EchEdgePool.hasFreshNodes()) {
            // 还有候选可用，就不让这次请求为探测买单：后台补池，下一次请求受益。
            launchBackgroundProbe()
        }

        if (result.isEmpty()) {
            logger.w { "[DNS] $host has no candidate after probe; delegating to the native resolver" }
            return null
        }

        val candidates = result.toList()
        resolvedCandidates[host] = candidates
        return candidates.toTypedArray()
    }

    /**
     * 真实握手成功。除了晋升该节点，还要消费 Rust 顺序尝试留下的隐含信息：
     * 成功位置**之前**的候选必然没握上手——`client.rs` 只在连接/握手失败时才 `continue` 到下一个
     * 候选，且预算耗尽时会直接整体报错而不会"跳过前面的去试后面的"。因此按序数前缀判负是可靠的，
     * 不需要 Rust 侧回传任何额外字段。
     *
     * 只对"连接/握手阶段"失败的降权：请求发出后才失败的路径永远走不到这里（那时没有 connected_addr），
     * 所以 4xx/5xx 这类与 IP 无关的失败不会误伤健康节点。
     */
    fun recordSuccess(
        host: String,
        address: String,
    ) {
        val succeededIp = EchEdgePool.normalizeIp(address) ?: return
        if (isCloudflareIp(succeededIp)) {
            EchEdgePool.recordSuccess(address)
        } else {
            // 池只承载 Cloudflare 边缘。App 会访问大量非 CF 服务（图片 CDN、AI 提供方），
            // 它们的 connectedAddr 与优选池无关，收进来只会占着候选名额。
            logger.d { "[DNS] $host connected via non-Cloudflare $succeededIp; not pooled" }
        }

        val candidates = resolvedCandidates[host].orEmpty()
        val index = candidates.indexOfFirst { EchEdgePool.normalizeIp(it) == succeededIp }
        if (index <= 0) return
        candidates.take(index).forEach { EchEdgePool.recordFailure(it) }
        logger.d { "[DNS] $host connected via $succeededIp; demoted $index preceding candidate(s)" }
    }

    /**
     * 完整请求失败（超时或连接彻底失败）。把为该域名准备的全部候选标记为失败一次，
     * 避免坏节点因从未成功而永久留在池顶。
     */
    fun recordFailure(host: String) {
        val candidates = resolvedCandidates[host].orEmpty()
        candidates.forEach { EchEdgePool.recordFailure(it) }
        if (candidates.isNotEmpty()) {
            logger.w { "[DNS] $host request failed completely; demoted ${candidates.size} candidate(s)" }
        }
    }

    private fun resolveHostIps(host: String): List<String> =
        runCatching { InetAddress.getAllByName(host).mapNotNull { it.hostAddress } }
            .onFailure { logger.d { "[DNS] resolution failed for $host: ${it.message}" } }
            .getOrDefault(emptyList())
            .filterNot { isPoisonedIp(it) }

    private fun launchBackgroundProbe() {
        if (!backgroundProbeInFlight.compareAndSet(false, true)) return
        backgroundScope.launch {
            try {
                val recovered = CloudflareEdgeProbe.probe()
                if (recovered.isNotEmpty()) {
                    logger.i { "[DNS] background probe refreshed the pool with ${recovered.size} node(s)" }
                }
            } finally {
                backgroundProbeInFlight.set(false)
            }
        }
    }

    /** 仅供测试：清空解析记忆，避免用例之间互相污染。 */
    fun resetForTest() {
        resolvedCandidates.clear()
    }
}
