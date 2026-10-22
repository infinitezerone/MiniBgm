package com.infinitezerone.minibgm.core.model

/**
 * 应用主题模式：跟随系统 / 强制亮色 / 强制深色。
 */
enum class ThemeMode(
    val displayName: String,
) {
    SYSTEM("跟随系统"),
    LIGHT("亮色"),
    DARK("深色"),
}
