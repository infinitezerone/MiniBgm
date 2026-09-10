package com.infinitezerone.minibgm.core.database.di

import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.infinitezerone.minibgm.core.database.BgmDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** v1 → v2：air_schedules 增加事件仲裁列；新增 air_events 逐话事件表 */
private val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf(
                "ALTER TABLE air_schedules ADD COLUMN anilistId INTEGER DEFAULT NULL",
                "ALTER TABLE air_schedules ADD COLUMN broadcastRule TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE air_schedules ADD COLUMN totalEpisodes INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE air_schedules ADD COLUMN source TEXT NOT NULL DEFAULT 'official'",
                "ALTER TABLE air_schedules ADD COLUMN nextEpisode INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE air_schedules ADD COLUMN nextEpisodeAtUtc TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE air_schedules ADD COLUMN nextEpisodeKind TEXT NOT NULL DEFAULT ''",
                "CREATE TABLE IF NOT EXISTS `air_events` (" +
                    "`subjectId` INTEGER NOT NULL, " +
                    "`episode` INTEGER NOT NULL, " +
                    "`airAtUtc` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "PRIMARY KEY(`subjectId`, `episode`, `kind`, `source`))",
            ).forEach { sql ->
                connection.prepare(sql).use { it.step() }
            }
        }
    }

/** v2 → v3：user_collections 瘦身，移除 comment / rate / volStatus 字段，仅保留纯追番状态 */
private val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf(
                "CREATE TABLE IF NOT EXISTS `user_collections_new` (" +
                    "`userId` INTEGER NOT NULL, " +
                    "`subjectId` INTEGER NOT NULL, " +
                    "`subjectType` INTEGER NOT NULL, " +
                    "`type` INTEGER NOT NULL, " +
                    "`epStatus` INTEGER NOT NULL, " +
                    "`updatedAt` TEXT NOT NULL, " +
                    "PRIMARY KEY(`userId`, `subjectId`))",
                "INSERT INTO `user_collections_new` (`userId`, `subjectId`, `subjectType`, `type`, `epStatus`, `updatedAt`) " +
                    "SELECT `userId`, `subjectId`, `subjectType`, `type`, `epStatus`, `updatedAt` FROM `user_collections`",
                "DROP TABLE `user_collections`",
                "ALTER TABLE `user_collections_new` RENAME TO `user_collections`",
            ).forEach { sql ->
                connection.prepare(sql).use { it.step() }
            }
        }
    }

/** v3 → v4：给 episodes.subjectId 与 user_collections.(userId, type) 增加索引，杜绝全表扫描 */
private val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf(
                "CREATE INDEX IF NOT EXISTS `index_episodes_subjectId` ON `episodes` (`subjectId`)",
                "CREATE INDEX IF NOT EXISTS `index_user_collections_userId_type` ON `user_collections` (`userId`, `type`)",
            ).forEach { sql ->
                connection.prepare(sql).use { it.step() }
            }
        }
    }

/**
 * v4 → v5：
 * 1. air_schedules 拆分 beginUtc 为 airDate + beginAtUtc，增加 sortMinutes 杜绝空时间 ASCII 乱序；
 * 2. air_events 收紧主键为 (subjectId, episode, source)；
 * 3. episodes 移除死列 isCollected。
 */
