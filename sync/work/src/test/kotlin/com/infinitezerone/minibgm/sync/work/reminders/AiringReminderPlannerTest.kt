package com.infinitezerone.minibgm.sync.work.reminders

import com.infinitezerone.minibgm.core.model.UpcomingAiring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiringReminderPlannerTest {
    private val today = "2026-09-06"
    private val upcoming =
        listOf(
            UpcomingAiring(
                subjectId = 633836L,
                title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                titleCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                episode = 4,
                airAtUtc = "2026-09-07T14:00:00Z",
                kind = "scheduled",
            ),
        )

    @Test
    fun plan_returnsEmpty_whenDisabled() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = false,
                isLoggedIn = true,
                lastNotifiedDate = "",
                today = today,
                upcoming = upcoming,
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun plan_returnsEmpty_whenNotLoggedIn() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = false,
                lastNotifiedDate = "",
                today = today,
                upcoming = upcoming,
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun plan_dedupesWithinSameDay() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = true,
                lastNotifiedDate = "2026-09-06",
                today = today,
                upcoming = upcoming,
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun plan_returnsUpcoming_whenEligible() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = true,
                lastNotifiedDate = "2026-09-05",
                today = today,
                upcoming = upcoming,
            )

        assertEquals(1, planned.size)
        assertEquals(633836L, planned.first().subjectId)
        assertEquals(4, planned.first().episode)
    }

    @Test
    fun plan_allowsAgainNextDay() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = true,
                lastNotifiedDate = "2026-09-06",
                today = "2026-09-07",
                upcoming = upcoming,
            )

        assertEquals(1, planned.size)
    }
}
