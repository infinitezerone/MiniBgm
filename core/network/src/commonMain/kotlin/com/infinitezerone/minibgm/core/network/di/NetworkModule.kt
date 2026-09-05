package com.infinitezerone.minibgm.core.network.di

import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiApiServiceImpl
import com.infinitezerone.minibgm.core.network.BangumiCommunityService
import com.infinitezerone.minibgm.core.network.BangumiCommunityServiceImpl
import com.infinitezerone.minibgm.core.network.BangumiDataService
import com.infinitezerone.minibgm.core.network.BangumiDataServiceImpl
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.BgmTokenPair
import com.infinitezerone.minibgm.core.network.BgmTokenService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * @param enableNetworkLogging 仅由 app 层传入 BuildConfig.DEBUG
 * @param userAgent 由 app 层以 BuildConfig.VERSION_NAME 拼装，随版本自动更新
 */
fun networkModule(
    enableNetworkLogging: Boolean = false,
    userAgent: String,
): Module =
    module {
        single { BgmAuthConfig() }

        // 未鉴权独立 client（无 Auth 插件），专供访问无需 Bearer Token 的外部静态 CDN 资源与 Worker 兑换/刷新
        single(named("unauthenticated")) {
            BgmHttpClient.create(
                userAgent = userAgent,
                tokenProvider = get(),
                enableLogging = enableNetworkLogging,
            )
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
        single<BangumiApiService> { BangumiApiServiceImpl(get()) }
        single<BangumiDataService> { BangumiDataServiceImpl(get(named("unauthenticated"))) }
        single<BangumiCommunityService> { BangumiCommunityServiceImpl(get(named("unauthenticated"))) }
    }
