package com.infinitezerone.minibgm.core.model

/**
 * 应用内网页会话：把 bgm 系页面交给内置 WebView 经环回代理渲染所需的要素。
 *
 * WebView 无法使用原生 ECH 通道（它有自己的网络栈），因此页面统一走本地环回代理，
 * 由代理侧的 Ktor 客户端（EchHttpClientEngine）与上游通信。
 *
 * @property url WebView 应加载的地址：**必须使用真实站点的 host**（`http://bgm.tv/...`），
 *   否则页面 origin 会变成环回地址。第三方脚本（如 Cloudflare Turnstile）会校验 origin，
 *   不匹配就以 `110200 Unknown domain` 拒绝渲染。用 `http` 而非 `https`：拦截层无法转发
 *   POST body，表单需直连环回代理，同协议才不会落入混合内容限制。
 * @property cookieName 代理会话 Cookie 名；WebView 必须在加载前写入，否则代理拒绝服务
 * @property cookieValue 本次会话随机令牌（防止本机其他应用把环回端口当作 bgm.tv 免费代理）
 * @property proxyBaseUrl 环回代理基址（`http://127.0.0.1:<port>`）。令牌 Cookie 必须写在这个
 *   origin 上：表单提交直连代理时才会带上它。
 */
data class InAppWebSession(
    val url: String,
    val cookieName: String,
    val cookieValue: String,
    val proxyBaseUrl: String,
)
