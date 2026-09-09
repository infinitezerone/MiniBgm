package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonDetailTest {
    @Test
    fun birthdayText_formatsCorrectly() {
        val full = PersonDetail(id = 1L, name = "A", birthYear = 1995, birthMon = 12, birthDay = 6)
        assertEquals("1995年12月6日", full.birthdayText)

        val monDay = PersonDetail(id = 2L, name = "B", birthMon = 5, birthDay = 20)
        assertEquals("5月20日", monDay.birthdayText)

        val monOnly = PersonDetail(id = 3L, name = "C", birthMon = 7)
        assertEquals("7月", monOnly.birthdayText)

        val empty = PersonDetail(id = 4L, name = "D")
        assertNull(empty.birthdayText)
    }

    @Test
    fun careerAndGender_formatsCorrectly() {
        val p =
            PersonDetail(
                id = 1L,
                name = "A",
                career = listOf("seiyu", "artist", "writer", "illustrator", "actor", "other"),
                gender = "female",
            )
        assertEquals("声优 · 歌手/艺术家 · 作家 · 插画师 · 演员 · other", p.careerText)
        assertEquals("女", p.genderText)

        val male = PersonDetail(id = 2L, name = "M", gender = "male")
        assertEquals("男", male.genderText)

        val unknown = PersonDetail(id = 3L, name = "U", gender = "robot")
        assertEquals("robot", unknown.genderText)
    }
}
