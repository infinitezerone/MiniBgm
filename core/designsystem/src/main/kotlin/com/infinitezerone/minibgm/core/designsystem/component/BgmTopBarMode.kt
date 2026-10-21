package com.infinitezerone.minibgm.core.designsystem.component

/**
 * MiniBgm 顶栏视觉与交互模式。
 *
 * 用于协调 [BgmTopAppBar] 与 [BgmFloatingPageScaffold] 的材质分工、层级与滚动穿透行为。
 */
enum class BgmTopBarMode {
    /**
     * 沉浸天幕渐隐模式：
     * - 顶栏背景完全透明，无底部分割线；
     * - 顶栏下方全幅叠加 108dp 的 [BgmTopChromeScrim] 天幕渐隐遮罩；
     * - 页面内容全幅铺满（Edge-to-Edge），向上滚动时平滑穿入天幕消融；
     * - 适用于时间表、探索发现、条目详情等媒体/信息流浏览界面。
     */
    Immersive,

    /**
     * 实体稳固结构模式：
     * - 顶栏具有坚实表面底色，并完整覆盖状态栏区域；
     * - 底部具有精致的 hairline 分割线；
     * - 禁用天幕渐隐遮罩；
     * - 适用于设置、历史、离线管理、规则设置等结构化功能界面。
     */
    Solid,
}
