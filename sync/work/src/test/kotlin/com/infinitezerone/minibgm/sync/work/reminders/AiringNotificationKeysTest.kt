package com.infinitezerone.minibgm.sync.work.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiringNotificationKeysTest {
    @Test
    fun subjectTagsDoNotCollideAcrossModuloRange() {
        val subjectIds = listOf(1L, 10_001L, 410_001L, 633_836L)
        val tags = subjectIds.map(AiringNotificationKeys::subjectTag)

        assertEquals(subjectIds.size, tags.toSet().size)
    }

    @Test
    fun subjectTagIsStable() {
        assertEquals("airing_subject_633836", AiringNotificationKeys.subjectTag(633_836L))
    }

    @Test
    fun summaryRequiresAtLeastTwoChildren() {
        assertFalse(AiringNotificationKeys.shouldShowSummary(0))
        assertFalse(AiringNotificationKeys.shouldShowSummary(1))
        assertTrue(AiringNotificationKeys.shouldShowSummary(2))
    }

    @Test
    fun summaryTagIsOutsideChildTagNamespace() {
        assertFalse(AiringNotificationKeys.SUMMARY_TAG.startsWith(AiringNotificationKeys.TAG_PREFIX))
    }
}
