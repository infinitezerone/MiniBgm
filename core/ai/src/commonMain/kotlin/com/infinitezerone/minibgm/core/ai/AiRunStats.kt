package com.infinitezerone.minibgm.core.ai

/**
 * 单次智能体运行的可观测统计：轮次、工具调用、流式覆盖与 token 用量。
 * 每次运行结束时由 [AiToolActivity.lastRunStats] 发布最近一次的值（不清空，保留供 UI/日志读取）。
 */
data class AiRunStats(
    /** 实际执行的模型轮次数（含触发总结的收尾轮） */
    val turns: Int,
    /** 真实执行的工具调用总数（不含注入引导提示的重复调用与超限跳过） */
    val toolCallCount: Int,
    /** 其中以流式收到正文增量的轮次数 */
    val streamedTurns: Int,
    /** 端点未回 usage 时为 null */
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val totalTokens: Long? = null,
    /** 整次运行的挂钟耗时 */
    val elapsedMs: Long,
)
