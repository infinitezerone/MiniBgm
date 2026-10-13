package com.infinitezerone.minibgm.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.dao.AssistantSessionDao
import com.infinitezerone.minibgm.core.database.dao.DanmakuOverrideDao
import com.infinitezerone.minibgm.core.database.dao.TorrentTaskDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.database.entity.DanmakuEpisodeOverrideEntity
import com.infinitezerone.minibgm.core.database.entity.TorrentTaskEntity
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
        DanmakuEpisodeOverrideEntity::class,
        TorrentTaskEntity::class,
    ],
    // v11：磁力/BT 下载任务本地持久化表（TorrentTaskEntity）。
    // v10：弹幕分集用户纠正覆盖表。不写迁移——破坏性回退（fallbackToDestructiveMigration）整体清空重建。
    // v9：AniList↔bgm 映射缓存 + bangumi-data 月切片 ETag。
    // v8：助手多会话（assistant_sessions 表 + messages.sessionId 列）。
    version = 11,
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

    abstract fun danmakuOverrideDao(): DanmakuOverrideDao

    abstract fun torrentTaskDao(): TorrentTaskDao
}
