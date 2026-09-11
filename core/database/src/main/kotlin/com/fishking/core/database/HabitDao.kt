package com.fishking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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
          AND effectiveFromWeek <= :date
          AND (effectiveUntilExclusive IS NULL OR effectiveUntilExclusive > :date)
        ORDER BY effectiveFromWeek DESC LIMIT 1
        """,
    )
    suspend fun versionFor(habitId: String, date: LocalDate): HabitVersionEntity?

    @Query("SELECT * FROM habit_versions WHERE habitId = :habitId ORDER BY effectiveFromWeek ASC LIMIT 1")
    suspend fun firstVersion(habitId: String): HabitVersionEntity?

    @Query("SELECT * FROM habit_versions WHERE habitId = :habitId AND effectiveFromWeek > :date ORDER BY effectiveFromWeek ASC")
    suspend fun versionsAfter(habitId: String, date: LocalDate): List<HabitVersionEntity>

    @Query("DELETE FROM habit_versions WHERE habitId = :habitId AND effectiveFromWeek > :date")
    suspend fun deleteVersionsAfter(habitId: String, date: LocalDate): Int

    @Query("UPDATE habit_versions SET color = :color WHERE habitId = :habitId")
    suspend fun recolorAllVersions(habitId: String, color: Long)

    @Query("SELECT * FROM habit_day_records WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun dayRecord(habitId: String, date: LocalDate): HabitDayRecordEntity?

    @Query("SELECT * FROM habit_day_records WHERE habitId = :habitId ORDER BY date ASC")
    suspend fun recordsForHabit(habitId: String): List<HabitDayRecordEntity>

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
