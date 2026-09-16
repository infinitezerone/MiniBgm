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
     * @return 智能体执行结果
     */
    suspend fun execute(prompt: String): AppResult<String>

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
