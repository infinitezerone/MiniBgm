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

val databaseModule =
    module {
        single {
            Room
                .databaseBuilder(
                    androidContext(),
                    BgmDatabase::class.java,
                    "minibgm.db",
                ).addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
        single { get<BgmDatabase>().subjectDao() }
        single { get<BgmDatabase>().airScheduleDao() }
        single { get<BgmDatabase>().episodeDao() }
        single { get<BgmDatabase>().userCollectionDao() }
        single { get<BgmDatabase>().airEventDao() }
    }
