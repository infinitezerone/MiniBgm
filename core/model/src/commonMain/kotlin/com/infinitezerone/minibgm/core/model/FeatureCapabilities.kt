package com.infinitezerone.minibgm.core.model

/**
 * 应用全局特性能力开关集（基于当前编译配置与开发者覆写策略派生的运行时能力单一真源）。
 *
 * 遵循单向数据流与单一真源原则，统一声明当前环境中各项高阶特性的可达性，
 * 杜绝通过多层 UI 回调（Prop Drilling）或未受控的布尔参数手动接线导致的入口漏关与僵尸 UI。
 *
 * @property isAiAssistantEnabled AI 追番助手是否可用（含各页面 AI 找源入口与助手独立会话）
 * @property isInternalPlayerEnabled 内置视频播放器是否可用（含流媒体播放、在线解析与离线视频回放）
 */
data class FeatureCapabilities(
    val isAiAssistantEnabled: Boolean = true,
    val isInternalPlayerEnabled: Boolean = true,
)