internal val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf(
                // 1. air_schedules 重构
                "CREATE TABLE IF NOT EXISTS `air_schedules_new` (" +
                    "`bgmId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`titleCn` TEXT NOT NULL, " +
                    "`coverUrl` TEXT NOT NULL, " +
                    "`ratingScore` REAL NOT NULL, " +
                    "`airDate` TEXT NOT NULL, " +
                    "`beginAtUtc` TEXT, " +
                    "`sortMinutes` INTEGER NOT NULL, " +
                    "`weekday` INTEGER NOT NULL, " +
                    "`timeCst` TEXT NOT NULL, " +
                    "`timeJst` TEXT NOT NULL, " +
                    "`sitesJson` TEXT NOT NULL, " +
                    "`anilistId` INTEGER, " +
                    "`broadcastRule` TEXT NOT NULL, " +
                    "`totalEpisodes` INTEGER NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`nextEpisode` INTEGER NOT NULL, " +
                    "`nextEpisodeAtUtc` TEXT NOT NULL, " +
                    "`nextEpisodeKind` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`bgmId`))",
                "INSERT INTO `air_schedules_new` (" +
                    "`bgmId`, `title`, `titleCn`, `coverUrl`, `ratingScore`, " +
                    "`airDate`, `beginAtUtc`, `sortMinutes`, `weekday`, `timeCst`, `timeJst`, `sitesJson`, " +
                    "`anilistId`, `broadcastRule`, `totalEpisodes`, `source`, " +
                    "`nextEpisode`, `nextEpisodeAtUtc`, `nextEpisodeKind`, `updatedAt`) " +
                    "SELECT `bgmId`, `title`, `titleCn`, `coverUrl`, `ratingScore`, " +
                    "CASE WHEN `beginUtc` LIKE '%T%' THEN substr(`beginUtc`, 1, 10) ELSE `beginUtc` END, " +
                    "CASE WHEN `beginUtc` LIKE '%T%' THEN `beginUtc` ELSE NULL END, " +
                    "CASE WHEN length(`timeCst`) = 5 AND substr(`timeCst`, 3, 1) = ':' " +
                    "THEN CAST(substr(`timeCst`, 1, 2) AS INTEGER) * 60 + CAST(substr(`timeCst`, 4, 2) AS INTEGER) " +
                    "ELSE 9999 END, " +
                    "`weekday`, `timeCst`, `timeJst`, `sitesJson`, `anilistId`, `broadcastRule`, `totalEpisodes`, `source`, " +
                    "`nextEpisode`, `nextEpisodeAtUtc`, `nextEpisodeKind`, `updatedAt` " +
                    "FROM `air_schedules`",
                "DROP TABLE `air_schedules`",
                "ALTER TABLE `air_schedules_new` RENAME TO `air_schedules`",
                // 2. air_events 收紧主键
                "CREATE TABLE IF NOT EXISTS `air_events_new` (" +
                    "`subjectId` INTEGER NOT NULL, " +
                    "`episode` INTEGER NOT NULL, " +
                    "`airAtUtc` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "PRIMARY KEY(`subjectId`, `episode`, `source`))",
                "INSERT OR REPLACE INTO `air_events_new` (`subjectId`, `episode`, `airAtUtc`, `kind`, `source`) " +
                    "SELECT `subjectId`, `episode`, `airAtUtc`, `kind`, `source` FROM `air_events` " +
                    "ORDER BY CASE `kind` WHEN 'actual' THEN 3 WHEN 'scheduled' THEN 2 WHEN 'predicted' THEN 1 ELSE 0 END ASC",
                "DROP TABLE `air_events`",
                "ALTER TABLE `air_events_new` RENAME TO `air_events`",
                // 3. episodes 移除死列 isCollected 并重建索引
                "CREATE TABLE IF NOT EXISTS `episodes_new` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`subjectId` INTEGER NOT NULL, " +
                    "`sort` REAL NOT NULL, " +
                    "`ep` REAL NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`nameCn` TEXT NOT NULL, " +
                    "`duration` TEXT NOT NULL, " +
                    "`airdate` TEXT NOT NULL, " +
                    "`type` INTEGER NOT NULL, " +
                    "`desc` TEXT NOT NULL, " +
                    "`comment` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
                "INSERT INTO `episodes_new` (" +
                    "`id`, `subjectId`, `sort`, `ep`, `name`, `nameCn`, `duration`, `airdate`, `type`, `desc`, `comment`) " +
                    "SELECT `id`, `subjectId`, `sort`, `ep`, `name`, `nameCn`, `duration`, `airdate`, `type`, `desc`, `comment` " +
                    "FROM `episodes`",
                "DROP TABLE `episodes`",
                "ALTER TABLE `episodes_new` RENAME TO `episodes`",
                "CREATE INDEX IF NOT EXISTS `index_episodes_subjectId` ON `episodes` (`subjectId`)",
            ).forEach { sql ->
                connection.prepare(sql).use { it.step() }
            }
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf(
                // 1. 删除冷数据表 subjects 与 episodes
                "DROP TABLE IF EXISTS `subjects`",
                "DROP TABLE IF EXISTS `episodes`",
                // 2. air_schedules 移除 updatedAt 列并创建复合覆盖索引
                "CREATE TABLE IF NOT EXISTS `air_schedules_new` (" +
                    "`bgmId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`titleCn` TEXT NOT NULL, " +
                    "`coverUrl` TEXT NOT NULL, " +
                    "`ratingScore` REAL NOT NULL, " +
                    "`airDate` TEXT NOT NULL, " +
                    "`beginAtUtc` TEXT, " +
                    "`sortMinutes` INTEGER NOT NULL, " +
                    "`weekday` INTEGER NOT NULL, " +
                    "`timeCst` TEXT NOT NULL, " +
                    "`timeJst` TEXT NOT NULL, " +
                    "`sitesJson` TEXT NOT NULL, " +
                    "`anilistId` INTEGER, " +
                    "`broadcastRule` TEXT NOT NULL, " +
                    "`totalEpisodes` INTEGER NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`nextEpisode` INTEGER NOT NULL, " +
                    "`nextEpisodeAtUtc` TEXT NOT NULL, " +
                    "`nextEpisodeKind` TEXT NOT NULL, " +
                    "PRIMARY KEY(`bgmId`))",
                "INSERT INTO `air_schedules_new` (" +
                    "`bgmId`, `title`, `titleCn`, `coverUrl`, `ratingScore`, " +
                    "`airDate`, `beginAtUtc`, `sortMinutes`, `weekday`, `timeCst`, `timeJst`, `sitesJson`, " +
                    "`anilistId`, `broadcastRule`, `totalEpisodes`, `source`, " +
                    "`nextEpisode`, `nextEpisodeAtUtc`, `nextEpisodeKind`) " +
                    "SELECT `bgmId`, `title`, `titleCn`, `coverUrl`, `ratingScore`, " +
                    "`airDate`, `beginAtUtc`, `sortMinutes`, `weekday`, `timeCst`, `timeJst`, `sitesJson`, " +
                    "`anilistId`, `broadcastRule`, `totalEpisodes`, `source`, " +
                    "`nextEpisode`, `nextEpisodeAtUtc`, `nextEpisodeKind` " +
                    "FROM `air_schedules`",
                "DROP TABLE `air_schedules`",
                "ALTER TABLE `air_schedules_new` RENAME TO `air_schedules`",
                "CREATE INDEX IF NOT EXISTS `index_air_schedules_weekday_sortMinutes_ratingScore` " +
                    "ON `air_schedules` (`weekday`, `sortMinutes`, `ratingScore`)",
                // 3. air_events 增加针对性索引
                "CREATE INDEX IF NOT EXISTS `index_air_events_kind_airAtUtc` ON `air_events` (`kind`, `airAtUtc`)",
                "CREATE INDEX IF NOT EXISTS `index_air_events_airAtUtc` ON `air_events` (`airAtUtc`)",
                // 4. user_collections 升级复合覆盖索引
                "DROP INDEX IF EXISTS `index_user_collections_userId_type`",
                "CREATE INDEX IF NOT EXISTS `index_user_collections_userId_type_updatedAt` " +
                    "ON `user_collections` (`userId`, `type`, `updatedAt`)",
            ).forEach { sql ->
                connection.prepare(sql).use { it.step() }
            }
        }
    }

val databaseModule =
    module {
        single {
            Room
                .databaseBuilder(
                    androidContext(),
                    BgmDatabase::class.java,
                    "minibgm.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
        single { get<BgmDatabase>().airScheduleDao() }
        single { get<BgmDatabase>().userCollectionDao() }
        single { get<BgmDatabase>().airEventDao() }
    }
