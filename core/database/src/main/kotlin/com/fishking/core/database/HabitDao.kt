package com.fishking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class HabitForWeekRow(
    val id: String,
    val title: String,
    val color: Long,
    val startDate: LocalDate,
    val endedFromWeek: LocalDate?,
    val position: Long,
    val versionId: String,
    val period: String,
    val targetCount: Int,
    val scheduleDays: String,
    val effectiveFromWeek: LocalDate,
    val isSkipped: Boolean,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY position ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_versions ORDER BY habitId, effectiveFromWeek ASC")
    fun observeAllVersions(): Flow<List<HabitVersionEntity>>

    @Query("SELECT * FROM habit_day_records ORDER BY date ASC")
    fun observeAllRecords(): Flow<List<HabitDayRecordEntity>>

    @Query("SELECT * FROM habit_week_skips ORDER BY weekStart ASC")
    fun observeAllSkips(): Flow<List<HabitWeekSkipEntity>>

    @Query(
        """
        SELECT h.id, v.title AS title, v.color AS color, h.startDate, h.endedFromWeek, h.position,
               v.id AS versionId, v.period, v.targetCount, v.scheduleDays, v.effectiveFromWeek,
               EXISTS(
                   SELECT 1 FROM habit_week_skips s
                   WHERE s.habitId = h.id AND s.weekStart = :weekStart
               ) AS isSkipped
        FROM habits h
        JOIN habit_versions v ON v.habitId = h.id
        WHERE h.deletedAt IS NULL AND h.startDate <= :weekEnd
          AND (h.endedFromWeek IS NULL OR h.endedFromWeek > :weekStart)
          AND v.effectiveFromWeek = (
              SELECT MAX(v2.effectiveFromWeek)
              FROM habit_versions v2
              WHERE v2.habitId = h.id
                AND v2.effectiveFromWeek <= :weekStart
                AND (v2.effectiveUntilExclusive IS NULL OR v2.effectiveUntilExclusive > :weekStart)
          )
        ORDER BY h.position ASC
        """,
    )
    fun observeForWeek(weekStart: LocalDate, weekEnd: LocalDate): Flow<List<HabitForWeekRow>>

    @Query(
        """
        SELECT * FROM habit_day_records
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC
        """,
    )
    fun observeRecords(startDate: LocalDate, endDate: LocalDate): Flow<List<HabitDayRecordEntity>>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun findHabit(id: String): HabitEntity?

    @Query("SELECT MAX(position) FROM habits")
    suspend fun maximumPosition(): Long?

    @Query("UPDATE habits SET position = :position, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun updatePosition(id: String, position: Long, updatedAt: java.time.Instant): Int

    @Query(
        """
        SELECT * FROM habit_versions
        WHERE habitId = :habitId
          AND effectiveFromWeek <= :weekStart
          AND (effectiveUntilExclusive IS NULL OR effectiveUntilExclusive > :weekStart)
        ORDER BY effectiveFromWeek DESC LIMIT 1
        """,
    )
    suspend fun versionFor(habitId: String, weekStart: LocalDate): HabitVersionEntity?

    @Query("SELECT * FROM habit_versions WHERE habitId = :habitId ORDER BY effectiveFromWeek ASC LIMIT 1")
    suspend fun firstVersion(habitId: String): HabitVersionEntity?

    @Query("SELECT * FROM habit_day_records WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun dayRecord(habitId: String, date: LocalDate): HabitDayRecordEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Insert
    suspend fun insertVersion(version: HabitVersionEntity)

    @Update
    suspend fun updateVersion(version: HabitVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayRecord(record: HabitDayRecordEntity)

    @Query("DELETE FROM habit_day_records WHERE habitId = :habitId AND date = :date")
    suspend fun deleteDayRecord(habitId: String, date: LocalDate): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun skipWeek(skip: HabitWeekSkipEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM habit_week_skips WHERE habitId = :habitId AND weekStart = :weekStart)")
    suspend fun isWeekSkipped(habitId: String, weekStart: LocalDate): Boolean

    @Query("DELETE FROM habit_week_skips WHERE habitId = :habitId AND weekStart = :weekStart")
    suspend fun restoreWeek(habitId: String, weekStart: LocalDate): Int
}
