package com.infinitezerone.minibgm.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.infinitezerone.minibgm.core.database.di.MIGRATION_5_6
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Migration5To6Test {
    @Test
    fun migration5To6_executesExpectedMigrationSqlsInOrder() =
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

            MIGRATION_5_6.migrate(fakeConnection)

            assertEquals(5, MIGRATION_5_6.startVersion)
            assertEquals(6, MIGRATION_5_6.endVersion)

            // 验证执行了 11 条 SQL (删除冷表、重构 air_schedules 移除 updatedAt、各表创建复合覆盖索引)
            assertEquals(11, executedSqls.size)

            // 1. 删除冷表
            assertEquals("DROP TABLE IF EXISTS `subjects`", executedSqls[0])
            assertEquals("DROP TABLE IF EXISTS `episodes`", executedSqls[1])

            // 2. air_schedules 重构与覆盖索引
            assertTrue(executedSqls[2].startsWith("CREATE TABLE IF NOT EXISTS `air_schedules_new`"))
            assertFalse(executedSqls[2].contains("`updatedAt`"))
            assertTrue(executedSqls[3].startsWith("INSERT INTO `air_schedules_new`"))
            assertFalse(executedSqls[3].contains("`updatedAt`"))
            assertEquals("DROP TABLE `air_schedules`", executedSqls[4])
            assertEquals("ALTER TABLE `air_schedules_new` RENAME TO `air_schedules`", executedSqls[5])
            assertEquals(
                "CREATE INDEX IF NOT EXISTS `index_air_schedules_weekday_sortMinutes_ratingScore` ON `air_schedules` (`weekday`, `sortMinutes`, `ratingScore`)",
                executedSqls[6],
            )

            // 3. air_events 复合索引
            assertEquals(
                "CREATE INDEX IF NOT EXISTS `index_air_events_kind_airAtUtc` ON `air_events` (`kind`, `airAtUtc`)",
                executedSqls[7],
            )
            assertEquals(
                "CREATE INDEX IF NOT EXISTS `index_air_events_airAtUtc` ON `air_events` (`airAtUtc`)",
                executedSqls[8],
            )

            // 4. user_collections 覆盖索引
            assertEquals("DROP INDEX IF EXISTS `index_user_collections_userId_type`", executedSqls[9])
            assertEquals(
                "CREATE INDEX IF NOT EXISTS `index_user_collections_userId_type_updatedAt` ON `user_collections` (`userId`, `type`, `updatedAt`)",
                executedSqls[10],
            )
        }
}
