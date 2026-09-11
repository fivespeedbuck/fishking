package com.fishking.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object RecurrenceRules {
    fun occursOn(anchor: LocalDate, candidate: LocalDate, rule: RecurrenceRule): Boolean {
        if (candidate.isBefore(anchor)) return false
        return when (rule.frequency) {
            RecurrenceFrequency.ONCE -> candidate == anchor
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY ->
                candidate.dayOfWeek == anchor.dayOfWeek &&
                    ChronoUnit.WEEKS.between(anchor, candidate) >= 0
            RecurrenceFrequency.MONTHLY -> monthlyOccurrence(anchor, candidate, rule.monthlyOverflowPolicy)
        }
    }

    fun datesBetween(
        anchor: LocalDate,
        fromInclusive: LocalDate,
        toInclusive: LocalDate,
        rule: RecurrenceRule,
    ): List<LocalDate> {
        if (toInclusive.isBefore(fromInclusive)) return emptyList()
        val from = maxOf(anchor, fromInclusive)
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(toInclusive) }
            .filter { occursOn(anchor, it, rule) }
            .toList()
    }

    private fun monthlyOccurrence(
        anchor: LocalDate,
        candidate: LocalDate,
        overflowPolicy: MonthlyOverflowPolicy,
    ): Boolean {
        if (candidate.year == anchor.year && candidate.month == anchor.month) return candidate == anchor
        val monthStart = candidate.withDayOfMonth(1)
        val expectedDay = when (overflowPolicy) {
            MonthlyOverflowPolicy.CLAMP_TO_LAST_DAY -> minOf(anchor.dayOfMonth, monthStart.lengthOfMonth())
            MonthlyOverflowPolicy.SKIP_MONTH -> {
                if (anchor.dayOfMonth > monthStart.lengthOfMonth()) return false
                anchor.dayOfMonth
            }
        }
        return candidate.dayOfMonth == expectedDay
    }
}

object HabitRules {
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun nextDailyCount(currentCount: Int, targetCount: Int): Int {
        require(targetCount > 0) { "targetCount must be positive" }
        return if (currentCount >= targetCount) 0 else currentCount + 1
    }

    fun weeklyEffectiveDayCount(records: Collection<HabitDayRecord>, weekStart: LocalDate): Int {
        val weekEnd = weekStart.plusDays(6)
        return records.asSequence()
            .filter { it.count > 0 && !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
            .map(HabitDayRecord::date)
            .distinct()
            .count()
    }
}

/**
 * One source of truth for habit schedule and homepage projection.  Every query is evaluated
 * as-of [date]; future records must never make a historical day look completed or hidden.
 */
data class HabitDayState(
    val rule: HabitVersion,
    val actualCount: Int,
    val periodCount: Int,
    val isPlannedDate: Boolean,
    val isDue: Boolean,
    val isCompleteOnDate: Boolean,
    val shouldAppearOnHome: Boolean,
    val previousCompletionDate: LocalDate?,
    val nextDueDate: LocalDate?,
)

object HabitScheduleRules {
    fun state(rule: HabitVersion, allRecords: Collection<HabitDayRecord>, date: LocalDate): HabitDayState {
        val records = allRecords.asSequence()
            .filter { it.count > 0 && !it.date.isAfter(date) }
            .sortedBy(HabitDayRecord::date)
            .toList()
        val actual = records.firstOrNull { it.date == date }?.count ?: 0
        val periodCount = periodCount(rule, records, date)
        val planned = isPlannedDate(rule, records, date)
        val anchor = if (rule.period == HabitPeriod.AFTER_COMPLETION_N_DAYS) {
            // Dynamic cadence follows the latest real completion, including a completion
            // entered later as a historical backfill. Otherwise yesterday's forgotten tick
            // would incorrectly leave today immediately available again.
            records.lastOrNull()?.date
        } else {
            null
        }
        val nextDue = when (rule.period) {
            HabitPeriod.EVERY_N_DAYS -> fixedCadenceNextUnconsumed(rule, records)
            HabitPeriod.AFTER_COMPLETION_N_DAYS -> (anchor?.plusDays(rule.intervalDays.toLong()) ?: rule.scheduleStartDate)
            else -> null
        }
        val due = when (rule.period) {
            HabitPeriod.EVERY_N_DAYS,
            HabitPeriod.AFTER_COMPLETION_N_DAYS,
            -> nextDue != null && !date.isBefore(nextDue)
            else -> planned && periodCount < rule.targetCount
        }
        val completeToday = when (rule.period) {
            HabitPeriod.DAILY -> actual >= rule.targetCount
            else -> actual > 0
        }
        // A true actual completion always projects today, even when it happened off-plan.
        // Without an actual completion, only the currently due planned work projects.
        val appears = actual > 0 || (!completeToday && due)
        return HabitDayState(
            rule = rule,
            actualCount = actual,
            periodCount = periodCount,
            isPlannedDate = planned,
            isDue = due,
            isCompleteOnDate = completeToday,
            shouldAppearOnHome = appears,
            previousCompletionDate = anchor,
            nextDueDate = nextDue,
        )
    }

