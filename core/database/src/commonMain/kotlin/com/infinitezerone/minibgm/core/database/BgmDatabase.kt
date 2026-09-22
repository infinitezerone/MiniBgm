package com.infinitezerone.minibgm.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.dao.AssistantSessionDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity

@Database(
    entities = [
        AirScheduleEntity::class,
        UserCollectionEntity::class,
        AirEventEntity::class,
        AssistantMessageEntity::class,
        AssistantSessionEntity::class,
    ],
    // v8：助手多会话（assistant_sessions 表 + messages.sessionId 列）。
    // 破坏性升级，不写迁移——旧消息整体丢弃（fallbackToDestructiveMigration 已配置）
    version = 8,
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
}
