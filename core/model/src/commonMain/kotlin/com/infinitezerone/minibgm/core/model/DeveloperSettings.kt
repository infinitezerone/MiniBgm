package com.infinitezerone.minibgm.core.model

/**
 * 客户端运行时开发者调试设置。
 *
 * 用于在真机/测试包中动态覆盖特性开关、模拟原生插件异常状态、排查网络与崩溃日志，
 * 避免每次测试改动都需要重新打 Gradle 包。
 *
 * @property isDeveloperModeEnabled 是否已激活开发者模式
 * @property enableAiOverride AI 助手功能开关覆写（null = 跟随编译默认，true = 强制启用，false = 强制禁用）
 * @property enablePlayerOverride 内置播放器开关覆写（null = 跟随编译默认，true = 强制启用，false = 强制禁用）
 * @property verboseLoggingEnabled 详细网络与引擎日志开关
 * @property simulateTorrentUninstalled 模拟 BT 原生流化组件未安装（测试动态下载引导与安全加载）
 * @property customMirrorPrefixOverride 强制加速镜像前缀覆写（空串表示使用默认规则）
 */
data class DeveloperSettings(
    val isDeveloperModeEnabled: Boolean = false,
    val enableAiOverride: Boolean? = null,
    val enablePlayerOverride: Boolean? = null,
    val verboseLoggingEnabled: Boolean = false,
    val simulateTorrentUninstalled: Boolean = false,
    val customMirrorPrefixOverride: String = "",
) {
    /** 当前生效的 AI 助手状态（优先覆写值，未显式覆写则默认启用） */
    val isAiAssistantEffective: Boolean get() = enableAiOverride ?: true

    /** 当前生效的内置播放器状态（优先覆写值，未显式覆写则默认启用） */
    val isInternalPlayerEffective: Boolean get() = enablePlayerOverride ?: true
}
