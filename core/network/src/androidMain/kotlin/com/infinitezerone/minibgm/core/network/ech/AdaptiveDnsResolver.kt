package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 安全 DNS 解析策略与 Anycast 节点调度。
 */
object AdaptiveDnsResolver {
    private val logger = bgmLogger("Bgm/DnsResolver")

    // Bangumi Anycast 节点池
    private val bgmAnycastPool =
        CopyOnWriteArrayList(
            listOf(
                "172.67.73.67",
                "104.26.9.23",
                "104.26.8.23",
            ),
        )

    // AniList Anycast 节点池
    private val anilistAnycastPool =
        CopyOnWriteArrayList(
            listOf(
                "172.67.71.232",
                "104.26.14.71",
                "104.26.15.71",
            ),
        )

    /** 判断域名是否支持 ECH 握手 */
    private fun isDomainOrSubdomain(
        host: String,
        domain: String,
    ): Boolean = host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)

    fun isEchEligible(host: String): Boolean =
        isDomainOrSubdomain(host, "bgm.tv") ||
            isDomainOrSubdomain(host, "bangumi.tv") ||
            isDomainOrSubdomain(host, "chii.in")

    /** 判断域名是否由 Cloudflare 提供服务 */
    fun isCloudflareHosted(host: String): Boolean = isEchEligible(host) || isDomainOrSubdomain(host, "anilist.co")

    private fun formatAddress(
        ip: String,
        port: Int,
    ): String = if (ip.contains(':')) "[$ip]:$port" else "$ip:$port"

    private fun pureIp(address: String): String =
        if (address.startsWith('[')) {
            address.substringBefore(']').removePrefix("[")
        } else {
            address.substringBeforeLast(':')
        }

    /** 校验 IP 是否属于 Cloudflare Anycast 广播网段 */
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

    /** 过滤被投毒的无效 IP */
    fun isPoisonedIp(
        host: String,
        ip: String,
    ): Boolean {
        if (ip.isBlank() || ip == "0.0.0.0" || ip.startsWith("127.")) return true

        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) return true
        if (ip.startsWith("198.18.") || ip.startsWith("198.19.")) return true

        if (isCloudflareHosted(host)) {
            return !isCloudflareIp(ip)
        }

        if (ip.startsWith("199.59.") || ip.startsWith("128.242.") || ip.startsWith("31.13.")) return true
        if (ip.startsWith("210.56.") || ip.startsWith("66.220.")) return true
        if (ip.startsWith("37.61.") || ip.startsWith("46.82.") || ip.startsWith("78.16.")) return true

        return false
    }

    /** 解析目标连接地址列表 */
    fun resolveTargetAddrs(
        host: String,
        port: Int,
    ): Array<String>? {
        if (!isCloudflareHosted(host)) {
            return null
        }

        val resultList = ArrayList<String>()

        try {
            val systemIps = InetAddress.getAllByName(host)
            val cleanIps = systemIps.mapNotNull { it.hostAddress }.filter { !isPoisonedIp(host, it) }

            if (cleanIps.isNotEmpty()) {
                logger.d { "Host $host resolved clean system IPs: $cleanIps" }
                for (ip in cleanIps) {
                    resultList.add(formatAddress(ip, port))
                }
            } else {
                logger.w { "Host $host system DNS was poisoned! IPs: ${systemIps.map { it.hostAddress }}" }
            }
        } catch (t: Throwable) {
            logger.w(t) { "Host $host system DNS resolution failed, fallback to dynamic anycast pool" }
        }

        val pool =
            if (host.endsWith("anilist.co", ignoreCase = true)) {
                anilistAnycastPool
            } else {
                bgmAnycastPool
            }

        for (ip in pool) {
            val addr = formatAddress(ip, port)
            if (!resultList.contains(addr)) {
                resultList.add(addr)
            }
        }

        return resultList.toTypedArray()
    }

    /** 将握手成功的节点提升至首位 */
    fun recordSuccess(
        host: String,
        addr: String,
    ) {
        val pureIp = pureIp(addr)
        val pool =
            if (isDomainOrSubdomain(host, "anilist.co")) {
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

    /** 将连接失败的节点降权至末尾 */
    fun recordFailure(
        host: String,
        addr: String,
    ) {
        val pureIp = pureIp(addr)
        val pool =
            if (isDomainOrSubdomain(host, "anilist.co")) {
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
