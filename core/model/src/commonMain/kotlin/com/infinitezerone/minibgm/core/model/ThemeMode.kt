package com.infinitezerone.minibgm.core.model

/**
 * 应用主题模式。AMOLED 纯黑是深色模式下的叠加档，不在此枚举内——
 * 它只改变表面/容器阶梯的明度，不改变"亮/暗"本身的选择。
 */
enum class ThemeMode(
    val displayName: String,
) {
    SYSTEM("跟随系统"),
    LIGHT("亮色"),
    DARK("深色"),
}
