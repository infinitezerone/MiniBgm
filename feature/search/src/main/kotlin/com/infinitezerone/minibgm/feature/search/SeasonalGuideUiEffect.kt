package com.infinitezerone.minibgm.feature.search

/**
 * 一次性 UI 事件。
 *
 * 与 [SeasonalGuideUiState] **物理隔离、各司其职**：
 * - 状态是投影，可随时重组、可重复读取，`Snackbar` 这类一次性动作不能放进去——
 *   否则旋转屏幕 / 返回本页就会把同一条提示再弹一遍，还得配一个"已读"回写来擦除；
 * - 事件走 [Channel][kotlinx.coroutines.channels.Channel]，**消费即消失**，天然不重复、
 *   也不需要任何人去清空它。
 *
 * 目前只有提示；以后跳转、震动等一次性动作都加在这里，不再往状态里塞标记位。
 */
sealed interface UiEffect {
    /** 弹一条 Snackbar 提示 */
    data class ShowMessage(
        val text: String,
    ) : UiEffect
}
