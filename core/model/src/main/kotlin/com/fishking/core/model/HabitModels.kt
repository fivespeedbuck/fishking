package com.fishking.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class HabitPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    /** A fixed calendar cadence whose slots never move after a completion. */
    EVERY_N_DAYS,
    /** A cadence re-anchored by the latest completion the user chose to count. */
    AFTER_COMPLETION_N_DAYS,
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
    /**
     * This is deliberately a natural date, not a week boundary.  The underlying legacy
     * SQLite column remains named `effectiveFromWeek` so existing installations migrate
     * without a table rebuild.
     */
    val effectiveFromDate: LocalDate,
    val effectiveUntilExclusive: LocalDate? = null,
    /** Title follows dated versions. Colour is a global habit identity, synchronized across versions on recolouring. */
    val title: String,
    val color: Long,
    val period: HabitPeriod,
    val targetCount: Int,
    /** ISO weekday numbers for WEEKLY, month-day numbers for MONTHLY. Empty keeps legacy flexible rules. */
    val scheduleDays: Set<Int> = emptySet(),
    /** Positive only for the two N-day modes. */
    val intervalDays: Int = 1,
    /** Fixed cadence origin, or the first due date for completion-anchored cadence. */
    val scheduleStartDate: LocalDate,
    val createdAt: Instant,
) {
    init {
        require(targetCount > 0) { "Habit targetCount must be positive" }
        require(intervalDays > 0) { "Habit intervalDays must be positive" }
        if (period == HabitPeriod.EVERY_N_DAYS || period == HabitPeriod.AFTER_COMPLETION_N_DAYS) {
            require(targetCount == 1) { "N-day habits have a single completion target" }
            require(scheduleDays.isEmpty()) { "N-day habits cannot also select schedule days" }
        }
    }
}

data class HabitDayRecord(
    val habitId: String,
    val date: LocalDate,
    val count: Int,
    val isBackfilled: Boolean,
    val updatedAt: Instant,
    /** Legacy persistence flag; dynamic cadence now follows the latest real completion. */
    val affectsScheduleAnchor: Boolean = !isBackfilled,
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
    val intervalDays: Int = 1,
    val scheduleStartDate: LocalDate = startDate,
    val isSkipped: Boolean,
    val records: List<HabitDayRecord>,
    /** All versions intersecting this week. Existing callers can keep using the summary fields. */
    val versions: List<HabitVersion> = emptyList(),
) {
    fun countOn(date: LocalDate): Int = records.firstOrNull { it.date == date }?.count ?: 0

    val weeklyEffectiveDayCount: Int get() = effectiveCountFor(weekStart.plusDays(6))

    fun ruleOn(date: LocalDate): HabitVersion {
        val matching = versions.asSequence()
            .filter { !it.effectiveFromDate.isAfter(date) }
            .filter { it.effectiveUntilExclusive == null || it.effectiveUntilExclusive.isAfter(date) }
            .maxByOrNull(HabitVersion::effectiveFromDate)
        return matching ?: HabitVersion(
            id = versionId,
            habitId = id,
            effectiveFromDate = weekStart,
            title = title,
            color = color,
            period = period,
            targetCount = targetCount,
            scheduleDays = scheduleDays,
            intervalDays = intervalDays,
            scheduleStartDate = scheduleStartDate,
            createdAt = java.time.Instant.EPOCH,
        )
    }

    fun effectiveCountFor(date: LocalDate): Int = HabitScheduleRules
        .state(ruleOn(date), records, date)
        .periodCount

    fun isScheduledOn(date: LocalDate): Boolean = HabitScheduleRules
        .state(ruleOn(date), records, date)
        .isPlannedDate

    fun dayState(date: LocalDate): HabitDayState = HabitScheduleRules.state(ruleOn(date), records, date)
}

data class HabitWeekSnapshot(
    val weekStart: LocalDate,
    val items: List<HabitWeekItem>,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}
