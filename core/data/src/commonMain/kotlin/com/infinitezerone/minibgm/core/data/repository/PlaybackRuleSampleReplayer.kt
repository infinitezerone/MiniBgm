package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.CapturedNetworkCall
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.serialization.Serializable

/**
 * 重放一条被审计到的接口请求后取回的真实响应片段。
 *
 * 存在的理由：网络审计（`shouldInterceptRequest`）只能看到"页面请求了什么"，看不到响应正文，
 * 而 `EXTRACT_STREAM` 的正则必须照着真实响应写。重放一次就把这段缺口补上——
 * 模型的动作从"猜响应长什么样"变成"照样本写正则"。
 */
@Serializable
data class ApiResponseSample(
    val url: String,
    val ok: Boolean,
    /** 响应正文片段（已按 [MAX_SAMPLE_CHARS] 截断）；[ok] 为 false 时为空 */
    val bodyExcerpt: String = "",
    /** 失败或被拒的原因，让调用方能区分"站点不配合"与"我们没发对" */
    val note: String? = null,
)

/**
 * 把审计里的接口请求重放一遍以取回响应样本。
 *
 * **只重放 GET 且同站的请求**，原因见 [isReplayAllowed]。
 */
interface PlaybackRuleSampleReplayer {
    suspend fun replayApiSamples(trace: NetworkAuditTrace): List<ApiResponseSample>
}

/** 单次录制最多重放几条：采样是为了看结构，不是把整条链路重跑一遍 */
const val MAX_REPLAY_CALLS: Int = 3

/** 单条样本的正文上限：够看清字段结构，又不至于把响应整包灌进模型上下文 */
const val MAX_SAMPLE_CHARS: Int = 4000

class PlaybackRuleSampleReplayerImpl(
    private val pageFetchService: PageFetchService,
) : PlaybackRuleSampleReplayer {
    override suspend fun replayApiSamples(trace: NetworkAuditTrace): List<ApiResponseSample> {
        val pageUrl = trace.finalUrl.ifBlank { trace.pageUrl }
        val targets =
            trace.calls
                .filter { it.isApi && !it.isMedia && it.method.equals("GET", ignoreCase = true) }
                .distinctBy { it.url }
                .take(MAX_REPLAY_CALLS)
        if (targets.isEmpty()) return emptyList()

        return targets.map { call ->
            if (!isReplayAllowed(call.url, pageUrl)) {
                ApiResponseSample(url = call.url, ok = false, note = REJECTED_NOTE)
            } else {
                replayOne(call)
            }
        }
    }

    private suspend fun replayOne(call: CapturedNetworkCall): ApiResponseSample {
        // 头同样只带可重放的那几个：站点常靠 Referer 判来源，缺了会直接 403
        val headers = call.requestHeaders.filterKeys { it.lowercase() in PlaybackRuleRecorder.REPLAYABLE_HEADERS }
        val page =
            pageFetchService.fetchHtml(call.url, headers)
                ?: return ApiResponseSample(
                    url = call.url,
                    ok = false,
                    note = "重放没有拿到响应（不可达 / 被拒绝 / 无正文）",
                )
        val body = page.html
        if (body.isBlank()) {
            return ApiResponseSample(url = call.url, ok = false, note = "响应正文为空")
        }
        val excerpt =
            if (body.length > MAX_SAMPLE_CHARS) {
                body.take(MAX_SAMPLE_CHARS) + "\n…（已截断，原长 ${body.length} 字符）"
            } else {
                body
            }
        return ApiResponseSample(url = call.url, ok = true, bodyExcerpt = excerpt)
    }

    private companion object {
        const val REJECTED_NOTE: String = "未重放：该地址不在本次审计的站点范围内，或指向内网地址"
    }
}

/**
 * 是否允许重放这个地址。
 *
 * 这里挡的不是站点，是**调用方**：[NetworkAuditTrace] 是从模型传进来的 JSON 反序列化的，
 * 在重放之前它只是文本；一旦照着它发请求，它就成了一条"让 App 替调用方访问任意地址"的通道
 * （内网探测 / 云元数据端点 / 局域网设备）。两道约束叠加：
 *
 * 1. **同站**——目标 host 必须与被审计页面同站（相同、互为子域、或同注册域）。审计里被记录的
 *    请求本来就来自这次页面加载，跨站的根不该出现；这一条同时保证"重放的是这次真的发生过的事"。
 * 2. **非内网**——任一侧落在环回 / 私有段 / 链路本地 / 单标签主机时直接拒绝。第 1 条挡不住
 *    "页面地址本身就是内网"的情况（例如有人用局域网地址搭测试站），所以这一条独立成立。
 */
internal fun isReplayAllowed(
    targetUrl: String,
    pageUrl: String,
): Boolean {
    val targetHost = hostOf(targetUrl) ?: return false
    val pageHost = hostOf(pageUrl) ?: return false
    if (isPrivateHost(targetHost) || isPrivateHost(pageHost)) return false
    return isSameSite(targetHost, pageHost)
}

/** 取 host（不含端口与 userinfo），只认 http(s)；解析不出来返回 null */
internal fun hostOf(url: String): String? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        return null
    }
    val authority =
        trimmed
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
    // IPv6 字面量形如 [::1]:8080，一律拒绝，不参与同站判断
    if (authority.startsWith("[")) return null
    return authority.substringBefore(':').lowercase().takeIf { it.isNotBlank() }
}

/**
 * 同站判定：相同 host、互为子域、或同注册域。
 *
 * 最后一条是必须的——站点把接口放在独立子域（页面 `www.x.tv`、接口 `api.x.tv`）很常见，
 * 只认"互为子域"会把它们误判成跨站。这里按"末两段相同"近似注册域；没有 PSL，
 * 对 `co.uk` 这类多段后缀会放宽到同一二级域，可接受（同组织控制范围内）。
 */
internal fun isSameSite(
    hostA: String,
    hostB: String,
): Boolean {
    if (hostA == hostB) return true
    if (hostA.endsWith(".$hostB") || hostB.endsWith(".$hostA")) return true
    return tailDomain(hostA) == tailDomain(hostB)
}

private fun tailDomain(host: String): String = host.split('.').takeLast(2).joinToString(".")

/** 环回、私有段、链路本地、CGNAT、单标签主机与常见内网后缀 */
internal fun isPrivateHost(host: String): Boolean {
    if (host == "localhost" || host.endsWith(".localhost")) return true
    if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa")) return true
    // 单标签（无点）主机名在公网不可路由
    if (!host.contains('.')) return true

    val numbers = host.split('.').map { it.toIntOrNull() }
    // 含非数字段即普通域名
    if (numbers.any { it == null }) return false
    // 全数字形态：只有构成合法公网 IPv4 才放行。段数不对或越界的写法（192.168.1、
    // 十进制大整数等）在部分解析器里会被折算成内网地址，这里一律拒绝
    val octets = numbers.filterNotNull()
    if (octets.size != 4 || octets.any { it !in 0..255 }) return true

    val first = octets[0]
    val second = octets[1]
    return when {
        first == 0 || first == 10 || first == 127 -> true
        first == 192 && second == 168 -> true
        first == 172 && second in 16..31 -> true
        first == 169 && second == 254 -> true
        // CGNAT 段（RFC 6598）同样不该从 App 里被指使去访问
        first == 100 && second in 64..127 -> true
        else -> false
    }
}
