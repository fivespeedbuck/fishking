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
