package com.infinitezerone.minibgm.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.dao.AssistantSessionDao
import com.infinitezerone.minibgm.core.database.dao.DanmakuOverrideDao
import com.infinitezerone.minibgm.core.database.dao.PlaybackHistoryDao
import com.infinitezerone.minibgm.core.database.dao.TorrentTaskDao
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.database.entity.DanmakuEpisodeOverrideEntity
import com.infinitezerone.minibgm.core.database.entity.PlaybackHistoryEntity
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
        PlaybackHistoryEntity::class,
    ],
    // 开发测试阶段刻意不做迁移升级：表结构随迭代频繁变动，维护手写 Migration 的收益为负。
    // 版本号已归零回初始值，schema 变更时直接 bump 该数字，由 fallbackToDestructiveMigration 整体重建。
    //
    // 两条硬约束：
    // 1. 任何实体 / DAO 变更**必须**同时把 version 加一。Room 在版本号未变而结构已变时
    //    会抛 "Room cannot verify the data integrity"，fallbackToDestructiveMigration 不兜这种情况。
    // 2. exportSchema 已随之关闭——没有迁移就没有 schema 快照的用途。正式发布需要保全用户数据时，
    //    先重新打开 exportSchema 从当前结构导出基线，再逐个补 Migration。
    version = 2,
    exportSchema = false,
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

    abstract fun playbackHistoryDao(): PlaybackHistoryDao
}
