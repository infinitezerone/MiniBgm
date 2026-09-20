package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.common.AppResult

/**
 * MiniBgm AI 智能体执行服务接口。
 * 基于 JetBrains Koog 框架构建，支持本地（Ollama）、云端 Google Gemini 与标准 OpenAI 兼容端点。
 */
interface BgmAiAgentService {
    /**
     * 执行智能体推理任务。
     * @param prompt 输入指令或问题
     * @param history 会话历史列表，每项为 Pair(role, content)，如 ("user", "..."), ("assistant", "...")
     * @return 智能体执行结果
     */
    suspend fun execute(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
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
}
