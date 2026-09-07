package com.fishking.core.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainRulesTest {
    @Test
    fun `daily habit cycles from zero through target and back to zero`() {
        assertEquals(1, HabitRules.nextDailyCount(currentCount = 0, targetCount = 2))
        assertEquals(2, HabitRules.nextDailyCount(currentCount = 1, targetCount = 2))
        assertEquals(0, HabitRules.nextDailyCount(currentCount = 2, targetCount = 2))
        assertEquals(0, HabitRules.nextDailyCount(currentCount = 5, targetCount = 2))
    }

    @Test
    fun `weekly habit counts distinct real dates`() {
        val week = LocalDate.of(2026, 8, 31)
        val records = listOf(
            dayRecord(week, 2),
            dayRecord(week.plusDays(2), 1),
            dayRecord(week.minusDays(1), 1),
        )

        assertEquals(2, HabitRules.weeklyEffectiveDayCount(records, week))
    }

    @Test
    fun `monthly recurrence exposes both short month policies`() {
        val anchor = LocalDate.of(2026, 1, 31)
        val clamped = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            monthlyOverflowPolicy = MonthlyOverflowPolicy.CLAMP_TO_LAST_DAY,
        )
        val skipped = clamped.copy(monthlyOverflowPolicy = MonthlyOverflowPolicy.SKIP_MONTH)

        assertTrue(RecurrenceRules.occursOn(anchor, LocalDate.of(2026, 2, 28), clamped))
        assertFalse(RecurrenceRules.occursOn(anchor, LocalDate.of(2026, 2, 28), skipped))
        assertTrue(RecurrenceRules.occursOn(anchor, LocalDate.of(2026, 3, 31), skipped))
    }

    private fun dayRecord(date: LocalDate, count: Int) = HabitDayRecord(
        habitId = "habit",
        date = date,
        count = count,
        isBackfilled = false,
        updatedAt = Instant.EPOCH,
    )
}
