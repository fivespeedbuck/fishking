package com.fishking.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TodoSeriesEntity::class,
        TodoSeriesVersionEntity::class,
        TodoOccurrenceEntity::class,
        TodoReminderEntity::class,
        TodoSeriesReminderEntity::class,
        TagEntity::class,
        TodoTagCrossRef::class,
        HabitEntity::class,
        HabitVersionEntity::class,
        HabitDayRecordEntity::class,
        HabitWeekSkipEntity::class,
        LifeGoalEntity::class,
        LifeGoalEventEntity::class,
        TodoLifeGoalCrossRef::class,
        LifeGoalTagCrossRef::class,
        JournalEntity::class,
        JournalBlockEntity::class,
        MediaAssetEntity::class,
        JournalBlockMediaCrossRef::class,
        JournalLifeGoalCrossRef::class,
        JournalTodoCrossRef::class,
        JournalTagCrossRef::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class FishKingDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun habitDao(): HabitDao
    abstract fun journalDao(): JournalDao
    abstract fun lifeGoalDao(): LifeGoalDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "fishking.db"

        @JvmField
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_habit_versions` (
                        `id` TEXT NOT NULL,
                        `habitId` TEXT NOT NULL,
                        `effectiveFromWeek` INTEGER NOT NULL,
                        `effectiveUntilExclusive` INTEGER,
                        `title` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `period` TEXT NOT NULL,
                        `targetCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO `_new_habit_versions` (
                        `id`, `habitId`, `effectiveFromWeek`, `effectiveUntilExclusive`,
                        `title`, `color`, `period`, `targetCount`, `createdAt`
                    )
                    SELECT version.`id`, version.`habitId`, version.`effectiveFromWeek`,
                           version.`effectiveUntilExclusive`, habit.`title`, habit.`color`,
                           version.`period`, version.`targetCount`, version.`createdAt`
                    FROM `habit_versions` AS version
                    JOIN `habits` AS habit ON habit.`id` = version.`habitId`
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE `habit_versions`")
                database.execSQL("ALTER TABLE `_new_habit_versions` RENAME TO `habit_versions`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_habit_versions_habitId` " +
                        "ON `habit_versions` (`habitId`)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_habit_versions_habitId_effectiveFromWeek` " +
                        "ON `habit_versions` (`habitId`, `effectiveFromWeek`)",
                )
                database.execSQL("ALTER TABLE `journal_blocks` ADD COLUMN `textSize` TEXT")
                database.execSQL("ALTER TABLE `journal_blocks` ADD COLUMN `textStyleSpans` TEXT")
            }
        }

        @JvmField
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS index_journals_entryDate")
                database.execSQL("CREATE INDEX index_journals_entryDate ON journals(entryDate)")
                database.execSQL("ALTER TABLE journals ADD COLUMN entryTime TEXT")
            }
        }

        @JvmField
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo_occurrences ADD COLUMN planScope TEXT NOT NULL DEFAULT 'DATE'")
                database.execSQL("ALTER TABLE todo_occurrences ADD COLUMN planDeadline INTEGER")
                database.execSQL("ALTER TABLE journals ADD COLUMN deletedAt INTEGER")
                database.execSQL("ALTER TABLE habits ADD COLUMN deletedAt INTEGER")
            }
        }

        @JvmField
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE habit_versions ADD COLUMN scheduleDays TEXT NOT NULL DEFAULT ''")
                // The user confirmed all legacy recurring todos are test data. Remove the old
                // series facts so the retired feature cannot keep projecting ghost occurrences.
                database.execSQL("DELETE FROM todo_occurrences WHERE seriesId IS NOT NULL")
                database.execSQL("DELETE FROM todo_series")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_todo_cross_ref (
                        journalId TEXT NOT NULL,
                        todoOccurrenceId TEXT NOT NULL,
                        PRIMARY KEY(journalId, todoOccurrenceId),
                        FOREIGN KEY(journalId) REFERENCES journals(id) ON DELETE CASCADE,
                        FOREIGN KEY(todoOccurrenceId) REFERENCES todo_occurrences(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_todo_cross_ref_todoOccurrenceId ON journal_todo_cross_ref(todoOccurrenceId)")
            }
        }

        @JvmField
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_tag_cross_ref (
                        journalId TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(journalId, tagId),
                        FOREIGN KEY(journalId) REFERENCES journals(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_journal_tag_cross_ref_tagId " +
                        "ON journal_tag_cross_ref(tagId)",
                )
            }
        }

        fun create(context: Context): FishKingDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FishKingDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
                .build()
    }
}
