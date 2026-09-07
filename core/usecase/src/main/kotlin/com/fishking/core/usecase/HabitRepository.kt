package com.fishking.core.usecase

import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitWeekSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitRepository {
    suspend fun deleteHabit(id: String) = Unit
    fun observeWeek(weekStart: LocalDate): Flow<List<HabitWeekItem>>

    /** Oldest real habit week first and current week last, ready for a bottom-anchored timeline. */
    fun observeTimeline(currentDate: LocalDate): Flow<List<HabitWeekSnapshot>>

    /** Includes earlier empty weeks for explicit backfill, without creating any records. */
    fun observeTimelineFrom(currentDate: LocalDate, fromWeek: LocalDate): Flow<List<HabitWeekSnapshot>> = observeTimeline(currentDate)

    fun observeTimelineRange(currentDate: LocalDate, fromWeek: LocalDate, throughWeek: LocalDate): Flow<List<HabitWeekSnapshot>> = observeTimelineFrom(currentDate, fromWeek)

    suspend fun createHabit(
        title: String,
        color: Long,
        startDate: LocalDate,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int> = emptySet(),
    ): String

    suspend fun updateHabit(
        habitId: String,
        effectiveFromWeek: LocalDate,
        title: String,
        color: Long,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int> = emptySet(),
    )

    /** Returns the new count, 0 when the persisted day record was removed, or null when inactive. */
    suspend fun toggleCheckIn(habitId: String, date: LocalDate): Int?

    /** Returns the new skipped state, or null when the habit is inactive for that week. */
    suspend fun toggleWeekSkip(habitId: String, weekStart: LocalDate): Boolean?

    suspend fun endHabitFromWeek(habitId: String, weekStart: LocalDate)

    /** Persists the visible habit order used by the current and historical week panels. */
    suspend fun reorderHabits(orderedIds: List<String>) = Unit
}
