package com.infinitezerone.minibgm.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.infinitezerone.minibgm.core.database.di.MIGRATION_4_5
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Migration4To5Test {
    @Test
    fun migration4To5_executesExpectedMigrationSqlsInOrder() =
        runTest {
            val executedSqls = mutableListOf<String>()

            val fakeStatement =
                object : SQLiteStatement {
                    override fun bindBlob(
                        index: Int,
                        value: ByteArray,
                    ) {}

                    override fun bindDouble(
                        index: Int,
                        value: Double,
                    ) {}

                    override fun bindLong(
                        index: Int,
                        value: Long,
                    ) {}

                    override fun bindNull(index: Int) {}

                    override fun bindText(
                        index: Int,
                        value: String,
                    ) {}

                    override fun clearBindings() {}

                    override fun close() {}

                    override fun getBlob(index: Int): ByteArray = ByteArray(0)

                    override fun getColumnCount(): Int = 0

                    override fun getColumnName(index: Int): String = ""

                    override fun getColumnType(index: Int): Int = 0

                    override fun getDouble(index: Int): Double = 0.0

                    override fun getLong(index: Int): Long = 0L

                    override fun getText(index: Int): String = ""

                    override fun isNull(index: Int): Boolean = false

                    override fun reset() {}

                    override fun step(): Boolean = false
                }

            val fakeConnection =
                object : SQLiteConnection {
                    override fun prepare(sql: String): SQLiteStatement {
                        executedSqls += sql
                        return fakeStatement
                    }

                    override fun close() {}
                }

            MIGRATION_4_5.migrate(fakeConnection)

            assertEquals(4, MIGRATION_4_5.startVersion)
            assertEquals(5, MIGRATION_4_5.endVersion)

            // 验证执行了 13 条 SQL (3 张表的重构与索引重建)
            assertEquals(13, executedSqls.size)

            // 1. air_schedules
            assertTrue(executedSqls[0].startsWith("CREATE TABLE IF NOT EXISTS `air_schedules_new`"))
            assertTrue(executedSqls[0].contains("`airDate` TEXT NOT NULL"))
            assertTrue(executedSqls[0].contains("`beginAtUtc` TEXT"))
            assertTrue(executedSqls[0].contains("`sortMinutes` INTEGER NOT NULL"))
            assertTrue(executedSqls[1].startsWith("INSERT INTO `air_schedules_new`"))
            assertTrue(executedSqls[1].contains("CASE WHEN length(`timeCst`) = 5"))
            assertEquals("DROP TABLE `air_schedules`", executedSqls[2])
            assertEquals("ALTER TABLE `air_schedules_new` RENAME TO `air_schedules`", executedSqls[3])

            // 2. air_events (收紧主键)
            assertTrue(executedSqls[4].startsWith("CREATE TABLE IF NOT EXISTS `air_events_new`"))
            assertTrue(executedSqls[4].contains("PRIMARY KEY(`subjectId`, `episode`, `source`)"))
            assertTrue(executedSqls[5].startsWith("INSERT OR REPLACE INTO `air_events_new`"))
            assertTrue(executedSqls[5].contains("ORDER BY CASE `kind` WHEN 'actual' THEN 3"))
            assertEquals("DROP TABLE `air_events`", executedSqls[6])
            assertEquals("ALTER TABLE `air_events_new` RENAME TO `air_events`", executedSqls[7])

            // 3. episodes (移除死列 isCollected)
            assertTrue(executedSqls[8].startsWith("CREATE TABLE IF NOT EXISTS `episodes_new`"))
            assertFalse(executedSqls[8].contains("isCollected"))
            assertTrue(executedSqls[9].startsWith("INSERT INTO `episodes_new`"))
            assertFalse(executedSqls[9].contains("isCollected"))
            assertEquals("DROP TABLE `episodes`", executedSqls[10])
            assertEquals("ALTER TABLE `episodes_new` RENAME TO `episodes`", executedSqls[11])
            assertEquals("CREATE INDEX IF NOT EXISTS `index_episodes_subjectId` ON `episodes` (`subjectId`)", executedSqls[12])
        }
}
