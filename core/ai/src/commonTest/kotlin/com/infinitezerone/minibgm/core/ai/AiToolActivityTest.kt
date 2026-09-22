package com.infinitezerone.minibgm.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiToolActivityTest {
    @Test
    fun report_formatsToolNameWithDetail() {
        AiToolActivity.clear()
        AiToolActivity.report("查询放送表", "周3")
        assertEquals("正在调用工具：查询放送表（周3）", AiToolActivity.current.value)
    }

    @Test
    fun report_appendsToTimelineAndCurrentStaysLast() {
        AiToolActivity.clear()
        AiToolActivity.reportStatus("AI 正在思考并检索...")
        AiToolActivity.report("搜索动画条目", "关键词：芙莉莲")
        AiToolActivity.report("获取条目详情", "条目 ID 1001")

        assertEquals("正在调用工具：获取条目详情（条目 ID 1001）", AiToolActivity.current.value)
        val events = AiToolActivity.events.value
        assertEquals(3, events.size)
        assertTrue(events[0].text.contains("思考"))
        assertTrue(events[2].text.contains("条目详情"))
    }

    @Test
    fun clear_resetsTimelineAndCurrent() {
        AiToolActivity.report("查询放送表")
        AiToolActivity.clear()
        assertNull(AiToolActivity.current.value)
        assertTrue(AiToolActivity.events.value.isEmpty())
    }
}
