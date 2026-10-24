package com.infinitezerone.minibgm.feature.search

import androidx.annotation.StringRes

/**
 * 一次性用户提示：资源文案 + 格式化参数，UI 侧解析；
 * 服务端原始文案经 [serverMessage] 透传，非空时 UI 优先展示（红线「事件文案一律 @StringRes 下发」）。
 */
data class SearchUserMessage(
    @get:StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList(),
    val serverMessage: String? = null,
)
