package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeScheduleRepository : ScheduleRepository {
    private val schedulesState = MutableStateFlow<Map<Int, List<AirSchedule>>>(emptyMap())

    var refreshCallCount: Int = 0
        private set
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)

    fun sendSchedules(
        weekday: Int,
        schedules: List<AirSchedule>,
    ) {
        schedulesState.value =
            schedulesState.value.toMutableMap().apply {
                put(weekday, schedules)
            }
    }

    override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>> = schedulesState.map { it[weekday].orEmpty() }

    var syncBangumiDataCallCount: Int = 0
        private set
    var syncBangumiDataResult: AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSchedules(): AppResult<Unit> {
        refreshCallCount++
        return refreshResult
    }

    override suspend fun syncBangumiData(force: Boolean): AppResult<Unit> {
        syncBangumiDataCallCount++
        return syncBangumiDataResult
    }

    /** 测试可预置的即将播出事件 */
    var upcomingAiring: List<UpcomingAiring> = emptyList()
    var upcomingAiringRequestedSubjectIds: List<Long> = emptyList()
        private set

    override suspend fun getUpcomingAiringForSubjects(
        subjectIds: List<Long>,
        hoursAhead: Long,
        lookbackHours: Long,
    ): List<UpcomingAiring> {
        upcomingAiringRequestedSubjectIds = subjectIds
        return upcomingAiring.filter { it.subjectId in subjectIds.toSet() }
    }

    /** 测试可预置的默认筛选持久化值 */
    var scheduleDefaultOnlyWatching: Boolean = false
        private set

    override suspend fun getScheduleDefaultOnlyWatching(): Boolean = scheduleDefaultOnlyWatching

    override suspend fun setScheduleDefaultOnlyWatching(onlyWatching: Boolean) {
        scheduleDefaultOnlyWatching = onlyWatching
    }
}
