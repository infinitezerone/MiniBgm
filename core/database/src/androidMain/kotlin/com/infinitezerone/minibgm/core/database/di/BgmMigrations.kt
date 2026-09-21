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
