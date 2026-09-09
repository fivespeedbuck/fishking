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

    @Test
    fun `fixed cadence does not stack missed slots and an early completion does not shift its calendar`() {
        val start = LocalDate.of(2026, 9, 9)
        val rule = rule(HabitPeriod.EVERY_N_DAYS, start, 3)
        assertTrue(HabitScheduleRules.state(rule, emptyList(), start.plusDays(4)).isDue)
        val late = dayRecord(start.plusDays(4), 1)
        assertEquals(start.plusDays(6), HabitScheduleRules.state(rule, listOf(late), start.plusDays(4)).nextDueDate)

        val onTime = dayRecord(start, 1)
        val earlyExtra = dayRecord(start.plusDays(1), 1)
        assertEquals(start.plusDays(6), HabitScheduleRules.state(rule, listOf(onTime, earlyExtra), start.plusDays(1)).nextDueDate)

        val thirdEarly = dayRecord(start.plusDays(2), 1)
        assertEquals(
            start.plusDays(9),
            HabitScheduleRules.state(rule, listOf(onTime, earlyExtra, thirdEarly), start.plusDays(2)).nextDueDate,
        )
    }

    @Test
    fun `missing month day does not turn a selected monthly schedule into every day`() {
        val start = LocalDate.of(2026, 1, 31)
        val monthly = rule(HabitPeriod.MONTHLY, start, 1).copy(
            targetCount = 1,
            scheduleDays = setOf(31),
        )
        assertFalse(HabitScheduleRules.state(monthly, emptyList(), LocalDate.of(2026, 2, 14)).isPlannedDate)
        assertFalse(HabitScheduleRules.state(monthly, emptyList(), LocalDate.of(2026, 2, 28)).shouldAppearOnHome)
    }

    @Test
    fun `dynamic cadence uses latest anchor and ignores ordinary historical backfills`() {
        val start = LocalDate.of(2026, 9, 1)
        val rule = rule(HabitPeriod.AFTER_COMPLETION_N_DAYS, start, 3)
        val backfill = dayRecord(start.plusDays(1), 1).copy(isBackfilled = true, affectsScheduleAnchor = false)
        val today = dayRecord(start.plusDays(4), 1)
        assertEquals(start.plusDays(7), HabitScheduleRules.state(rule, listOf(backfill, today), start.plusDays(4)).nextDueDate)
        assertTrue(HabitScheduleRules.state(rule, listOf(backfill, today), start.plusDays(7)).isDue)
    }

    private fun rule(period: HabitPeriod, start: LocalDate, interval: Int) = HabitVersion(
        id = "version", habitId = "habit", effectiveFromDate = start, title = "测试", color = 1,
        period = period, targetCount = 1, intervalDays = interval, scheduleStartDate = start, createdAt = Instant.EPOCH,
    )

    private fun dayRecord(date: LocalDate, count: Int) = HabitDayRecord(
        habitId = "habit",
        date = date,
        count = count,
        isBackfilled = false,
        updatedAt = Instant.EPOCH,
    )
}
