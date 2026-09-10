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

    @Test
    fun parseTimeToMinutes_calculatesCorrectMinutes() {
        assertEquals(0, TimeUtils.parseTimeToMinutes("00:00"))
        assertEquals(1110, TimeUtils.parseTimeToMinutes("18:30"))
        assertEquals(1439, TimeUtils.parseTimeToMinutes("23:59"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes(""))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("invalid"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("25:00"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("12:60"))
    }

    @Test
    fun normalizeIsoUtc_removesFractionalSeconds() {
        assertEquals("2026-08-12T14:00:00Z", TimeUtils.normalizeIsoUtc("2026-08-12T14:00:00.000Z"))
        assertEquals("2026-08-12T14:00:00Z", TimeUtils.normalizeIsoUtc("2026-08-12T14:00:00Z"))
        assertEquals("", TimeUtils.normalizeIsoUtc(""))
        assertEquals("invalid-string", TimeUtils.normalizeIsoUtc("invalid-string"))
    }

    @Test
    fun epochMillisOfIso_supportsBareDateFallback() {
        val millis = TimeUtils.epochMillisOfIso("1996-01-08")
        assertTrue(millis != null)
        val iso = TimeUtils.epochMillisOfIso("1996-01-08T00:00:00Z")
        assertTrue(iso != null)
    }
}
