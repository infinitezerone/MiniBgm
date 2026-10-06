package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.common.AppResult

/**
 * 一轮结构化会话历史：以真实 user/assistant 角色进入模型 messages 数组，
 * 不再拍平成文本拼接——role 结构保真是工具调用类任务表现的基础。
 */
data class AiHistoryTurn(
    val isUser: Boolean,
    val content: String,
)

/**
 * MiniBgm AI 智能体执行服务接口。
 * 基于 JetBrains Koog 框架构建，支持本地（Ollama）、云端 Google Gemini 与标准 OpenAI 兼容端点。
 */
interface BgmAiAgentService {
    /**
     * 执行智能体推理任务。
     * @param prompt 输入指令或问题
     * @param history 结构化会话历史（按时间正序）；由服务层做投毒清洗与预算裁剪后注入 messages 数组
     * @return 智能体执行结果
     */
    suspend fun execute(
        prompt: String,
        history: List<AiHistoryTurn> = emptyList(),
    ): AppResult<String>

    /**
     * 拉取指定端点或当前配置端点上可用的模型 id 列表（OpenAI 兼容 /models 或 Ollama /api/tags）。
     * 供 AI 设置界面提供"拉取模型列表"能力，避免手填已下线的模型名。
     */
    suspend fun fetchAvailableModels(
        endpoint: String? = null,
        apiKey: String? = null,
        provider: String? = null,
    ): AppResult<List<String>>

    /**
     * HITL 待确认写操作执行器（当用户在 UI 侧确认执行智能体生成的写操作提案时调用）。
     */
    val pendingActionExecutor: PendingActionExecutor?
        get() = null

    /**
     * HITL 待确认操作提案暂存区。
     */
    val pendingActionStore: PendingActionStore?
        get() = null

    /**
     * 播放资源解析结果暂存区。
     */
    val playableSourcesStore: PlayableSourcesStore?
        get() = null
}
