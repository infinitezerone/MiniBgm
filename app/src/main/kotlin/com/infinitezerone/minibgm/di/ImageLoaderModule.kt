package com.infinitezerone.minibgm.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.infinitezerone.minibgm.core.common.BgmImageUtils
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val imageLoaderModule =
    module {
        single<HttpClient>(named("imageHttpClient")) {
            BgmHttpClient
                .createBaseClient(
                    engine = getOrNull(),
                    userAgent = appUserAgent,
                    loggerTag = "Bgm/ImageHttp",
                ) {
                    install(HttpRedirect)
                    install(HttpTimeout) {
                        connectTimeoutMillis = 8_000
                        socketTimeoutMillis = 10_000
                        requestTimeoutMillis = 15_000
                    }
                    install(DefaultRequest) {
                        header(HttpHeaders.Accept, "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    }
                }.also { client ->
                    // 动态 Referer：仅 Bangumi 官方图床（lain.bgm.tv 等）需要 Referer: https://bgm.tv/；
                    // 评论区里的第三方外链图床（新浪、B站、Imgur 等）若携带 bgm.tv Referer 会直接被防盗链策略 403 阻断，
                    // 因此第三方图床严格不发送该 Referer，确保评论区外链图片顺利直出。
                    client.plugin(HttpSend).intercept { request ->
                        if (BgmImageUtils.isBgmImageHost(request.url.host)) {
                            request.header("Referer", "https://bgm.tv/")
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

                    // 全局拦截器：
                    // 1. 将所有 http:// 图片地址自动升轨为安全 https://，防止 Android Cleartext 限制与 301 重定向开销；
                    // 2. 将 Bangumi 未压缩扫图（/pic/cover/l/、/pic/crt/l/、/pic/user/l/）透明优化为
                    //    官方 CDN 400px WebP 压缩规格（/r/400/...），节约 ~75% 移动端带宽并收敛 Coil 缓存键。
                    add(
                        Interceptor { chain ->
                            val request = chain.request
                            val data = request.data
                            if (data is String) {
                                val optimizedUrl = BgmImageUtils.optimizeBgmImageUrl(data)
                                if (optimizedUrl != data) {
                                    return@Interceptor chain
                                        .withRequest(
                                            request.newBuilder().data(optimizedUrl).build(),
                                        ).proceed()
                                }
                            }
                            chain.proceed()
                        },
                    )
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
