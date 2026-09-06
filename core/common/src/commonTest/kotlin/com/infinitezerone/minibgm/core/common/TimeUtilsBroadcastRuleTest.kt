package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeUtilsBroadcastRuleTest {
    @Test
    fun parseBroadcastRule_parsesWeeklyRepeat() {
        val parsed = TimeUtils.parseBroadcastRule("R/2026-08-12T14:00:00.000Z/P7D")

        assertTrue(parsed != null)
        val (startMillis, periodMillis) = parsed
        assertEquals(TimeUtils.epochMillisOfIso("2026-08-12T14:00:00Z"), startMillis)
        assertEquals(7L * 24 * 60 * 60 * 1000, periodMillis)
    }

    @Test
    fun parseBroadcastRule_parsesWeeklyShorthand() {
        val parsed = TimeUtils.parseBroadcastRule("R/2026-08-12T14:00:00Z/P1W")

        assertTrue(parsed != null)
        assertEquals(7L * 24 * 60 * 60 * 1000, parsed.second)
    }

    @Test
    fun parseBroadcastRule_parsesCountedRepeat() {
        // R12 = 重复 12 次；周期解析不受次数影响
        val parsed = TimeUtils.parseBroadcastRule("R12/2026-08-12T14:00:00.000Z/P7D")

        assertTrue(parsed != null)
        assertEquals(TimeUtils.epochMillisOfIso("2026-08-12T14:00:00Z"), parsed.first)
    }

    @Test
    fun parseBroadcastRule_rejectsInvalidRules() {
        assertNull(TimeUtils.parseBroadcastRule(""))
        assertNull(TimeUtils.parseBroadcastRule("P7D"))
        assertNull(TimeUtils.parseBroadcastRule("R/2026-08-12T14:00:00.000Z"))
        assertNull(TimeUtils.parseBroadcastRule("R/2026-08-12T14:00:00.000Z/PT"))
    }
}
