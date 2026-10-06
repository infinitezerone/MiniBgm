package com.infinitezerone.minibgm.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.request.crossfade
import com.infinitezerone.minibgm.core.common.isBgmDomain
import com.infinitezerone.minibgm.core.common.toBgmCdnUrl
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.createPlatformHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.InternalAPI
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 图片专用双轨路由网络引擎：
 * 1. Bangumi 官方图床（lain.bgm.tv 等）：路由至 [primaryEngine]（原生 ECH 引擎），
 *    享受 Anycast 优选、ECH 加密 SNI 与 HTTP/2 多路复用，穿透网络干扰；
 * 2. 外部第三方图床（新浪、B站、Imgur 等）：路由至 [fallbackEngine]（平台通用 CIO 引擎），
 *    支持标准 HTTP/HTTPS，独立连接池，严格不占用 ECH 的 64 个并发槽位，消除对业务 API 的资源挤占。
 */
@OptIn(InternalAPI::class)
private class ImageRoutingEngine(
    private val primaryEngine: HttpClientEngine,
    private val fallbackEngine: HttpClientEngine,
) : HttpClientEngineBase("ImageRoutingEngine") {
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>>
        get() = primaryEngine.supportedCapabilities.intersect(fallbackEngine.supportedCapabilities)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val host = data.url.host
        val targetEngine =
            if (host.isBgmDomain || host.equals("anilist.co", ignoreCase = true) || host.endsWith(".anilist.co", ignoreCase = true)) {
                primaryEngine
            } else {
                fallbackEngine
            }
        return targetEngine.execute(data)
    }

    override fun close() {
        super.close()
        primaryEngine.close()
        fallbackEngine.close()
    }
}

/**
 * Bangumi 图片 CDN 优化与协议升轨拦截器：
 * 1. 结构化覆盖 String 与泛型 Uri 数据源，杜绝优化静默失效；
 * 2. 将所有 http:// 图片地址升轨为安全的 https://，避免 Cleartext 拦截与 301 重定向开销；
 * 3. 将 Bangumi 未压缩扫图透明优化为官方 CDN 400px 压缩规格（/r/400/...），节约移动端带宽并收敛 Coil 缓存键。
 */
private class BgmImageCdnInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val rawData = request.data
        val urlString =
            when (rawData) {
                is String -> rawData
                is coil3.Uri -> rawData.toString()
                is android.net.Uri -> rawData.toString()
                else -> null
            }
        if (!urlString.isNullOrBlank()) {
            val optimizedUrl = urlString.toBgmCdnUrl()
            if (optimizedUrl != urlString) {
                return chain
                    .withRequest(
                        request.newBuilder().data(optimizedUrl).build(),
                    ).proceed()
            }
        }
        return chain.proceed()
    }
}

val imageLoaderModule =
    module {
        single<HttpClient>(named("imageHttpClient")) {
            val primaryEngine = getOrNull<HttpClientEngine>() ?: createPlatformHttpClientEngine()
            val fallbackEngine = CIO.create()
            val compositeEngine = ImageRoutingEngine(primaryEngine, fallbackEngine)

            BgmHttpClient
                .createBaseClient(
                    engine = compositeEngine,
                    userAgent = appUserAgent,
                    loggerTag = "Bgm/ImageHttp",
                ) {
                    install(HttpRedirect)
                    install(HttpTimeout) {
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 15_000
                        requestTimeoutMillis = 20_000
                    }
                    install(DefaultRequest) {
                        header(HttpHeaders.Accept, "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    }
                }.also { client ->
                    // 动态 Referer 与 User-Agent：
                    // 1. Bangumi 官方图床（lain.bgm.tv 等）：需要 Referer: https://bgm.tv/，保留标准 appUserAgent；
                    // 2. 评论区里的第三方外链图床（新浪、B站、Imgur 等）：
                    //    严格不发送 bgm.tv Referer（会被防盗链策略 403 阻断）；
                    //    必须将 User-Agent 伪装为移动端标准浏览器（Chrome UA），彻底消除第三方图床因判定为非浏览器/App爬虫导致的 403 阻断。
                    client.plugin(HttpSend).intercept { request ->
                        val host = request.url.host
                        if (host.isBgmDomain) {
                            request.header("Referer", "https://bgm.tv/")
                        } else {
                            request.headers.remove(HttpHeaders.UserAgent)
                            request.header(
                                HttpHeaders.UserAgent,
                                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                            )
                        }
                        execute(request)
                    }
                }
        }
        single<ImageLoader> {
            val context = androidContext()
            val client = get<HttpClient>(named("imageHttpClient"))
            ImageLoader
                .Builder(context)
                .components {
                    // 支持动态 GIF 与 WebP 动图原生高效解码（minSdk 31+）
                    add(AnimatedImageDecoder.Factory())

                    // 挂载图片 CDN 优化与协议安全升轨拦截器
                    add(BgmImageCdnInterceptor())
                    add(KtorNetworkFetcherFactory(httpClient = { client }))
                }.memoryCache {
                    MemoryCache
                        .Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }.diskCache {
                    DiskCache
                        .Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(250L * 1024 * 1024)
                        .build()
                }.crossfade(true)
                .build()
        }
    }
