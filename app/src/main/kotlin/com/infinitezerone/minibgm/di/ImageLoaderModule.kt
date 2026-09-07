package com.infinitezerone.minibgm.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.infinitezerone.minibgm.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRedirect
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
                install(DefaultRequest) {
                    header(
                        HttpHeaders.UserAgent,
                        "MiniBgm/${BuildConfig.VERSION_NAME} (android) (https://github.com/infinitezerone/MiniBgm)",
                    )
                }
            }
        }
        single<ImageLoader> {
            val context = androidContext()
            val client = get<HttpClient>(named("imageHttpClient"))
            ImageLoader
                .Builder(context)
                .components {
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
