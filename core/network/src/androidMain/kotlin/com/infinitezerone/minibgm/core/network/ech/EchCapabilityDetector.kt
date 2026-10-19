package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.createPlatformHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * 动态 ECH 能力探测与缓存注册器。
 *
 * 遵循现代 Chromium (Chrome / Edge) 标准网络栈的决策路线：
 * 1. 种子域名（Bangumi 官方域、AniList、以及高频图床 sda1.dev）直接内建支持，零探测开销；
 * 2. 外部第三方域名在首次请求时，经由高可靠国内公共 DoH (AliDNS 223.5.5.5) 异步探测 Type 65 (HTTPS RR)；
 * 3. 若响应包含 `ech=` 加密参数，判定为 Cloudflare ECH 支持站点，自动登记至 [AdaptiveDnsResolver]，
 *    使请求顺利通过外层加密伪装与 Anycast 优选路由，穿透移动等运营商的明文 SNI 阻断；
 * 4. 探测结果在内存中进行状态缓存，杜绝重复网络请求；发生网络抖动或异常时 fail-open 安全降级为直连。
 */
object EchCapabilityDetector {
    private val logger = bgmLogger("Bgm/EchDetector")

    private const val ALIDNS_DOH_URL = "https://223.5.5.5/resolve"
    private const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 内置的 DoH 探测客户端：使用带平台 ECH 引擎的客户端，安全查询 cloudflare-dns.com 不会被递归拦截 */
    private val embeddedProbeClient: HttpClient by lazy { HttpClient(createPlatformHttpClientEngine()) }

    /** 探测进行中的 host 集合：请求路径每次调用都会触发本探测器，防止并发重复 DoH 查询 */
    private val probingInFlight = ConcurrentHashMap.newKeySet<String>()

    private enum class SupportStatus {
        SUPPORTED,
        UNSUPPORTED,
    }

    private val cache = ConcurrentHashMap<String, SupportStatus>()

    @Serializable
    internal data class DohResponse(
        @SerialName("Status") val status: Int = -1,
        @SerialName("Answer") val answer: List<DohAnswer>? = null,
    )

    @Serializable
    internal data class DohAnswer(
        val name: String = "",
        val type: Int = 0,
        val data: String = "",
    )

    /**
     * 判断域名是否直接命中静态内置的 Cloudflare / ECH 白名单。
     */
    fun isSeedHost(host: String): Boolean =
        AdaptiveDnsResolver.isEchEligible(host) ||
            host.equals("anilist.co", ignoreCase = true) ||
            host.endsWith(".anilist.co", ignoreCase = true)

    /**
     * 判断域名是否已知支持 ECH（静态种子、动态注册或内存探测成功）。
     */
    fun isEchEligible(host: String): Boolean =
        isSeedHost(host) ||
            (cache[host.trim().lowercase()] == SupportStatus.SUPPORTED && !AdaptiveDnsResolver.isEchCoolingDown(host))

    /**
     * 标记指定 host 的 ECH 握手或连接超时失败，触发冷却窗口。
     */
    fun markEchFailure(host: String) {
        AdaptiveDnsResolver.markEchHandshakeFailure(host)
    }

    /**
     * 判断指定 host 是否支持通过 ECH 引擎访问。
     *
     * @param host 待检测的目标域名
     * @param probeClient 供探测使用的 HttpClient；若为 null 且未命中缓存，则保守降级为 false
     */
    suspend fun isEchSupported(
        host: String,
        probeClient: HttpClient? = null,
    ): Boolean {
        val normalized = host.trim().lowercase()
        if (normalized.isBlank()) return false

        if (isSeedHost(normalized)) {
            return true
        }

        val cachedStatus = cache[normalized]
        if (cachedStatus != null) {
            return cachedStatus == SupportStatus.SUPPORTED
        }

        if (probeClient == null) {
            return false
        }

        val supported = probeEchViaDoh(normalized, probeClient)
        cache[normalized] = if (supported) SupportStatus.SUPPORTED else SupportStatus.UNSUPPORTED
        if (supported) {
            AdaptiveDnsResolver.registerEchHost(normalized)
            logger.i { "Host $normalized supports ECH via Type 65 discovery; registered to ECH routing" }
        } else {
            logger.d { "Host $normalized does not support ECH or probe returned no ECH parameter" }
        }
        return supported
    }

    /**
     * 对"不在种子名单且尚未探测过"的域名发起一次 fire-and-forget 的 ECH 能力探测。
     *
     * 供请求引擎在直连路径上调用：当前请求不受影响（fail-open 直连），探测命中即注册，
     * 后续请求自动升级走 ECH 路由。种子域名 / IP 字面量 / 已有结论 / 探测进行中一律跳过。
     *
     * @return 本次真正发起的探测 Job；跳过时返回 null
     */
    fun probeAsyncIfUnknown(
        host: String,
        scope: CoroutineScope = backgroundScope,
        client: HttpClient? = null,
    ): Job? {
        val normalized = host.trim().lowercase()
        if (normalized.isBlank() || isSeedHost(normalized) || isIpLiteral(normalized)) return null
        if (cache.containsKey(normalized) || !probingInFlight.add(normalized)) return null
        return scope.launch {
            try {
                isEchSupported(normalized, client ?: embeddedProbeClient)
            } finally {
                probingInFlight.remove(normalized)
            }
        }
    }

    private fun isIpLiteral(host: String): Boolean = host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}""")) || host.contains(':')

    /**
     * 查询指定域名的 Type 65 (HTTPS) 记录并解析 ECH 字段。
     *
     * 1. 优先尝试公共 DoH（AliDNS 223.5.5.5）；
     * 2. 若 AliDNS 探测未返回 ech（国内公共 DNS 常见针对涉外域名的 Type 65 投毒并抹除 ech 参数），
     *    回退至通过 ECH 加密通道连接的 Cloudflare DoH (cloudflare-dns.com) 获取无污染真实记录。
     */
    private suspend fun probeEchViaDoh(
        host: String,
        client: HttpClient,
    ): Boolean {
        val aliResult = queryDoh(ALIDNS_DOH_URL, host, client)
        if (aliResult) return true

        return queryDoh(CLOUDFLARE_DOH_URL, host, client)
    }

    private suspend fun queryDoh(
        dohUrl: String,
        host: String,
        client: HttpClient,
    ): Boolean =
        try {
            val response =
                client.get(dohUrl) {
                    url.parameters.append("name", host)
                    url.parameters.append("type", "HTTPS")
                    header("Accept", "application/dns-json")
                }
            if (response.status != HttpStatusCode.OK) {
                logger.w { "DoH probe for $host via $dohUrl returned non-200 status: ${response.status}" }
                false
            } else {
                val body = response.bodyAsText()
                parseDohResponseHasEch(body)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            logger.d { "DoH probe failed for $host via $dohUrl (fail-open to direct connect): ${t.message}" }
            false
        }

    /**
     * 解析 DoH 响应 JSON，判断是否有 Type 65 且含有 ech 参数的答案。
     */
    internal fun parseDohResponseHasEch(jsonText: String): Boolean =
        runCatching {
            val decoded = BgmHttpClient.jsonConfig.decodeFromString<DohResponse>(jsonText)
            if (decoded.status == 0 && decoded.answer != null) {
                decoded.answer.any { it.type == 65 && it.data.contains("ech=") }
            } else {
                false
            }
        }.getOrDefault(false)

    /** 供单测使用的重置状态方法 */
    fun resetForTest() {
        cache.clear()
    }
}
