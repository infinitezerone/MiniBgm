package com.infinitezerone.minibgm.core.model

/**
 * 应用内网页会话：把 bgm 系页面交给内置 WebView 经环回代理渲染所需的三要素。
 *
 * WebView 无法使用原生 ECH 通道（它有自己的网络栈），因此页面统一走本地环回代理，
 * 由代理侧的 Ktor 客户端（EchHttpClientEngine）与上游通信。
 *
 * @property url WebView 应加载的环回地址（已映射上游域名与端口）
 * @property cookieName 代理会话 Cookie 名；WebView 必须在加载前写入，否则代理拒绝服务
 * @property cookieValue 本次会话随机令牌（防止本机其他应用把环回端口当作 bgm.tv 免费代理）
 */
data class InAppWebSession(
    val url: String,
    val cookieName: String,
    val cookieValue: String,
)
