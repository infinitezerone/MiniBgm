package com.infinitezerone.minibgm.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次智能体运行中的单条活动记录：工具调用或状态说明 */
data class AiActivityEvent(
    val text: String,
    val timestampMs: Long,
)

/**
 * AI 工具执行活动（进程内共享）：把"正在调用哪个工具、进行到哪一步"暴露给 UI，
 * 消除推理模型多轮往返期间（每轮 12~30s）的静默感。
 *
 * [events] 保留本次运行至今的完整步骤序列（供 UI 展示"第 N 步 / 时间线"）；
 * [current] 始终等于最后一条活动的文本（兼容既有读取方）。
 * 纯装饰信息：写入方吞掉所有异常，读取方只读快照。
 */
object AiToolActivity {
    private val _events = MutableStateFlow<List<AiActivityEvent>>(emptyList())
    private val _current = MutableStateFlow<String?>(null)
    private val _streamingText = MutableStateFlow<String?>(null)

    /** 本次运行的活动序列，按发生顺序排列；每次 [clear] 后重置 */
    val events: StateFlow<List<AiActivityEvent>> = _events.asStateFlow()

    /** 当前活动描述；null 表示无进行中的工具调用 */
    val current: StateFlow<String?> = _current.asStateFlow()

    /**
     * 当前轮次已流式产出的正文增量（累积文本）；null 表示本轮尚无流式输出。
     * 每轮开始时重置——中途轮次的预览文本会随工具调用被下一轮覆盖，最终回复在运行结束时
     * 由 UI 落成正式消息气泡。StateFlow 自带合并，高频 token 增量不会淹没收集方。
     */
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private fun append(text: String) {
        _current.value = text
        _events.value = _events.value + AiActivityEvent(text = text, timestampMs = System.currentTimeMillis())
    }

    fun report(
        tool: String,
        detail: String? = null,
    ) {
        append(
            buildString {
                append("正在调用工具：")
                append(tool)
                if (!detail.isNullOrBlank()) {
                    append("（")
                    append(detail)
                    append("）")
                }
            },
        )
    }

    fun reportStatus(status: String) {
        append(status)
    }

    fun appendStreamText(fragment: String) {
        if (fragment.isEmpty()) return
        _streamingText.value = (_streamingText.value ?: "") + fragment
    }

    fun resetStreamText() {
        _streamingText.value = null
    }

    fun clear() {
        _current.value = null
        _events.value = emptyList()
        _streamingText.value = null
    }
}
