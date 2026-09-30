package com.infinitezerone.minibgm.core.network.oauth

import io.ktor.client.HttpClient

/** 环回代理默认上游：Bangumi 主站。 */
const val BGM_WEB_PROXY_HOST: String = "bgm.tv"

/**
 * 应用内网页环回反向代理契约。
 *
 * 内置 WebView 用不了原生 ECH 通道（系统 WebView 有自己的网络栈），所以把 bgm 系页面
 * 先落到本机环回端口，由代理侧的 [HttpClient]（EchHttpClientEngine）与上游通信，
 * 页面里指向上游域名的绝对链接会被重写成环回地址，导航始终留在代理内。
 *
 * 同一时刻只服务一个上游：换上游前会先 [stop]。登录与普通浏览互斥使用。
 */
interface OAuthProxyService {
    val isRunning: Boolean
    val port: Int?

    /**
     * 启动代理。
     *
     * @param upstreamHost 上游主机名（如 `bgm.tv` / `next.bgm.tv`），决定转发目标与链接重写
     * @return 环回端口
     */
    fun start(upstreamHost: String = BGM_WEB_PROXY_HOST): Int

    fun stop()

    /**
     * 把上游绝对地址映射为环回地址（保留 path 与 query 的原始编码）。
     *
     * @return 非本次上游域名的地址返回 null——调用方应回退到系统浏览器
     */
    fun toLoopbackUrl(url: String): String?

    /**
     * 本次会话必须由 WebView 在加载前写入的 Cookie（名, 值）；未启动时为 null。
     *
     * 代理只接受携带该 Cookie 的请求：环回端口对本机其他应用可见，
     * 无令牌等于免费送出「以本应用身份访问 bgm.tv」的能力。
     */
    fun sessionCookie(): Pair<String, String>?
}

expect fun createOAuthProxyService(client: HttpClient): OAuthProxyService
