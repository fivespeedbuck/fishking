package com.fishking.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FishKingDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FishKingDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesRowsAndBackfillsHistoricalHabitAppearance() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO habits (
                    id, title, color, startDate, endedFromWeek, position, createdAt, updatedAt
                ) VALUES ('habit-1', '刷牙', 123456, 0, NULL, 0, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO habit_versions (
                    id, habitId, effectiveFromWeek, effectiveUntilExclusive,
                    period, targetCount, createdAt
                ) VALUES ('version-1', 'habit-1', 0, NULL, 'DAILY', 2, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO journals (
                    id, entryDate, locationName, latitude, longitude, createdAt, updatedAt
                ) VALUES ('journal-1', 0, NULL, NULL, NULL, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO journal_blocks (
                    id, journalId, position, type, text, textColor, createdAt, updatedAt
                ) VALUES ('block-1', 'journal-1', 0, 'TEXT_LINE', '旧日记', 99, 10, 10)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            FishKingDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT title, color, period, targetCount FROM habit_versions WHERE id = 'version-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("刷牙", cursor.getString(0))
            assertEquals(123456L, cursor.getLong(1))
            assertEquals("DAILY", cursor.getString(2))
            assertEquals(2, cursor.getInt(3))
        }
        migrated.query(
            "SELECT text, textColor, textSize, textStyleSpans FROM journal_blocks WHERE id = 'block-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("旧日记", cursor.getString(0))
            assertEquals(99L, cursor.getLong(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
        }
        migrated.close()
    }

    @Test
    fun migration2To3PreservesJournalBlocksMediaAndAllowsSameDateEntries() {
        val name = "fishking-multi-entry-migration"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO journals VALUES ('old', 20000, '原位置', NULL, NULL, 10, 10)")
            execSQL("INSERT INTO journal_blocks VALUES ('body', 'old', 0, 'TEXT_LINE', '旧正文', 99, 'BODY', NULL, 10, 10)")
            execSQL("INSERT INTO media_assets VALUES ('asset', '/private/old.jpg', NULL, 'image/jpeg', 100, NULL, NULL, NULL, 10)")
            execSQL("INSERT INTO journal_block_media_cross_ref VALUES ('body', 'asset', 0)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 3, true, FishKingDatabase.MIGRATION_2_3)
        migrated.query("SELECT entryTime, locationName FROM journals WHERE id='old'").use {
            it.moveToFirst(); assertNull(it.getString(0)); assertEquals("原位置", it.getString(1))
        }
        migrated.query("SELECT text, textColor FROM journal_blocks WHERE id='body'").use {
            it.moveToFirst(); assertEquals("旧正文", it.getString(0)); assertEquals(99L, it.getLong(1))
        }
        migrated.query("SELECT COUNT(*) FROM journal_block_media_cross_ref WHERE blockId='body' AND assetId='asset'").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
        migrated.execSQL("INSERT INTO journals VALUES ('morning', 20000, NULL, NULL, NULL, 20, 20, '09:00')")
        migrated.execSQL("INSERT INTO journals VALUES ('evening', 20000, NULL, NULL, NULL, 30, 30, '20:00')")
        migrated.query("SELECT COUNT(*) FROM journals WHERE entryDate=20000").use { it.moveToFirst(); assertEquals(3, it.getInt(0)) }
        migrated.close()
    }

    @Test
    fun migration3To4PreservesTodosJournalsHabitsAndMediaWithSafeDefaults() {
        val name = "fishking-plan-soft-delete-migration"
        helper.createDatabase(name, 3).apply {
            execSQL(
                """
                INSERT INTO todo_occurrences (
                    id, seriesId, seriesVersionId, nominalDate, displayDate, title,
                    priority, accentColor, status, completedAt, position,
                    isSeriesException, deletedAt, createdAt, updatedAt
                ) VALUES ('todo-old', NULL, NULL, 20000, 20000, '旧待办',
                    'NORMAL', NULL, 'OPEN', NULL, 0, 0, NULL, 10, 10)
                """.trimIndent(),
            )
            execSQL("INSERT INTO todo_reminders VALUES ('reminder-old', 'todo-old', 2, 540, 0, 1, 10)")
            execSQL("INSERT INTO habits VALUES ('habit-old', '旧习惯', 123456, 20000, NULL, 0, 10, 10)")
            execSQL("INSERT INTO habit_versions VALUES ('habit-version-old', 'habit-old', 19997, NULL, '旧习惯', 123456, 'DAILY', 1, 10)")
            execSQL("INSERT INTO journals VALUES ('journal-old', 20000, '旧位置', NULL, NULL, 10, 10, '09:30')")
            execSQL("INSERT INTO journal_blocks VALUES ('block-old', 'journal-old', 0, 'TEXT_LINE', '旧正文', NULL, 'BODY', NULL, 10, 10)")
            execSQL("INSERT INTO media_assets VALUES ('asset-old', '/private/old.jpg', NULL, 'image/jpeg', 100, 'abc', NULL, NULL, 10)")
            execSQL("INSERT INTO journal_block_media_cross_ref VALUES ('block-old', 'asset-old', 0)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 4, true, FishKingDatabase.MIGRATION_3_4)
        migrated.query("SELECT title, planScope, planDeadline FROM todo_occurrences WHERE id='todo-old'").use {
            it.moveToFirst()
            assertEquals("旧待办", it.getString(0))
            assertEquals("DATE", it.getString(1))
            assertNull(it.getString(2))
        }
        migrated.query("SELECT deletedAt, locationName FROM journals WHERE id='journal-old'").use {
            it.moveToFirst(); assertNull(it.getString(0)); assertEquals("旧位置", it.getString(1))
        }
        migrated.query("SELECT deletedAt, title FROM habits WHERE id='habit-old'").use {
            it.moveToFirst(); assertNull(it.getString(0)); assertEquals("旧习惯", it.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM todo_reminders WHERE occurrenceId='todo-old'").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM journal_block_media_cross_ref WHERE blockId='block-old' AND assetId='asset-old'").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
        migrated.execSQL("UPDATE todo_occurrences SET planScope='MONTH', planDeadline=20030 WHERE id='todo-old'")
        migrated.execSQL("UPDATE journals SET deletedAt=99 WHERE id='journal-old'")
        migrated.execSQL("UPDATE habits SET deletedAt=99 WHERE id='habit-old'")
        migrated.close()
    }

    @Test
    fun migration4To5AddsHabitSchedulesAndRemovesLegacyRecurringTestData() {
        val name = "fishking-habit-schedule-migration"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO habits VALUES ('habit', '跑步 #健康', 123456, 20000, NULL, 0, 10, 10, NULL)")
            execSQL("INSERT INTO habit_versions VALUES ('habit-version', 'habit', 19997, NULL, '跑步 #健康', 123456, 'WEEKLY', 2, 10)")
            execSQL("INSERT INTO journals VALUES ('journal', 20000, NULL, NULL, NULL, 10, 10, '09:00', NULL)")
            execSQL("INSERT INTO todo_occurrences VALUES ('once', NULL, NULL, 20000, 20000, '保留', 'NORMAL', NULL, 'OPEN', NULL, 0, 0, NULL, 10, 10, 'DATE', NULL)")
            execSQL("INSERT INTO todo_series VALUES ('series', NULL, 10, 10)")
            execSQL("INSERT INTO todo_series_versions VALUES ('series-version', 'series', 20000, NULL, '测试循环', 'WEEKLY', 'CLAMP_TO_MONTH_END', 'NORMAL', NULL, 10)")
            execSQL("INSERT INTO todo_occurrences VALUES ('recurring', 'series', 'series-version', 20000, 20000, '测试循环', 'NORMAL', NULL, 'OPEN', NULL, 1024, 0, NULL, 10, 10, 'DATE', NULL)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 5, true, FishKingDatabase.MIGRATION_4_5)
        migrated.query("SELECT scheduleDays FROM habit_versions WHERE id='habit-version'").use {
            it.moveToFirst(); assertEquals("", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM todo_occurrences WHERE id='recurring'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        migrated.query("SELECT COUNT(*) FROM todo_occurrences WHERE id='once'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        migrated.execSQL("INSERT INTO journal_todo_cross_ref VALUES ('journal', 'once')")
        migrated.query("SELECT COUNT(*) FROM journal_todo_cross_ref").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        migrated.close()
    }

    @Test
    fun migration5To6AddsJournalTagsWithoutChangingExistingJournalData() {
        val name = "fishking-journal-tag-migration"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO journals VALUES ('journal', 20000, '宁波市', NULL, NULL, 10, 11, '13:55', NULL)")
            execSQL("INSERT INTO tags VALUES ('tag', '生活', '生活', 12)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 6, true, FishKingDatabase.MIGRATION_5_6)
        migrated.execSQL("PRAGMA foreign_keys=ON")
        migrated.execSQL("INSERT INTO journal_tag_cross_ref VALUES ('journal', 'tag')")
        migrated.query(
            """
            SELECT journals.locationName, tags.name
            FROM journal_tag_cross_ref
            JOIN journals ON journals.id = journal_tag_cross_ref.journalId
            JOIN tags ON tags.id = journal_tag_cross_ref.tagId
            """.trimIndent(),
        ).use {
            it.moveToFirst()
            assertEquals("宁波市", it.getString(0))
            assertEquals("生活", it.getString(1))
        }
        migrated.execSQL("DELETE FROM journals WHERE id='journal'")
        migrated.query("SELECT COUNT(*) FROM journal_tag_cross_ref").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migration6To7PreservesHabitRulesAndMakesLegacyBackfillsNonAnchors() {
        val name = "fishking-habit-n-day-migration"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO habits VALUES ('daily', '刷牙', 1, 20000, NULL, 0, 10, 10, NULL)")
            execSQL("INSERT INTO habits VALUES ('weekly', '锻炼', 2, 20000, NULL, 1, 10, 10, NULL)")
            execSQL("INSERT INTO habits VALUES ('monthly', '洗床单', 3, 20000, NULL, 2, 10, 10, NULL)")
            execSQL("INSERT INTO habit_versions VALUES ('daily-v', 'daily', 20000, NULL, '刷牙', 1, 'DAILY', 2, '', 10)")
            execSQL("INSERT INTO habit_versions VALUES ('weekly-v', 'weekly', 20000, NULL, '锻炼', 2, 'WEEKLY', 4, '1,3,6', 10)")
            execSQL("INSERT INTO habit_versions VALUES ('monthly-v', 'monthly', 20000, NULL, '洗床单', 3, 'MONTHLY', 1, '15', 10)")
            execSQL("INSERT INTO habit_day_records VALUES ('daily', 20001, 1, 0, 11)")
            execSQL("INSERT INTO habit_day_records VALUES ('weekly', 20002, 1, 1, 12)")
            execSQL("INSERT INTO habit_day_records VALUES ('monthly', 20003, 1, 0, 13)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 7, true, FishKingDatabase.MIGRATION_6_7)
        migrated.query("SELECT period, targetCount, scheduleDays, intervalDays, scheduleStartDate FROM habit_versions ORDER BY habitId").use {
            assertEquals(3, it.count)
            while (it.moveToNext()) {
                assertTrue(it.getString(0) in setOf("DAILY", "WEEKLY", "MONTHLY"))
                assertTrue(it.getInt(1) > 0)
                assertEquals(1, it.getInt(3))
                assertEquals(20000L, it.getLong(4))
            }
        }
        migrated.query("SELECT habitId, isBackfilled, affectsScheduleAnchor FROM habit_day_records ORDER BY habitId").use {
            it.moveToFirst(); assertEquals("daily", it.getString(0)); assertEquals(1, it.getInt(2))
            it.moveToNext(); assertEquals("monthly", it.getString(0)); assertEquals(1, it.getInt(2))
            it.moveToNext(); assertEquals("weekly", it.getString(0)); assertEquals(1, it.getInt(1)); assertEquals(0, it.getInt(2))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "fishking-migration-test"
    }
}
