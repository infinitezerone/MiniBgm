package com.infinitezerone.minibgm.core.database.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * v8 → v9 迁移的防漂移门禁：把 [MIGRATION_8_9] 的手写 DDL 与 Room 导出的
 * `schemas/…BgmDatabase/9.json` 逐字对齐。
 *
 * 这两张表只新增、不改既有列，一旦 DDL 与 schema 分叉，真机升级时会因 identity 校验失败而崩。
 */
class AniListMappingMigrationSchemaTest {
    private val exportedCreateSql: List<String> by lazy {
        val schema =
            listOf(
                File("schemas/$DATABASE_FQN/$SCHEMA_VERSION.json"),
                File("core/database/schemas/$DATABASE_FQN/$SCHEMA_VERSION.json"),
            ).firstOrNull(File::exists) ?: error("找不到导出的 schema：$DATABASE_FQN/$SCHEMA_VERSION.json")

        CREATE_SQL_REGEX
            .findAll(schema.readText())
            .map { it.groupValues[1] }
            .toList()
    }

    @Test
    fun `AniList 映射表 DDL 与导出 schema 完全一致`() {
        assertContainsExactly(ANILIST_BGM_MAPPING_TABLE_NAME, ANILIST_BGM_MAPPING_TABLE_DDL)
    }

    @Test
    fun `bangumi-data 月 ETag 表 DDL 与导出 schema 完全一致`() {
        assertContainsExactly(BANGUMI_DATA_MONTH_ETAG_TABLE_NAME, BANGUMI_DATA_MONTH_ETAG_TABLE_DDL)
    }

    private fun assertContainsExactly(
        tableName: String,
        expected: String,
    ) {
        assertTrue(
            exportedCreateSql.any { it.replace("\${TABLE_NAME}", tableName) == expected },
            "Migration DDL 与 schema/$SCHEMA_VERSION.json 不一致，请同步 $tableName 的建表语句：\n" +
                "migration: $expected\n",
        )
    }

    private companion object {
        const val DATABASE_FQN = "com.infinitezerone.minibgm.core.database.BgmDatabase"
        const val SCHEMA_VERSION = 9
        const val ANILIST_BGM_MAPPING_TABLE_NAME = "anilist_bgm_mapping"
        const val BANGUMI_DATA_MONTH_ETAG_TABLE_NAME = "bangumi_data_month_etag"
        val CREATE_SQL_REGEX = Regex(""""createSql":\s*"((?:[^"\\]|\\.)*)"""")
    }
}
