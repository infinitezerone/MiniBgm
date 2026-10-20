package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 应用视觉语言开关。
 *
 * [Brand] 保留现有 Material 3 表面风格；[Luminous] 只替换界面 chrome 的材质、圆角与光感，
 * 不改变页面信息结构、导航层级与业务流程。
 */
enum class AppUiStyle {
    Brand,
    Luminous,
}

val LocalAppUiStyle = staticCompositionLocalOf { AppUiStyle.Brand }
