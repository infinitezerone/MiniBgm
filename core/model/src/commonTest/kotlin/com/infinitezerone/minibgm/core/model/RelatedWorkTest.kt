package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelatedWorkTest {
    @Test
    fun aggregateBySubject_emptyListReturnsEmpty() {
        val empty = emptyList<RelatedWork>()
        assertTrue(empty.aggregateBySubject().isEmpty())
    }

    @Test
    fun aggregateBySubject_mergesDuplicateSubjectsAndCombinesStaff() {
        val works =
            listOf(
                RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "原作"),
                RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "导演"),
                RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "脚本"),
                RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "摄影监督"),
                RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "剪辑"),
                RelatedWork(id = 1001L, name = "イヴの時間", staff = "导演"),
            )

        val aggregated = works.aggregateBySubject()

        assertEquals(2, aggregated.size)
        assertEquals(29414L, aggregated[0].id)
        assertEquals("原作 / 导演 / 脚本 / 摄影监督 / 剪辑", aggregated[0].staff)
        assertEquals(1001L, aggregated[1].id)
        assertEquals("导演", aggregated[1].staff)
    }

    @Test
    fun aggregateBySubject_handlesBlankAndDuplicateStaff() {
        val works =
            listOf(
                RelatedWork(id = 100L, name = "作品A", staff = "导演"),
                RelatedWork(id = 100L, name = "作品A", staff = "导演"),
                RelatedWork(id = 100L, name = "作品A", staff = " "),
                RelatedWork(id = 100L, name = "作品A", staff = "分镜"),
            )

        val aggregated = works.aggregateBySubject()

        assertEquals(1, aggregated.size)
        assertEquals(100L, aggregated[0].id)
        assertEquals("导演 / 分镜", aggregated[0].staff)
    }
}
