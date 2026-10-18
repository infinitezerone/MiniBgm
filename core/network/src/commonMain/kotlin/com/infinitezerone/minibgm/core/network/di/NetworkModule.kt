package com.infinitezerone.minibgm.core.network.di

import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiApiServiceImpl
import com.infinitezerone.minibgm.core.network.BangumiCommunityService
import com.infinitezerone.minibgm.core.network.BangumiCommunityServiceImpl
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.BgmTokenPair
import com.infinitezerone.minibgm.core.network.BgmTokenService
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.PageFetchServiceImpl
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotService
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotServiceImpl
import com.infinitezerone.minibgm.core.network.createHttpCacheStorage
import com.infinitezerone.minibgm.core.network.createPlatformHttpClientEngine
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.cache.HttpCache
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * @param enableNetworkLogging 仅由 app 层传入 BuildConfig.DEBUG
 * @param userAgent 由 app 层以 BuildConfig.VERSION_NAME 拼装，随版本自动更新
 * @param engine 底层 HTTP 引擎，单测时注入 MockEngine
 * @param authConfig OAuth 认证配置，app 层注入由 CI / 本地配置提供的网关端点
 * @param httpCacheDirectory HTTP 磁盘缓存目录（null 表示不持久化；仅弹幕取数使用）
 */
fun networkModule(
    enableNetworkLogging: Boolean = false,
    userAgent: String,
    engine: HttpClientEngine? = null,
    authConfig: BgmAuthConfig = BgmAuthConfig(),
    httpCacheDirectory: String? = null,
): Module =
    module {
        single { authConfig }
        single<HttpClientEngine> { engine ?: createPlatformHttpClientEngine() }

        // 未鉴权独立 client（无 Auth 插件），专供访问无需 Bearer Token 的外部静态 CDN 资源与 Worker 兑换/刷新
        single(named("unauthenticated")) {
            BgmHttpClient.createBaseClient(
                engine = get(),
                enableLogging = enableNetworkLogging,
                userAgent = userAgent,
            )
        }

        // 第三方内容驱动的抓取/订阅专用：禁止自动跟随 30x，调用方必须逐跳过 UrlSafety
        single(named("untrusted")) {
            BgmHttpClient.createBaseClient(
                engine = get(),
                enableLogging = enableNetworkLogging,
                userAgent = userAgent,
            ) {
                followRedirects = false
            }
        }

        single<com.infinitezerone.minibgm.core.network.oauth.OAuthProxyService> {
            com.infinitezerone.minibgm.core.network.oauth
                .createOAuthProxyService(client = get(named("unauthenticated")))
        }

        single {
            BgmTokenService(client = get(named("unauthenticated")), config = get())
        }

        // 业务主 client：Bearer 自动注入 + 401 时经 Worker 刷新重试
        single {
            BgmHttpClient.create(
                userAgent = userAgent,
                tokenProvider = get(),
                enableLogging = enableNetworkLogging,
                engine = get(),
                // 凭据被 OAuth 拒绝（invalid_grant 等 4xx）降级为 null：client 据此
                // 清除本地凭据（isLoggedIn 随之翻转，自动登出闭环）并沿用原始 401
                // 上抛 Unauthorized；5xx/网络异常等瞬时故障原样上抛以免误登出
                // （降级策略见 refreshOrNull）
                tokenRefresher = { oldRefreshToken ->
                    get<BgmTokenService>().refreshOrNull(oldRefreshToken)?.let { refreshed ->
                        BgmTokenPair(
                            accessToken = refreshed.accessToken,
                            // bgm.tv 刷新接口可能不回传新 refresh token，此时沿用旧的
                            refreshToken = refreshed.refreshToken.ifBlank { oldRefreshToken },
                        )
                    }
                },
            )
        }
        single<BangumiApiService> { BangumiApiServiceImpl(client = get(), authConfig = get()) }
        single<BangumiCommunityService> { BangumiCommunityServiceImpl(get(named("unauthenticated"))) }
        single<ScheduleSnapshotService> { ScheduleSnapshotServiceImpl(get(named("unauthenticated"))) }
        single<PageFetchService> {
            // 只注入禁止自动跟随的 client：抓取/探测的每一跳都由 PageFetchService
            // 自己复核 URL 并剥跨域凭据头，不接受 Ktor 的隐式重定向
            PageFetchServiceImpl(
                untrustedClient = get(named("untrusted")),
                dispatchers = getOrNull() ?: BgmDispatchers(),
            )
        }

        // 弹幕取数专用：HTTP 缓存的新鲜度完全交给服务端响应头（Cache-Control / ETag / 304），
        // 仓库不再自研 TTL。刻意与通用抓取 client 分开——播放页/订阅页的抓取不能被顺带缓存，
        // 否则源列表会按服务端给的 max-age 静默变旧。
        single<PageFetchService>(named("danmaku")) {
            val cacheDirectory = httpCacheDirectory?.trim()?.takeIf { it.isNotBlank() }
            if (cacheDirectory == null) {
                PageFetchServiceImpl(
                    untrustedClient = get(named("untrusted")),
                    dispatchers = getOrNull() ?: BgmDispatchers(),
                )
            } else {
                val cacheStorage = createHttpCacheStorage(cacheDirectory)
                val cachedClient =
                    BgmHttpClient.createBaseClient(
                        engine = get(),
                        enableLogging = enableNetworkLogging,
                        userAgent = userAgent,
                    ) {
                        // 与通用抓取同一条安全边界：30x 由 PageFetchService 逐跳复核后自己跟
                        followRedirects = false
                        install(HttpCache) {
                            publicStorage(cacheStorage)
                        }
                    }
                PageFetchServiceImpl(
                    untrustedClient = cachedClient,
                    dispatchers = getOrNull() ?: BgmDispatchers(),
                )
            }
        }
        single<com.infinitezerone.minibgm.core.network.CommunitySubscriptionService> {
            com.infinitezerone.minibgm.core.network
                .CommunitySubscriptionServiceImpl(get(named("untrusted")))
        }
        single<com.infinitezerone.minibgm.core.network.UpdateService> {
            com.infinitezerone.minibgm.core.network
                .UpdateServiceImpl(get(named("unauthenticated")))
        }
        single<com.infinitezerone.minibgm.core.network.AssetDownloadService> {
            com.infinitezerone.minibgm.core.network
                .AssetDownloadServiceImpl(get(named("unauthenticated")))
        }
    }
