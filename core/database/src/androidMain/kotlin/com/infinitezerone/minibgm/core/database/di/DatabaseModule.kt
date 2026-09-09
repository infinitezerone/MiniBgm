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

val databaseModule =
    module {
        single {
            Room
                .databaseBuilder(
                    androidContext(),
                    BgmDatabase::class.java,
                    "minibgm.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
        single { get<BgmDatabase>().subjectDao() }
        single { get<BgmDatabase>().airScheduleDao() }
        single { get<BgmDatabase>().episodeDao() }
        single { get<BgmDatabase>().userCollectionDao() }
        single { get<BgmDatabase>().airEventDao() }
    }
