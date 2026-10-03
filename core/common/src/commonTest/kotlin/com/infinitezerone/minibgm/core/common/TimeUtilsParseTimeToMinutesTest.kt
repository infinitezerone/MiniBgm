package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeUtilsParseTimeToMinutesTest {
    @Test
    fun validTimesConvertToMinuteOfDay() {
        assertEquals(0, TimeUtils.parseTimeToMinutes("00:00"))
        assertEquals(8 * 60 + 5, TimeUtils.parseTimeToMinutes("08:05"))
        assertEquals(23 * 60 + 59, TimeUtils.parseTimeToMinutes("23:59"))
    }

    @Test
    fun malformedInputReturnsSentinel() {
        assertEquals(9999, TimeUtils.parseTimeToMinutes(""))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("8:05"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("0805"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("08-05"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("08:0a"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("ab:05"))
    }

    @Test
    fun outOfRangeComponentsReturnSentinel() {
        assertEquals(9999, TimeUtils.parseTimeToMinutes("24:00"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("00:60"))
        assertEquals(9999, TimeUtils.parseTimeToMinutes("99:99"))
    }
}
