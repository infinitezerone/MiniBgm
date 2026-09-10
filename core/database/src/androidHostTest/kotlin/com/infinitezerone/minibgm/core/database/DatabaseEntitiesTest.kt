package com.infinitezerone.minibgm.core.database

import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseEntitiesTest {
    @Test
    fun airScheduleEntity_beginUtc_returnsBeginAtUtcWhenPresent() {
        val entity =
            AirScheduleEntity(
                bgmId = 1L,
                title = "Test",
                titleCn = "测试",
                coverUrl = "https://example.com/cover.jpg",
                ratingScore = 8.5,
                airDate = "2026-04-01",
                beginAtUtc = "2026-04-01T15:00:00Z",
                weekday = 3,
                timeCst = "23:00",
                timeJst = "00:00",
                sitesJson = "[]",
            )

        assertEquals("2026-04-01T15:00:00Z", entity.beginUtc)
        assertEquals(AirScheduleEntity.SOURCE_OFFICIAL, entity.source)
        assertEquals(AirScheduleEntity.UNKNOWN_SORT_MINUTES, entity.sortMinutes)
        assertNull(entity.anilistId)
    }

    @Test
    fun airScheduleEntity_beginUtc_fallsBackToAirDateWhenBeginAtUtcNull() {
        val entity =
            AirScheduleEntity(
                bgmId = 2L,
                title = "Test 2",
                titleCn = "测试 2",
                coverUrl = "",
                ratingScore = 7.0,
                airDate = "2026-04-02",
                beginAtUtc = null,
                weekday = 4,
                timeCst = "18:00",
                timeJst = "19:00",
                sitesJson = "[]",
            )

        assertEquals("2026-04-02", entity.beginUtc)
    }

    @Test
    fun airEventEntity_properties_matchConstructor() {
        val event =
            AirEventEntity(
                subjectId = 12345L,
                episode = 1,
                airAtUtc = "2026-04-01T15:00:00Z",
                kind = "actual",
                source = "anilist",
            )

        assertEquals(12345L, event.subjectId)
        assertEquals(1, event.episode)
        assertEquals("actual", event.kind)
        assertEquals("anilist", event.source)
    }

    @Test
    fun userCollectionEntity_properties_matchConstructor() {
        val collection =
            UserCollectionEntity(
                userId = 999L,
                subjectId = 12345L,
                subjectType = 2,
                type = 3,
                epStatus = 5,
                updatedAt = "2026-04-01T00:00:00Z",
            )

        assertEquals(999L, collection.userId)
        assertEquals(12345L, collection.subjectId)
        assertEquals(2, collection.subjectType)
        assertEquals(3, collection.type)
        assertEquals(5, collection.epStatus)
        assertEquals("2026-04-01T00:00:00Z", collection.updatedAt)
    }
}
