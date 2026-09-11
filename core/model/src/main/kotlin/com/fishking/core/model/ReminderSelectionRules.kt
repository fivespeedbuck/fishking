package com.fishking.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure rules shared by the quick-create and edit reminder surfaces. */
object ReminderSelectionRules {
    /** A leftover deadline field is never authoritative for a normal-date todo. */
    fun effectiveDeadline(scope: TodoPlanScope, deadline: LocalDate?): LocalDate? =
        deadline.takeIf { scope == TodoPlanScope.DEADLINE }

    fun upperBound(todoDate: LocalDate, deadline: LocalDate? = null): LocalDate =
        deadline ?: todoDate

    fun validDates(today: LocalDate, todoDate: LocalDate, deadline: LocalDate? = null): List<LocalDate> {
        val upper = upperBound(todoDate, deadline)
        if (upper.isBefore(today)) return emptyList()
        return generateSequence(today) { date -> date.plusDays(1).takeUnless { it.isAfter(upper) } }.toList()
    }

    fun isValidDate(date: LocalDate, today: LocalDate, todoDate: LocalDate, deadline: LocalDate? = null): Boolean =
        date in validDates(today, todoDate, deadline)

    /** Times for today are strictly in the future; a future day has every minute available. */
    fun validTimes(date: LocalDate, now: LocalDateTime, stepMinutes: Int = 5): List<LocalTime> {
        require(stepMinutes > 0 && 60 % stepMinutes == 0) { "stepMinutes must divide one hour" }
        val first = if (date == now.toLocalDate()) nextTime(now.toLocalTime(), stepMinutes) else LocalTime.MIDNIGHT
        if (date.isBefore(now.toLocalDate())) return emptyList()
        if (!first.isBefore(LocalTime.MAX)) return emptyList()
        val startMinutes = first.hour * 60 + first.minute
        return (0 until (24 * 60 / stepMinutes))
            .map { startMinutes + it * stepMinutes }
            .filter { it < 24 * 60 }
            .map { LocalTime.of(it / 60, it % 60) }
    }

    fun isValidTime(date: LocalDate, time: LocalTime, now: LocalDateTime): Boolean =
        if (date.isAfter(now.toLocalDate())) true else date == now.toLocalDate() && time.isAfter(now.toLocalTime())

    fun nextTime(now: LocalTime, stepMinutes: Int = 5): LocalTime {
        require(stepMinutes > 0)
        val total = now.hour * 60 + now.minute
        val rounded = ((total / stepMinutes) + 1) * stepMinutes
        return if (rounded >= 24 * 60) LocalTime.MAX else LocalTime.of(rounded / 60, rounded % 60)
    }

    fun initialTime(date: LocalDate, now: LocalDateTime, previous: LocalTime?): LocalTime? {
        val times = validTimes(date, now, 1)
        previous?.takeIf { it in times }?.let { return it }
        val preferred = if (date == now.toLocalDate()) nextTime(now.toLocalTime()) else LocalTime.of(9, 0)
        return preferred.takeIf { it in times } ?: times.firstOrNull()
    }
}
