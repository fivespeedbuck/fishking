package com.fishking.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.*

class ReminderSelectionRulesTest {
    private val today = LocalDate.of(2026, 9, 11)
    @Test fun normalDateAllowsTodayThroughTodoDateOnly() {
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), ReminderSelectionRules.validDates(today, today.plusDays(2)))
        assertFalse(ReminderSelectionRules.isValidDate(today.minusDays(1), today, today.plusDays(2)))
        assertFalse(ReminderSelectionRules.isValidDate(today.plusDays(3), today, today.plusDays(2)))
    }
    @Test fun deadlineIsTheUpperBoundEvenIfDisplayDateIsEarlier() {
        assertEquals(today.plusDays(5), ReminderSelectionRules.validDates(today, today, today.plusDays(5)).last())
        assertTrue(ReminderSelectionRules.validDates(today, today.minusDays(1)).isEmpty())
    }
    @Test fun todayMustBeStrictlyFutureAndRoundsToNextFiveMinutes() {
        val now = LocalDateTime.of(today, LocalTime.of(10, 11, 45))
        assertEquals(LocalTime.of(10, 15), ReminderSelectionRules.nextTime(now.toLocalTime()))
        assertFalse(ReminderSelectionRules.isValidTime(today, LocalTime.of(10, 11), now))
        assertFalse(ReminderSelectionRules.isValidTime(today, now.toLocalTime(), now))
        assertTrue(ReminderSelectionRules.validTimes(today, now, 1).all { it.isAfter(now.toLocalTime()) })
        assertEquals(1440, ReminderSelectionRules.validTimes(today.plusDays(1), now, 1).size)
    }
    @Test fun endOfDayDoesNotWrapToPastMidnight() {
        val now = LocalDateTime.of(today, LocalTime.of(23, 59))
        assertTrue(ReminderSelectionRules.validTimes(today, now, 1).isEmpty())
        assertEquals(LocalTime.of(23, 59), ReminderSelectionRules.validTimes(today, now.minusMinutes(1), 1).single())
        assertEquals(LocalTime.of(10, 15), ReminderSelectionRules.nextTime(LocalTime.of(10, 10)))
    }
    @Test fun staleDeadlineIsIgnoredForNonDeadlineScopes() {
        TodoPlanScope.values().forEach { scope ->
            val deadline = today.plusDays(4)
            val effective = ReminderSelectionRules.effectiveDeadline(scope, deadline)
            assertEquals(if (scope == TodoPlanScope.DEADLINE) deadline else null, effective)
            assertEquals(if (scope == TodoPlanScope.DEADLINE) deadline else today,
                ReminderSelectionRules.validDates(today, today, effective).last())
        }
    }
    @Test fun initialTimeKeepsValidSelectionAndReplacesExpiredSelection() {
        val now = LocalDateTime.of(today, LocalTime.of(15, 11, 30))
        assertEquals(LocalTime.of(15, 15), ReminderSelectionRules.initialTime(today, now, LocalTime.of(15, 11)))
        assertEquals(LocalTime.of(21, 43), ReminderSelectionRules.initialTime(today, now, LocalTime.of(21, 43)))
        assertEquals(LocalTime.of(9, 0), ReminderSelectionRules.initialTime(today.plusDays(1), now, null))
        assertEquals(null, ReminderSelectionRules.initialTime(today, now.withHour(23).withMinute(59), null))
    }
}
