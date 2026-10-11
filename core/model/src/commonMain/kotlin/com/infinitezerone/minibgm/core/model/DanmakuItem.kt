package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 弹幕类型：SCROLL 滚动弹幕（自右向左），TOP 顶部固定，BOTTOM 底部固定
 */
@Serializable
enum class DanmakuMode {
    SCROLL,
    TOP,
    BOTTOM,
}

/**
 * 弹幕条目模型。
 *
 * @property timeMs 弹幕相对于视频开头的播放时间（毫秒）
 * @property text 弹幕文字内容
 * @property color 24 位 RGB 颜色整数（如 0xFFFFFF 代表纯白）
 * @property mode 弹幕显示样式类型
 */
@Serializable
data class DanmakuItem(
    val timeMs: Long,
    val text: String,
    val color: Int = 0xFFFFFF,
    val mode: DanmakuMode = DanmakuMode.SCROLL,
)
