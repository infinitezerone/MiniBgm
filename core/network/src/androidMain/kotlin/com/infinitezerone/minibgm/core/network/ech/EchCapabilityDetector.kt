package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
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
     * 向 AliDNS DoH 查询指定域名的 Type 65 (HTTPS) 记录并解析 ECH 字段。
     */
    private suspend fun probeEchViaDoh(
        host: String,
        client: HttpClient,
    ): Boolean =
        try {
            val response =
                client.get(ALIDNS_DOH_URL) {
                    url.parameters.append("name", host)
                    url.parameters.append("type", "HTTPS")
                    header("Accept", "application/dns-json")
                }
            if (response.status != HttpStatusCode.OK) {
                logger.w { "DoH probe for $host returned non-200 status: ${response.status}" }
                false
            } else {
                val body = response.bodyAsText()
                parseDohResponseHasEch(body)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            logger.w(t) { "DoH probe failed for $host (fail-open to direct connect): ${t.message}" }
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
