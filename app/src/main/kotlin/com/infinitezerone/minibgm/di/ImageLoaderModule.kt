package com.infinitezerone.minibgm.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.infinitezerone.minibgm.BuildConfig
import com.infinitezerone.minibgm.core.common.BgmImageUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val imageLoaderModule =
    module {
        single<HttpClient>(named("imageHttpClient")) {
            HttpClient(CIO) {
                install(HttpRedirect)
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                }
                install(DefaultRequest) {
                    header(
                        HttpHeaders.UserAgent,
                        "MiniBgm/${BuildConfig.VERSION_NAME} (android) (https://github.com/infinitezerone/MiniBgm)",
                    )
                    header("Referer", "https://bgm.tv/")
                    header(HttpHeaders.Accept, "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                }
            }
        }
        single<ImageLoader> {
            val context = androidContext()
            val client = get<HttpClient>(named("imageHttpClient"))
            ImageLoader
                .Builder(context)
                .components {
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
