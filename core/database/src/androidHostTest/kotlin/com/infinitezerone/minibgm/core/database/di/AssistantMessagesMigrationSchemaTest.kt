package com.infinitezerone.minibgm.core.database.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 手写 Migration 的防漂移门禁：把 DDL 与 Room 导出的 schema JSON 逐字对齐。
 *
 * 实体列改动会重新导出 schema，但不会改动 [MIGRATION_6_7] 里的常量，二者一旦分叉，
 * 真机上表现为升级即崩（Room identity 校验失败）。这里把它变成构建期失败。
 */
class AssistantMessagesMigrationSchemaTest {
    private val exportedCreateSql: List<String> by lazy {
        val schema =
            listOf(
                File("schemas/$DATABASE_FQN/$SCHEMA_VERSION.json"),
                File("core/database/schemas/$DATABASE_FQN/$SCHEMA_VERSION.json"),
            ).firstOrNull(File::exists) ?: error("找不到导出的 schema：$DATABASE_FQN/$SCHEMA_VERSION.json")

        CREATE_SQL_REGEX
            .findAll(schema.readText())
            .map { it.groupValues[1].replace("\${TABLE_NAME}", TABLE_NAME) }
            .toList()
    }

    @Test
    fun `表 DDL 与导出 schema 完全一致`() {
        assertContainsExactly(ASSISTANT_MESSAGES_TABLE_DDL)
    }

    @Test
    fun `索引 DDL 与导出 schema 完全一致`() {
        assertContainsExactly(ASSISTANT_MESSAGES_TIMESTAMP_INDEX_DDL)
    }

    private fun assertContainsExactly(expected: String) {
        assertTrue(
            exportedCreateSql.any { it == expected },
            "Migration DDL 与 schema/$SCHEMA_VERSION.json 不一致，请同步 $TABLE_NAME 的建表语句：\n" +
                "migration: $expected\n",
        )
    }

    private companion object {
        const val DATABASE_FQN = "com.infinitezerone.minibgm.core.database.BgmDatabase"
        const val SCHEMA_VERSION = 7
        const val TABLE_NAME = "assistant_messages"
        val CREATE_SQL_REGEX = Regex(""""createSql":\s*"((?:[^"\\]|\\.)*)"""")
    }
}
