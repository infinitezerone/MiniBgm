package com.infinitezerone.minibgm.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AirScheduleDao {
    @Query("SELECT * FROM air_schedules WHERE weekday = :weekday ORDER BY sortMinutes ASC, ratingScore DESC")
    fun getSchedulesByWeekday(weekday: Int): Flow<List<AirScheduleEntity>>

    @Query("SELECT * FROM air_schedules")
    fun getAllSchedules(): Flow<List<AirScheduleEntity>>

    @Query("SELECT * FROM air_schedules")
    suspend fun getAllSchedulesList(): List<AirScheduleEntity>

    @Query("SELECT * FROM air_schedules WHERE bgmId IN (:ids)")
    suspend fun getSchedulesByIds(ids: List<Long>): List<AirScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<AirScheduleEntity>)

    /** 清理官方日历中已消失的条目（只清理 official 来源，保护 bgm-data 合并插入的网播番） */
    @Query("DELETE FROM air_schedules WHERE source = 'official' AND bgmId NOT IN (:keepIds)")
    suspend fun deleteOfficialSchedulesNotIn(keepIds: List<Long>)

    /** 清理超出名单窗口的 bgm-data 合并行（防止过期网播番长期滞留） */
    @Query("DELETE FROM air_schedules WHERE source = 'bgm_data' AND airDate < :date")
    suspend fun deleteStaleBgmDataSchedules(date: String)

    @Query("DELETE FROM air_schedules")
    suspend fun clearSchedules()
}

@Dao
interface AirEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAirEvents(events: List<AirEventEntity>)

    @Query("SELECT * FROM air_events")
    suspend fun getAllAirEvents(): List<AirEventEntity>

    @Query("DELETE FROM air_events WHERE kind = 'predicted' AND airAtUtc < :isoUtc")
    suspend fun deleteStalePredictedEvents(isoUtc: String)

    @Query("DELETE FROM air_events WHERE kind = 'predicted'")
    suspend fun deleteAllPredictedEvents()

    @Query("DELETE FROM air_events WHERE subjectId NOT IN (:keepIds)")
    suspend fun deleteEventsNotIn(keepIds: List<Long>)

    @Query(
        "SELECT * FROM air_events " +
            "WHERE subjectId IN (:subjectIds) AND airAtUtc >= :fromIso AND airAtUtc <= :toIso " +
            "ORDER BY airAtUtc ASC",
    )
    suspend fun getUpcomingEvents(
        subjectIds: List<Long>,
        fromIso: String,
        toIso: String,
    ): List<AirEventEntity>
}

@Dao
interface UserCollectionDao {
    @Query("SELECT * FROM user_collections WHERE userId = :userId AND type = :type ORDER BY updatedAt DESC")
    fun getCollectionsByType(
        userId: Long,
        type: Int,
    ): Flow<List<UserCollectionEntity>>

    @Query("SELECT * FROM user_collections WHERE userId = :userId AND subjectId = :subjectId")
    fun getCollectionBySubjectId(
        userId: Long,
        subjectId: Long,
    ): Flow<UserCollectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: UserCollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<UserCollectionEntity>)

    @Query("DELETE FROM user_collections WHERE userId = :userId AND subjectId = :subjectId")
    suspend fun deleteBySubjectId(
        userId: Long,
        subjectId: Long,
    )

    @Query("DELETE FROM user_collections WHERE userId = :userId AND type = :type")
    suspend fun deleteByType(
        userId: Long,
        type: Int,
    )

    @Transaction
    suspend fun replaceCollectionsByType(
        userId: Long,
        type: Int,
        collections: List<UserCollectionEntity>,
    ) {
        deleteByType(userId, type)
        insertCollections(collections)
    }

    @Query("DELETE FROM user_collections WHERE userId = :userId")
    suspend fun clearByUserId(userId: Long)

    @Query("DELETE FROM user_collections")
    suspend fun clearAll()
}