    private fun periodCount(rule: HabitVersion, records: List<HabitDayRecord>, date: LocalDate): Int = when (rule.period) {
        HabitPeriod.DAILY -> records.firstOrNull { it.date == date }?.count ?: 0
        HabitPeriod.WEEKLY -> records.asSequence()
            .filter { !it.date.isBefore(HabitRules.weekStart(date)) }
            .map(HabitDayRecord::date).distinct().count()
        HabitPeriod.MONTHLY -> records.asSequence()
            .filter { java.time.YearMonth.from(it.date) == java.time.YearMonth.from(date) }
            .map(HabitDayRecord::date).distinct().count()
        HabitPeriod.EVERY_N_DAYS,
        HabitPeriod.AFTER_COMPLETION_N_DAYS,
        -> if (records.any { it.date == date }) 1 else 0
    }

    private fun isPlannedDate(rule: HabitVersion, records: List<HabitDayRecord>, date: LocalDate): Boolean = when (rule.period) {
        HabitPeriod.DAILY -> true
        HabitPeriod.WEEKLY -> if (rule.scheduleDays.isEmpty()) true else {
            val candidates = rule.scheduleDays.sorted()
                .map { HabitRules.weekStart(date).plusDays((it - 1).toLong()) }
                .filter { !it.isAfter(HabitRules.weekStart(date).plusDays(6)) }
            candidates.isNotEmpty() && plannedFlexibleDate(
                candidates = candidates,
                records = records.filter { !it.date.isBefore(HabitRules.weekStart(date)) },
                date = date,
                target = rule.targetCount,
            )
        }
        HabitPeriod.MONTHLY -> if (rule.scheduleDays.isEmpty()) true else {
            val candidates = rule.scheduleDays.sorted().mapNotNull { day ->
                val month = java.time.YearMonth.from(date)
                if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
            }
            candidates.isNotEmpty() && plannedFlexibleDate(
                candidates = candidates,
                records = records.filter { java.time.YearMonth.from(it.date) == java.time.YearMonth.from(date) },
                date = date,
                target = rule.targetCount,
            )
        }
        HabitPeriod.EVERY_N_DAYS -> date >= rule.scheduleStartDate &&
            ChronoUnit.DAYS.between(rule.scheduleStartDate, date) % rule.intervalDays == 0L
        HabitPeriod.AFTER_COMPLETION_N_DAYS -> false
    }

    /**
     * Candidate weekdays/month-days are reminder slots, not exclusive permissions. A real
     * early completion consumes the next candidate slot, so Wed/Sat plus Tue completion does
     * not nag again on Wednesday. Empty candidates intentionally means flexible period goal.
     */
    private fun plannedFlexibleDate(
        candidates: List<LocalDate>,
        records: List<HabitDayRecord>,
        date: LocalDate,
        target: Int,
    ): Boolean {
        if (candidates.isEmpty()) return true
        val candidateIndex = candidates.indexOf(date)
        if (candidateIndex < 0) return false
        val completedBefore = records.count { it.date.isBefore(date) }
        return completedBefore < target && candidateIndex >= completedBefore
    }

    /** Assign every real completion to the earliest still-unconsumed fixed slot. */
    private fun fixedCadenceNextUnconsumed(
        rule: HabitVersion,
        records: List<HabitDayRecord>,
    ): LocalDate {
        val interval = rule.intervalDays.toLong()
        var nextSlot = rule.scheduleStartDate
        records.filter { !it.date.isBefore(rule.scheduleStartDate) }.forEach {
            if (!it.date.isBefore(nextSlot)) {
                // One late completion clears all missed slots through that date while the
                // underlying calendar cadence remains anchored to scheduleStartDate.
                val elapsed = ChronoUnit.DAYS.between(rule.scheduleStartDate, it.date)
                val nextIndex = elapsed / interval + 1
                nextSlot = rule.scheduleStartDate.plusDays(nextIndex * interval)
            } else {
                // An early completion consumes exactly the next future slot.
                nextSlot = nextSlot.plusDays(interval)
            }
        }
        return nextSlot
    }
}
