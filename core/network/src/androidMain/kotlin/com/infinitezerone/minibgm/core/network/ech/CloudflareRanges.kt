package com.infinitezerone.minibgm.core.network.ech

import kotlin.random.Random

/**
 * Cloudflare 公开边缘网段。
 *
 * 改造前这些前缀散落在 [AdaptiveDnsResolver.isCloudflareIp] 的一串 `startsWith` 判断里，
 * 现在集中成一张表，同时服务两个**取向相反**的目的：
 *
 * - [contains]：判毒白名单。逐条等价于改造前的判定（含 `108.162.` / `198.41.` 这类比官方公告
 *   更宽的粗粒度前缀）。刻意既不收紧也不放宽——判毒宁可放过、不可误杀：每放宽一格只是少拦一个
 *   伪造 IP（还会被后面的真实握手筛掉），而误杀一个真实边缘 IP 会直接丢候选并触发一次多余的探测。
 * - [sampleCandidates]：兜底探测的采样范围。只取两块**有直接证据**承载 HTTP 边缘、且容量足够大的
 *   网段（改造前那份可用边缘 IP 全部落在 104.26.x / 172.67.x，即这两块之内）。其余段容量极小，
 *   采样命中率不值得占用探测预算。
 */
internal object CloudflareRanges {
    private class Range(
        val base: Long,
        val prefix: Int,
    ) {
        val size: Long = 1L shl (32 - prefix)

        fun contains(value: Long): Boolean = (value ushr (32 - prefix)) == (base ushr (32 - prefix))
    }

    private class Ipv6Range(
        val base: ByteArray,
        val prefix: Int,
    ) {
        fun contains(ipBytes: ByteArray): Boolean {
            if (ipBytes.size != 16) return false
            val fullBytes = prefix / 8
            val remainingBits = prefix % 8
            for (i in 0 until fullBytes) {
                if (ipBytes[i] != base[i]) return false
            }
            if (remainingBits > 0) {
                val mask = (0xFF shl (8 - remainingBits)) and 0xFF
                if ((ipBytes[fullBytes].toInt() and mask) != (base[fullBytes].toInt() and mask)) {
                    return false
                }
            }
            return true
        }
    }

    private val WHITELIST: List<Range> =
        listOf(
            range("172.64.0.0", 13),
            range("104.16.0.0", 12),
            range("162.158.0.0", 15),
            range("108.162.0.0", 16),
            range("198.41.0.0", 16),
            range("188.114.0.0", 16),
            range("190.93.0.0", 16),
            range("197.234.0.0", 16),
            range("141.101.0.0", 16),
        )

    private val IPV6_WHITELIST: List<Ipv6Range> =
        listOf(
            ipv6Range("2606:4700::", 32),
            ipv6Range("2400:cb00::", 32),
            ipv6Range("2803:f800::", 32),
            ipv6Range("2405:b500::", 32),
            ipv6Range("2405:8100::", 32),
            ipv6Range("2a06:98c0::", 29),
            ipv6Range("2c0f:f248::", 32),
        )

    private val SAMPLING: List<Range> =
        listOf(
            range("104.16.0.0", 12),
            range("172.64.0.0", 13),
        )

    /** 该 IP 是否落在 Cloudflare 网段内（支持 IPv4 与 IPv6）。非 IP 字面量一律返回 false。 */
    fun contains(ip: String): Boolean {
        val ipv4 = ipv4ToLong(ip)
        if (ipv4 != null) {
            return WHITELIST.any { it.contains(ipv4) }
        }
        val ipv6 = ipv6ToBytes(ip)
        if (ipv6 != null) {
            return IPV6_WHITELIST.any { it.contains(ipv6) }
        }
        return false
    }

    /**
     * 在采样范围内随机取 [count] 个候选地址（去重、排除 [exclude]）。
     *
     * 随机采样是可行的，因为 CF 的 anycast 网络用每个边缘承载全部托管 zone——采样范围内的地址
     * 密度足够高，不需要通讯录式的外部优选 IP 列表。命中率由紧随其后的握手探测负责收敛。
     */
    fun sampleCandidates(
        count: Int,
        random: Random = Random.Default,
        exclude: Set<String> = emptySet(),
    ): List<String> {
        if (count <= 0) return emptyList()
        val picked = LinkedHashSet<String>()
        // 去重与排除会吃掉一部分采样额度，给一个次数上限避免在极端情况下空转
        val maxAttempts = count * 8
        var attempts = 0
        while (picked.size < count && attempts < maxAttempts) {
            attempts++
            val candidate = randomIp(random)
            if (candidate !in exclude) picked.add(candidate)
        }
        return picked.toList()
    }

    private fun randomIp(random: Random): String {
        var ticket = random.nextLong(SAMPLING.sumOf { it.size })
        var selected = SAMPLING.last()
        for (range in SAMPLING) {
            if (ticket < range.size) {
                selected = range
                break
            }
            ticket -= range.size
        }
        return longToIpv4(selected.base + random.nextLong(selected.size))
    }

    /** 网段字面量在对象初始化时就校验：写错一个地址应当在启动期立刻炸掉，而不是静默少一个网段。 */
    private fun range(
        base: String,
        prefix: Int,
    ): Range = Range(requireNotNull(ipv4ToLong(base)) { "invalid CIDR base address: $base" }, prefix)

    private fun ipv4ToLong(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    private fun longToIpv4(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { shift -> ((value shr shift) and 0xFF).toString() }

    private fun ipv6Range(
        base: String,
        prefix: Int,
    ): Ipv6Range =
        Ipv6Range(
            requireNotNull(ipv6ToBytes(base)) { "invalid CIDR base IPv6 address: $base" },
            prefix,
        )

    private fun ipv6ToBytes(ip: String): ByteArray? {
        val trimmed = ip.trim().removePrefix("[").removeSuffix("]")
        if (!trimmed.contains(':')) return null
        if (!trimmed.all { it.isDigit() || (it in 'a'..'f') || (it in 'A'..'F') || it == ':' }) {
            return null
        }
        val parts = trimmed.split("::")
        if (parts.size > 2) return null

        val leftParts = if (parts[0].isEmpty()) emptyList() else parts[0].split(':')
        val rightParts = if (parts.size == 2 && parts[1].isNotEmpty()) parts[1].split(':') else emptyList()

        val totalSupplied = leftParts.size + rightParts.size
        if (parts.size == 1 && totalSupplied != 8) return null
        if (parts.size == 2 && totalSupplied >= 8) return null

        val result = ByteArray(16)
        var byteIdx = 0

        fun parseGroup(part: String): Int? {
            if (part.isEmpty() || part.length > 4) return null
            return part.toIntOrNull(16)
        }

        for (part in leftParts) {
            val v = parseGroup(part) ?: return null
            result[byteIdx++] = (v shr 8).toByte()
            result[byteIdx++] = (v and 0xFF).toByte()
        }

        if (parts.size == 2) {
            val missingGroups = 8 - totalSupplied
            byteIdx += missingGroups * 2
        }

        for (part in rightParts) {
            val v = parseGroup(part) ?: return null
            result[byteIdx++] = (v shr 8).toByte()
            result[byteIdx++] = (v and 0xFF).toByte()
        }

        return if (byteIdx == 16) result else null
    }
}
