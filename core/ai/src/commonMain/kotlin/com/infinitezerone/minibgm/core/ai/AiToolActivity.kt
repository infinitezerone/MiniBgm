package com.infinitezerone.minibgm.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI 工具执行活动（进程内共享）：把"正在调用哪个工具、进行到哪一步"暴露给 UI，
 * 消除推理模型多轮往返期间（每轮 12~30s）的静默感。
 * 纯装饰信息：写入方吞掉所有异常，读取方只读快照。
 */
object AiToolActivity {
    private val _current = MutableStateFlow<String?>(null)

    /** 当前活动描述；null 表示无进行中的工具调用 */
    val current: StateFlow<String?> = _current.asStateFlow()

    fun report(
        tool: String,
        detail: String? = null,
    ) {
        _current.value =
            buildString {
                append("正在调用工具：")
                append(tool)
                if (!detail.isNullOrBlank()) {
                    append("（")
                    append(detail)
                    append("）")
                }
            }
    }

    fun reportStatus(status: String) {
        _current.value = status
    }

    fun clear() {
        _current.value = null
    }
}
