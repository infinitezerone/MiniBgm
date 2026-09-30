package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 自适应安全 DNS 策略与动态节点调度中心
 *
 * 核心职责：
 * 1. 甄别域名是否需要启用 ECH（Encrypted Client Hello）
 * 2. 精准防投毒（Anti-Poisoning Filter）：校验 Cloudflare 托管站点的系统 DNS 是否属于合法 Cloudflare Anycast 网段；
 * 3. 域名池隔离：分别维护 Bangumi 与 AniList 的专属 Anycast 候选节点池，避免跨站错配引起超时；
 * 4. 动态自愈与降权：握手成功的健康节点动态置顶，失败节点动态沉底。
 */
object AdaptiveDnsResolver {
    private val logger = bgmLogger("Bgm/DnsResolver")

    // Bangumi 官方 Anycast 节点候选池 (api.bgm.tv, bgm.tv, lain.bgm.tv, chii.in)
    private val bgmAnycastPool =
        CopyOnWriteArrayList(
            listOf(
                "172.67.73.67",
                "104.26.9.23",
                "104.26.8.23",
            ),
        )

    // AniList CDN 官方 Anycast 节点候选池 (s4.anilist.co, anilist.co)
    private val anilistAnycastPool =
        CopyOnWriteArrayList(
            listOf(
                "172.67.71.232",
                "104.26.14.71",
                "104.26.15.71",
            ),
        )

    /**
     * 判断域名是否托管于 Cloudflare 并支持 ECH 握手穿透
     */
    fun isEchEligible(host: String): Boolean =
        host.endsWith("bgm.tv", ignoreCase = true) ||
            host.endsWith("bangumi.tv", ignoreCase = true) ||
            host.endsWith("chii.in", ignoreCase = true)

    /**
     * 判断域名是否由 Cloudflare CDN 提供服务
     */
    fun isCloudflareHosted(host: String): Boolean = isEchEligible(host) || host.endsWith("anilist.co", ignoreCase = true)

    /**
     * 校验 IP 是否属于 Cloudflare 官方公开的 IPv4 Anycast 广播网段
     */
    fun isCloudflareIp(ip: String): Boolean {
        if (ip.startsWith("172.")) {
            val second = ip.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            return second in 64..71 // 172.64.0.0/13
        }
        if (ip.startsWith("104.")) {
            val second = ip.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            return second in 16..31 // 104.16.0.0/12
        }
        if (ip.startsWith("162.158.") || ip.startsWith("162.159.")) return true
        if (ip.startsWith("108.162.")) return true
        if (ip.startsWith("198.41.")) return true
        if (ip.startsWith("188.114.")) return true
        if (ip.startsWith("190.93.")) return true
        if (ip.startsWith("197.234.")) return true
        if (ip.startsWith("141.101.")) return true
        return false
    }

    /**
     * 防投毒过滤器：若为 Cloudflare 托管站点，但解析出的 IP 不在 Cloudflare 官方网段中，则必为 GFW 伪造投毒 IP
     */
    fun isPoisonedIp(
        host: String,
        ip: String,
    ): Boolean {
        if (ip.isBlank() || ip == "0.0.0.0" || ip.startsWith("127.")) return true

        // RFC 1918 私有 / 保留网段
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) return true
        if (ip.startsWith("198.18.") || ip.startsWith("198.19.")) return true

        // 针对 Cloudflare 站点（如 Bangumi、AniList），非 Cloudflare IP 均视为污染
        if (isCloudflareHosted(host)) {
            return !isCloudflareIp(ip)
        }

        // 常规站点的经典 GFW 投毒网段过滤
        if (ip.startsWith("199.59.") || ip.startsWith("128.242.") || ip.startsWith("31.13.")) return true
        if (ip.startsWith("210.56.") || ip.startsWith("66.220.")) return true
        if (ip.startsWith("37.61.") || ip.startsWith("46.82.") || ip.startsWith("78.16.")) return true

        return false
    }

    /**
     * 自适应解析目标连接地址列表
     */
    fun resolveTargetAddrs(
        host: String,
        port: Int,
    ): Array<String>? {
        if (!isCloudflareHosted(host)) {
            return null
        }

        val resultList = ArrayList<String>()

        // 1. 优先尝试系统原生 DNS（若在海外或使用 VPN/代理，可获取到最近机房且延迟最低的 IP）
        try {
            val systemIps = InetAddress.getAllByName(host)
            val cleanIps = systemIps.mapNotNull { it.hostAddress }.filter { !isPoisonedIp(host, it) }

            if (cleanIps.isNotEmpty()) {
                logger.d { "Host $host resolved clean system IPs: $cleanIps" }
                for (ip in cleanIps) {
                    resultList.add("$ip:$port")
                }
            } else {
                logger.w { "Host $host system DNS was poisoned! IPs: ${systemIps.map { it.hostAddress }}" }
            }
        } catch (t: Throwable) {
            logger.w(t) { "Host $host system DNS resolution failed, fallback to dynamic anycast pool" }
        }

        // 2. 匹配专属动态 Anycast 健康候选池（保证即使本地 DNS 100% 污染也能秒连）
        val pool =
            if (host.endsWith("anilist.co", ignoreCase = true)) {
                anilistAnycastPool
            } else {
                bgmAnycastPool
            }

        for (ip in pool) {
            val addr = "$ip:$port"
            if (!resultList.contains(addr)) {
                resultList.add(addr)
            }
        }

        return resultList.toTypedArray()
    }

    /**
     * 当某节点成功完成握手与响应后，动态提升其优先级至首位（自愈缓存）
     */
    fun recordSuccess(
        host: String,
        addr: String,
    ) {
        val pureIp = addr.substringBefore(':')
        val pool =
            if (host.endsWith("anilist.co", ignoreCase = true)) {
                anilistAnycastPool
            } else {
                bgmAnycastPool
            }

        if (pool.contains(pureIp)) {
            val currentIdx = pool.indexOf(pureIp)
            if (currentIdx > 0) {
                pool.removeAt(currentIdx)
                pool.add(0, pureIp)
                logger.d { "Promoted healthy node $pureIp to top priority for $host" }
            }
        }
    }

    /**
     * 当某节点连接失败或超时后，动态降权至队尾，避免后续请求反复踩坑
     */
    fun recordFailure(
        host: String,
        addr: String,
    ) {
        val pureIp = addr.substringBefore(':')
        val pool =
            if (host.endsWith("anilist.co", ignoreCase = true)) {
                anilistAnycastPool
            } else {
                bgmAnycastPool
            }

        if (pool.contains(pureIp)) {
            val currentIdx = pool.indexOf(pureIp)
            if (currentIdx == 0 && pool.size > 1) {
                pool.removeAt(0)
                pool.add(pureIp)
                logger.d { "Demoted failing node $pureIp to end of pool for $host" }
            }
        }
    }
}
