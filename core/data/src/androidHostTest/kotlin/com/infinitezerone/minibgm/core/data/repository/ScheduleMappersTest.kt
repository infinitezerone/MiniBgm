package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.network.SeasonSnapshotItemDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleMappersTest {
    @Test
    fun seasonSnapshotItemDto_toSubject_mapsCorrectlyWithBgmId() {
        val dto =
            SeasonSnapshotItemDto(
                anilistId = 1001L,
                bgmId = 2002L,
                title = "Test Anime",
                titleCn = "测试动画",
                coverUrl = "https://example.com/cover.jpg",
                format = "TV",
                airDate = "2026-04-01",
                episodes = 12,
                genres = listOf("Action", "Comedy"),
                tags = listOf("School", "Comedy"),
                ratingScore = 8.5,
                isAdult = false,
            )

        val subject = dto.toSubject()

        assertEquals(2002L, subject.id)
        assertEquals("Test Anime", subject.name)
        assertEquals("测试动画", subject.nameCn)
        assertEquals("TV", subject.platform)
        assertEquals(listOf("TV", "日本"), subject.metaTags)
        assertEquals("2026-04-01", subject.airDate)
        assertEquals(12, subject.eps)
        assertNotNull(subject.rating)
        assertEquals(8.5, subject.rating?.score)
        assertNotNull(subject.images)
        assertEquals("https://example.com/cover.jpg", subject.images?.large)

        val tagNames = subject.tags.map { it.name }
        assertEquals(listOf("Action", "Comedy", "School"), tagNames)
    }

    @Test
    fun seasonSnapshotItemDto_toSubject_unmappedFallbackAndAdultTag() {
        val dto =
            SeasonSnapshotItemDto(
                anilistId = 5005L,
                bgmId = null,
                title = "Adult OVA",
                titleCn = null,
                coverUrl = "",
                format = "",
                airDate = null,
                episodes = 2,
                genres = listOf("Romance"),
                tags = emptyList(),
                ratingScore = 0.0,
                isAdult = true,
            )

        val subject = dto.toSubject()

        assertEquals(-5005L, subject.id)
        assertEquals("Adult OVA", subject.name)
        assertEquals("", subject.nameCn)
        assertEquals("TV", subject.platform) // format fallback
        assertEquals("", subject.airDate)
        assertNull(subject.images)
        assertNull(subject.rating)

        val tagNames = subject.tags.map { it.name }
        assertTrue(tagNames.contains("Hentai"))
        assertTrue(tagNames.contains("Romance"))
    }

    @Test
    fun extractSubjectTags_alreadyHasHentai_doesNotDuplicate() {
        val tags = extractSubjectTags(listOf("hentai"), emptyList(), isAdult = true)
        assertEquals(1, tags.size)
        assertEquals("hentai", tags.first().name)
    }

    @Test
    fun toSubjectImages_blankOrNull_returnsNull() {
        assertNull(toSubjectImages(null))
        assertNull(toSubjectImages(""))
        assertNull(toSubjectImages("   "))
    }
}
