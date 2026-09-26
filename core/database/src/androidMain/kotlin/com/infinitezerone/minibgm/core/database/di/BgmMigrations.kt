package com.infinitezerone.minibgm.core.database.di

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/** v7 新增表：追番助手会话消息 */
internal const val ASSISTANT_MESSAGES_TABLE_DDL: String =
    "CREATE TABLE IF NOT EXISTS `assistant_messages` " +
        "(`id` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
        "`timestamp` INTEGER NOT NULL, `pendingActionsJson` TEXT, `playableSourcesJson` TEXT, " +
        "`isError` INTEGER NOT NULL, PRIMARY KEY(`id`))"

internal const val ASSISTANT_MESSAGES_TIMESTAMP_INDEX_DDL: String =
    "CREATE INDEX IF NOT EXISTS `index_assistant_messages_timestamp` " +
        "ON `assistant_messages` (`timestamp`)"

/**
 * v6 → v7：新增追番助手会话表。
 *
 * DDL 必须与 Room 导出的 `schemas/…BgmDatabase/7.json` 逐字一致，漂移由
 * [AssistantMessagesMigrationSchemaTest] 在构建期拦住——真机上一旦不一致，
 * Room 打开数据库时就会因 identity 校验失败而崩溃。
 */
internal val MIGRATION_6_7 =
    Migration(6, 7) { connection ->
        connection.execSQL(ASSISTANT_MESSAGES_TABLE_DDL)
        connection.execSQL(ASSISTANT_MESSAGES_TIMESTAMP_INDEX_DDL)
    }

/** v9 新增表：AniList↔bgm 映射缓存 */
internal const val ANILIST_BGM_MAPPING_TABLE_DDL: String =
    "CREATE TABLE IF NOT EXISTS `anilist_bgm_mapping` " +
        "(`anilistId` INTEGER NOT NULL, `bgmId` INTEGER NOT NULL, `sitesJson` TEXT NOT NULL, " +
        "`title` TEXT NOT NULL, `titleCn` TEXT NOT NULL, `beginIso` TEXT NOT NULL, `endIso` TEXT NOT NULL, " +
        "`monthKey` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`anilistId`))"

/** v9 新增表：bangumi-data 月切片 ETag 缓存 */
internal const val BANGUMI_DATA_MONTH_ETAG_TABLE_DDL: String =
    "CREATE TABLE IF NOT EXISTS `bangumi_data_month_etag` " +
        "(`monthKey` TEXT NOT NULL, `etag` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`monthKey`))"

/**
 * v8 → v9：新增映射与月切片 ETag 两张缓存表。
 * 只新增、不改既有列，不丢用户收藏 / 会话数据。
 *
 * DDL 必须与 Room 导出的 `schemas/…BgmDatabase/9.json` 逐字一致。
 */
internal val MIGRATION_8_9 =
    Migration(8, 9) { connection ->
        connection.execSQL(ANILIST_BGM_MAPPING_TABLE_DDL)
        connection.execSQL(BANGUMI_DATA_MONTH_ETAG_TABLE_DDL)
    }
