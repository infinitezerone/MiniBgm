package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 阴影/层级令牌：只表达视觉高度，不替代业务语义。
 *
 * 内容框、玻璃 chrome 与瞬态浮层使用不同的阴影重量，避免所有浮层都靠同一个模糊阴影
 * 硬撑层级。数值沿用现有实现，替换 token 不改变视觉结果。
 */
object BgmElevation {
    val none: Dp = 0.dp

    /** 实体内容框：与背景保持轻微分离。 */
    val content: Dp = 2.dp

    /** 玻璃 chrome 的默认高度：悬浮导航、玻璃面板。 */
    val glassChrome: Dp = 10.dp

    /** 瞬态提示层：Snackbar、临时浮岛。 */
    val floatingChrome: Dp = 12.dp
}
