package com.infinitezerone.minibgm.core.model

/**
 * 播出事件的可信度层级。
 * 多个数据源对同一话的排期冲突时，按 ACTUAL > SCHEDULED > PREDICTED 仲裁。
 */
object AirEventKind {
    /** 实际已播出（来自逐话记录源） */
    const val ACTUAL = "actual"

    /** 平台/社区已排期（时间可信，尚未播出） */
    const val SCHEDULED = "scheduled"

    /** 由播出规则推算（仅供预期，可能因停播/特番偏差） */
    const val PREDICTED = "predicted"

    /** 仲裁排序：数值越小可信度越高 */
    fun rank(kind: String): Int =
        when (kind) {
            ACTUAL -> 0
            SCHEDULED -> 1
            else -> 2
        }
}
