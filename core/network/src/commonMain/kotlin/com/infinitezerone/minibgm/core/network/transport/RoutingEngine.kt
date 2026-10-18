package com.infinitezerone.minibgm.core.network.transport

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException

/**
 * 传输路由：ECH 只负责 https，其余（当前是 http / 本机回环）交给 plain 引擎。
 *
 * 这是对"ECH 引擎占着通用引擎位置"的收口：服务层不再需要判断回环地址、
 * 也不再需要自建 CIO client。所有请求原样委托给选中的引擎——不重建 callContext、
 * 不额外包 withTimeout，超时/取消语义仍由 Ktor 插件与目标引擎决定。
 *
 * capability 取两个引擎的交集：routing 对外是一个引擎，只能承诺双方都支持的能力。
 * 当前两条通道都支持 HttpTimeout，业务侧也没有 WebSocket/SSE 消费方。
 */
@OptIn(InternalAPI::class)
class RoutingEngine(
    private val echEngine: HttpClientEngine,
    private val plainEngine: HttpClientEngine,
) : HttpClientEngineBase("RoutingEngine") {
    private val logger = bgmLogger("Bgm/RoutingEngine")

    override val config: HttpClientEngineConfig = HttpClientEngineConfig()

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        echEngine.supportedCapabilities intersect plainEngine.supportedCapabilities

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val engine = engineFor(data.url)
        if (engine === plainEngine) return plainEngine.execute(data)
        return try {
            echEngine.execute(data)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            // ECH 握手被拒/被中断时不能让请求直接死掉：GET/HEAD 幂等，
            // 回落到 plain 重试一次；写请求禁止回落，避免重复落库。
            if (data.method != HttpMethod.Get && data.method != HttpMethod.Head) throw failure
            logger.w {
                "ECH failed for " + data.url.host + " (" + failure.message + "); falling back to plain"
            }
            plainEngine.execute(data)
        }
    }

    internal fun engineFor(url: Url): HttpClientEngine = if (url.protocol == URLProtocol.HTTP) plainEngine else echEngine

    override fun close() {
        echEngine.close()
        plainEngine.close()
    }
}
