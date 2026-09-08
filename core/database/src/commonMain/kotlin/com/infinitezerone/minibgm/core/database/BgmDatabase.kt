package com.infinitezerone.minibgm.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.EpisodeDao
import com.infinitezerone.minibgm.core.database.dao.SubjectDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.EpisodeEntity
import com.infinitezerone.minibgm.core.database.entity.SubjectEntity
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity

@Database(
    entities = [
        SubjectEntity::class,
        EpisodeEntity::class,
        AirScheduleEntity::class,
        UserCollectionEntity::class,
        AirEventEntity::class,
    ],
    version = 2,
    // 导出 schema JSON 到 core/database/schemas（KmpRoomConventionPlugin 已配 schemaDirectory），
    // 为后续 Migration 提供可审计的迁移历史
    exportSchema = true,
)
abstract class BgmDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao

    abstract fun airScheduleDao(): AirScheduleDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun userCollectionDao(): UserCollectionDao

    abstract fun airEventDao(): AirEventDao
}
