package com.fishking.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class HabitPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class Habit(
    val id: String,
    val title: String,
    val color: Long,
    val startDate: LocalDate,
    val endedFromWeek: LocalDate? = null,
    val position: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class HabitVersion(
    val id: String,
    val habitId: String,
    val effectiveFromWeek: LocalDate,
    val effectiveUntilExclusive: LocalDate? = null,
    /** The user-facing identity is versioned so historical weeks never change after an edit. */
    val title: String,
    val color: Long,
    val period: HabitPeriod,
    val targetCount: Int,
    /** ISO weekday numbers for WEEKLY, month-day numbers for MONTHLY. Empty keeps legacy flexible rules. */
    val scheduleDays: Set<Int> = emptySet(),
    val createdAt: Instant,
) {
    init {
        require(targetCount > 0) { "Habit targetCount must be positive" }
    }
}

data class HabitDayRecord(
    val habitId: String,
    val date: LocalDate,
    val count: Int,
    val isBackfilled: Boolean,
    val updatedAt: Instant,
) {
    init {
        require(count > 0) { "Persisted habit count must be positive" }
    }
}

data class HabitWeekSkip(
    val habitId: String,
    val weekStart: LocalDate,
    val createdAt: Instant,
)

data class HabitWeekItem(
    val id: String,
    val title: String,
    val color: Long,
    val startDate: LocalDate,
    val position: Long,
    val weekStart: LocalDate,
    val versionId: String,
    val period: HabitPeriod,
    val targetCount: Int,
    val scheduleDays: Set<Int> = emptySet(),
    val isSkipped: Boolean,
    val records: List<HabitDayRecord>,
) {
    fun countOn(date: LocalDate): Int = records.firstOrNull { it.date == date }?.count ?: 0

    val weeklyEffectiveDayCount: Int
        get() = HabitRules.weeklyEffectiveDayCount(records, weekStart)

    fun effectiveCountFor(date: LocalDate): Int = when (period) {
        HabitPeriod.DAILY -> countOn(date)
        HabitPeriod.WEEKLY -> HabitRules.weeklyEffectiveDayCount(records, HabitRules.weekStart(date))
        HabitPeriod.MONTHLY -> records.asSequence()
            .filter { it.count > 0 && YearMonth.from(it.date) == YearMonth.from(date) }
            .map(HabitDayRecord::date)
            .distinct()
            .count()
    }

    fun isScheduledOn(date: LocalDate): Boolean = when (period) {
        HabitPeriod.DAILY -> true
        HabitPeriod.WEEKLY -> scheduleDays.isEmpty() || date.dayOfWeek.value in scheduleDays
        HabitPeriod.MONTHLY -> scheduleDays.isEmpty() || date.dayOfMonth in scheduleDays
    }
}

data class HabitWeekSnapshot(
    val weekStart: LocalDate,
    val items: List<HabitWeekItem>,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}
