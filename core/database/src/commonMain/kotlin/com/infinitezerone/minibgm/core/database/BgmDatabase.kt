package com.infinitezerone.minibgm.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.dao.AssistantSessionDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity

@Database(
    entities = [
        AirScheduleEntity::class,
        UserCollectionEntity::class,
        AirEventEntity::class,
        AssistantMessageEntity::class,
        AssistantSessionEntity::class,
        AniListBgmMappingEntity::class,
        BangumiDataMonthEtagEntity::class,
    ],
    // v9：AniList↔bgm 映射缓存 + bangumi-data 月切片 ETag（MIGRATION_8_9 只新增两张表，不动既有数据）。
    // v8：助手多会话（assistant_sessions 表 + messages.sessionId 列）。
    version = 9,
    // 导出 schema JSON 到 core/database/schemas（KmpRoomConventionPlugin 已配 schemaDirectory），
    // 为后续 Migration 提供可审计的迁移历史
    exportSchema = true,
)
abstract class BgmDatabase : RoomDatabase() {
    abstract fun airScheduleDao(): AirScheduleDao

    abstract fun userCollectionDao(): UserCollectionDao

    abstract fun airEventDao(): AirEventDao

    abstract fun assistantMessageDao(): AssistantMessageDao

    abstract fun assistantSessionDao(): AssistantSessionDao

    abstract fun anilistMappingDao(): AniListMappingDao
}
