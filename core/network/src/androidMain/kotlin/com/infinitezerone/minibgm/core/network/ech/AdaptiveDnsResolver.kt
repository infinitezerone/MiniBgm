package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 自适应安全 DNS 策略与动态节点调度中心
 *
 * 核心职责：
 * 1. 甄别域名是否需要启用 ECH（Encrypted Client Hello）
 * 2. 系统 DNS 洁净度检测（Anti-Poisoning Filter）：若系统 DNS 未被污染（海外/VPN），优先直连就近最优 IP；
 * 3. 动态容灾与健康自愈（Dynamic Health Discovery）：当系统 DNS 遭遇 GFW 投毒或解析失败时，动态调度 Anycast 候选节点；
 * 4. 故障自动降权与自愈重排：成功节点置顶，失败节点后移，杜绝硬编码死链与重复超时。
 */
object AdaptiveDnsResolver {
    private val logger = bgmLogger("Bgm/DnsResolver")

    // Cloudflare Anycast 官方广播网段初始优选探测池（动态维护，无需固定顺序）
    private val cloudflareAnycastPool =
        CopyOnWriteArrayList(
            listOf(
                "172.67.73.67",
                "104.26.9.23",
                "172.67.71.232",
                "104.26.14.71",
                "104.26.8.23",
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
     * 判断域名是否由 Cloudflare CDN 提供服务（需防 DNS 污染）
     */
    fun isCloudflareHosted(host: String): Boolean = isEchEligible(host) || host.endsWith("anilist.co", ignoreCase = true)

    /**
     * 防投毒过滤器：检测 IP 是否为 GFW 经典伪造 IP 或保留网段
     */
    fun isPoisonedIp(ip: String): Boolean {
        if (ip.isBlank() || ip == "0.0.0.0" || ip.startsWith("127.")) return true

        // RFC 1918 私有 / 保留网段
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) return true
        if (ip.startsWith("198.18.") || ip.startsWith("198.19.")) return true

        // GFW 经典伪造投毒 IP 网段（Twitter, Facebook, 随机不可达地址池）
        if (ip.startsWith("199.59.")) return true // Twitter 投毒网段（如 199.59.150.43）
        if (ip.startsWith("128.242.")) return true // 经典 GFW 投毒网段（如 128.242.240.29）
        if (ip.startsWith("31.13.")) return true // Facebook 投毒网段（如 31.13.76.65）
        if (ip.startsWith("37.61.") || ip.startsWith("46.82.") || ip.startsWith("78.16.")) return true
        if (ip.startsWith("93.46.") || ip.startsWith("243.185.") || ip.startsWith("8.7.198.")) return true

        return false
    }

    /**
     * 自适应解析目标连接地址列表
     *
     * @return 优先连接地址列表（如 ["172.67.73.67:443", ...]），若为通用域名且无污染则返回 null（由 Rust 引擎直接走原生解析）
     */
    fun resolveTargetAddrs(
        host: String,
        port: Int,
    ): Array<String>? {
        if (!isCloudflareHosted(host)) {
            return null
        }

        val resultList = ArrayList<String>()

        // 1. 优先尝试系统原生 DNS（若在海外或挂了代理，可获取到最近机房且延迟最低的 IP）
        try {
            val systemIps = InetAddress.getAllByName(host)
            val cleanIps = systemIps.mapNotNull { it.hostAddress }.filter { !isPoisonedIp(it) }

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

        // 2. 补充动态 Anycast 健康候选池（保证即使本地 DNS 100% 污染也能秒连）
        for (ip in cloudflareAnycastPool) {
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
    fun recordSuccess(ip: String) {
        val pureIp = ip.substringBefore(':')
        if (cloudflareAnycastPool.contains(pureIp)) {
            val currentIdx = cloudflareAnycastPool.indexOf(pureIp)
            if (currentIdx > 0) {
                cloudflareAnycastPool.removeAt(currentIdx)
                cloudflareAnycastPool.add(0, pureIp)
                logger.d { "Promoted healthy node $pureIp to top priority" }
            }
        }
    }

    /**
     * 当某节点连接失败或超时后，动态降权至队尾，避免后续请求反复踩坑
     */
    fun recordFailure(ip: String) {
        val pureIp = ip.substringBefore(':')
        if (cloudflareAnycastPool.contains(pureIp)) {
            val currentIdx = cloudflareAnycastPool.indexOf(pureIp)
            if (currentIdx == 0 && cloudflareAnycastPool.size > 1) {
                cloudflareAnycastPool.removeAt(0)
                cloudflareAnycastPool.add(pureIp)
                logger.d { "Demoted failing node $pureIp to end of pool" }
            }
        }
    }
}
