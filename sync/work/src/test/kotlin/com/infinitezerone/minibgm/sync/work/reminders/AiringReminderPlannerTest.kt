package com.infinitezerone.minibgm.sync.work.reminders

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
                currentHour = 9,
                reminderHour = 8,
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
                currentHour = 9,
                reminderHour = 8,
                upcoming = upcoming,
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun plan_returnsEmpty_beforeReminderHour() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = true,
                lastNotifiedDate = "",
                today = today,
                currentHour = 7,
                reminderHour = 8,
                upcoming = upcoming,
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun plan_notifiesAtReminderHour() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = true,
                lastNotifiedDate = "2026-09-05",
                today = today,
                currentHour = 8,
                reminderHour = 8,
                upcoming = upcoming,
            )

        assertEquals(1, planned.size)
    }

    @Test
    fun plan_dedupesWithinSameDay() {
        val planned =
            AiringReminderPlanner.plan(
                enabled = true,
                isLoggedIn = true,
                lastNotifiedDate = "2026-09-06",
                today = today,
                currentHour = 9,
                reminderHour = 8,
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
                currentHour = 21,
                reminderHour = 8,
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
                currentHour = 9,
                reminderHour = 8,
                upcoming = upcoming,
            )

        assertEquals(1, planned.size)
    }

    // ---- pickPreAir：开播前提醒 ----

    private val nowEpochMillis = 1_788_000_000_000L

    /** 以 [upcoming] 首条为模板，开播时刻平移 deltaMinutes 分钟 */
    private fun airingAt(deltaMinutes: Long): UpcomingAiring =
        upcoming
            .first()
            .copy(airAtUtc = TimeUtils.isoUtcFromEpochMillis(nowEpochMillis + deltaMinutes * 60_000L))

    private fun pickPreAir(
        item: UpcomingAiring,
        notifiedKeys: List<String> = emptyList(),
        leadMinutes: Long = 15L,
    ): List<UpcomingAiring> =
        AiringReminderPlanner.pickPreAir(
            enabled = true,
            isLoggedIn = true,
            notifiedKeys = notifiedKeys,
            today = today,
            nowEpochMillis = nowEpochMillis,
            leadMinutes = leadMinutes,
            upcoming = listOf(item),
        )

    @Test
    fun pickPreAir_returnsEmpty_whenDisabled() {
        val planned =
            AiringReminderPlanner.pickPreAir(
                enabled = false,
                isLoggedIn = true,
                notifiedKeys = emptyList(),
                today = today,
                nowEpochMillis = nowEpochMillis,
                leadMinutes = 15L,
                upcoming = listOf(airingAt(10)),
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun pickPreAir_returnsEmpty_whenNotLoggedIn() {
        val planned =
            AiringReminderPlanner.pickPreAir(
                enabled = true,
                isLoggedIn = false,
                notifiedKeys = emptyList(),
                today = today,
                nowEpochMillis = nowEpochMillis,
                leadMinutes = 15L,
                upcoming = listOf(airingAt(10)),
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun pickPreAir_picksWithinLeadWindow() {
        val planned = pickPreAir(airingAt(10))

        assertEquals(1, planned.size)
        assertEquals(633836L, planned.first().subjectId)
    }

    @Test
    fun pickPreAir_picksAtExactLeadBoundary() {
        val planned = pickPreAir(airingAt(15))

        assertEquals(1, planned.size)
    }

    @Test
    fun pickPreAir_skipsBeyondLeadWindow() {
        val planned = pickPreAir(airingAt(16))

        assertTrue(planned.isEmpty())
    }

    @Test
    fun pickPreAir_picksWithinGraceWindowWhenSlightlyLate() {
        // 在容错窗口内（例如晚了 5 分钟），仍能捕获到通知（已开播状态）
        val planned = pickPreAir(airingAt(-5))

        assertEquals(1, planned.size)
        assertTrue(AiringReminderPlanner.isAlreadyStarted(airingAt(-5), nowEpochMillis))
    }

    @Test
    fun pickPreAir_picksLateArrivalWithinAlignedGraceWindow() {
        // 回归：容错窗口与 setAndAllowWhileIdle 的 1 小时调度窗口对齐后，
        // 系统晚唤醒 45 分钟仍应补发「现已开播」，而不是静默丢弃
        val item = airingAt(-45)
        val planned = pickPreAir(item)

        assertEquals(1, planned.size)
        assertTrue(AiringReminderPlanner.isAlreadyStarted(item, nowEpochMillis))
    }

    @Test
    fun pickPreAir_skipsBeyondGraceWindow() {
        // 超出容错窗口（已过去 61 分钟），不再打扰用户
        val planned = pickPreAir(airingAt(-61))

        assertTrue(planned.isEmpty())
    }

    @Test
    fun preAirLookbackHours_coversGraceWindowAndDelayOffset() {
        // 回看窗口必须同时覆盖容错窗口与源延迟偏移，否则条目在进入容错判定前就被 SQL 排除
        assertEquals(1L, AiringReminderPlanner.preAirLookbackHours(airDelayOffsetMinutes = 0L))
        assertEquals(2L, AiringReminderPlanner.preAirLookbackHours(airDelayOffsetMinutes = 15L))
        assertEquals(3L, AiringReminderPlanner.preAirLookbackHours(airDelayOffsetMinutes = 120L))
        // 负值（提前提醒）不应让回看窗口缩到容错区间以下
        assertEquals(1L, AiringReminderPlanner.preAirLookbackHours(airDelayOffsetMinutes = -60L))
    }

    @Test
    fun pickPreAir_respectsDelayOffset() {
        // 播出时间虽然已过 5 分钟，但用户设置了源延迟 15 分钟，实际距离开播还有 10 分钟
        val planned =
            AiringReminderPlanner.pickPreAir(
                enabled = true,
                isLoggedIn = true,
                notifiedKeys = emptyList(),
                today = today,
                nowEpochMillis = nowEpochMillis,
                leadMinutes = 15L,
                airDelayOffsetMinutes = 15L,
                upcoming = listOf(airingAt(-5)),
            )

        assertEquals(1, planned.size)
        // 加上延迟后实际尚未播出
        assertTrue(!AiringReminderPlanner.isAlreadyStarted(airingAt(-5), nowEpochMillis, airDelayOffsetMinutes = 15L))
    }

    @Test
    fun nextAiringSchedule_picksNearestFutureEpisode() {
        val ep1 = airingAt(30)
        val ep2 = airingAt(10)
        val ep3 = airingAt(-90) // 超出容错窗口，已过期

        val next =
            AiringReminderPlanner.nextAiringSchedule(
                notifiedKeys = emptyList(),
                nowEpochMillis = nowEpochMillis,
                leadMinutes = 15L,
                upcoming = listOf(ep1, ep2, ep3),
            )

        // ep2 开播在 10 分钟后，提前 15 分钟应当立刻触发 (maxOf(now, targetTrigger) == now)
        assertEquals(ep2.subjectId, next?.first?.subjectId)
        assertEquals(nowEpochMillis, next?.second)
    }

    @Test
    fun nextAiringSchedule_schedulesImmediateCatchUpForLateEpisode() {
        // 回归：App 在开播后重新核准（冷启动 / 重装 / 开机 / 改时区）时，
        // 容错窗口内刚错过的剧集必须仍被选为「立即触发」——这是配合查询侧
        // lookback 把漏掉的提醒补回来的关键一环
        val late = airingAt(-45)

        val next =
            AiringReminderPlanner.nextAiringSchedule(
                notifiedKeys = emptyList(),
                nowEpochMillis = nowEpochMillis,
                leadMinutes = 0L,
                upcoming = listOf(late),
            )

        assertEquals(late.subjectId, next?.first?.subjectId)
        assertEquals(nowEpochMillis, next?.second)
    }

    @Test
    fun nextAiringSchedule_ignoresEpisodeBeyondGraceWindow() {
        val next =
            AiringReminderPlanner.nextAiringSchedule(
                notifiedKeys = emptyList(),
                nowEpochMillis = nowEpochMillis,
                leadMinutes = 0L,
                upcoming = listOf(airingAt(-61)),
            )

        assertEquals(null, next)
    }

    @Test
    fun pruneNotifiedKeys_retainsYesterdayToSurviveMidnightRollover() {
        val yesterdayKey = "2026-09-05:633836:4:2026-09-07T14:00:00Z"
        val todayKey = "$today:633836:5:2026-09-08T14:00:00Z"
        val staleKey = "2026-09-04:633836:3:2026-09-06T14:00:00Z"

        val retained =
            AiringReminderPlanner.pruneNotifiedKeys(
                existing = listOf(yesterdayKey, todayKey, staleKey),
                today = today,
            )

        // 跨日补发时仍能比对到昨天的键，避免同一集被重复推送；更早的键正常回收
        assertEquals(listOf(yesterdayKey, todayKey), retained)
    }

    @Test
    fun pruneNotifiedKeys_fallsBackToLiteralPrefixWhenDateUnparsable() {
        // 防御分支：日期不可解析时回退为字面前缀匹配——既不抛异常，也不因裁剪规则
        // 失效而把当期键一并删掉（删掉会导致同一集重复推送）
        val literalKey = "not-a-date:633836:4:2026-09-07T14:00:00Z"
        val staleKey = "2026-09-05:633836:3:2026-09-06T14:00:00Z"

        val retained =
            AiringReminderPlanner.pruneNotifiedKeys(
                existing = listOf(literalKey, staleKey),
                today = "not-a-date",
            )

        assertEquals(listOf(literalKey), retained)
    }

    @Test
    fun pickPreAir_skipsAlreadyNotified() {
        val item = airingAt(10)
        val planned =
            pickPreAir(
                item,
                notifiedKeys = listOf(AiringReminderPlanner.preAirKey(today, item)),
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun pickPreAir_dedupesSameAiringAcrossMidnight() {
        // 回归：临近午夜开播的剧集在日期翻转前后各进入一次窗口，
        // 去重必须按内容（subjectId:episode:airAtUtc）跨日生效而非按通知日
        val item = airingAt(10)
        val planned =
            pickPreAir(
                item,
                notifiedKeys = listOf("2026-09-05:" + AiringReminderPlanner.preAirContentKey(item)),
            )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun pickPreAir_doesNotDedupeDifferentAirings() {
        val item = airingAt(10)
        val otherAiring = item.copy(airAtUtc = "2026-09-07T15:00:00Z")
        val planned =
            pickPreAir(
                item,
                notifiedKeys = listOf("2026-09-05:" + AiringReminderPlanner.preAirContentKey(otherAiring)),
            )

        assertEquals(1, planned.size)
    }
}
