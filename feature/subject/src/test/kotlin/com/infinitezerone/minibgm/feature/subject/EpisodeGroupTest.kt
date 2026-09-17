package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeGroupTest {
    @Test
    fun fromType_mapsAllBangumiTypesToExpectedGroups() {
        assertEquals(EpisodeGroup.MAIN, EpisodeGroup.fromType(0))
        assertEquals("本篇", EpisodeGroup.MAIN.label)

        assertEquals(EpisodeGroup.SP, EpisodeGroup.fromType(1))
        assertEquals("特别篇", EpisodeGroup.SP.label)

        assertEquals(EpisodeGroup.OP_ED, EpisodeGroup.fromType(2))
        assertEquals(EpisodeGroup.OP_ED, EpisodeGroup.fromType(3))
        assertEquals("OP/ED", EpisodeGroup.OP_ED.label)

        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(4))
        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(5))
        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(6))
        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(99))
        assertEquals("其他", EpisodeGroup.OTHER.label)
    }
}
