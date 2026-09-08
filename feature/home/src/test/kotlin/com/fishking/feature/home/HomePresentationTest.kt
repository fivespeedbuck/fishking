package com.fishking.feature.home

import com.fishking.core.model.HabitDayRecord
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoStatus
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePresentationTest {
    private val date = LocalDate.of(2026, 9, 4)

    @Test
    fun openAndCompletedSectionsRespectSharedTodoHabitPositions() {
        val sections = buildHomeDisplaySections(
            date = date,
            todos = listOf(
                todo("normal", TodoPriority.NORMAL, TodoStatus.OPEN, position = 100L),
                todo("done-normal", TodoPriority.NORMAL, TodoStatus.COMPLETED, position = 500L),
                todo("urgent", TodoPriority.URGENT, TodoStatus.OPEN, position = 300L),
                todo("done-urgent", TodoPriority.URGENT, TodoStatus.COMPLETED, position = 400L),
            ),
            habits = listOf(
                habit("open-habit", 1, 2, position = 200L),
                habit("done-habit", 2, 2, position = 600L),
            ),
        )

        assertEquals(listOf("todo-normal", "habit-open-habit", "todo-urgent"), sections.open.map { it.stableKey })
        assertEquals(listOf("todo-done-urgent", "todo-done-normal", "habit-done-habit"), sections.completed.map { it.stableKey })
    }

    @Test
    fun weeklyRowsNeverMixOpenAndCompletedCardsWhenOpenCountIsOdd() {
        val entries = buildWeekEntries(date, listOf(
            todo("open", TodoPriority.NORMAL, TodoStatus.OPEN),
            todo("done-normal", TodoPriority.NORMAL, TodoStatus.COMPLETED),
            todo("done-urgent", TodoPriority.URGENT, TodoStatus.COMPLETED),
        ), emptyList())
        val rows = buildWeekRows(entries)
        assertEquals(2, rows.size)
        assertEquals(1, rows[0].entries.size)
        assertFalse(rows[0].startsCompletedSection)
        assertTrue(rows[1].startsCompletedSection)
        assertEquals(listOf("done-normal", "done-urgent"), rows[1].entries.map { (it as WeekEntry.Todo).value.id })
    }

    @Test
    fun weeklyHabitMovesBelowDividerAfterTodaysCheckWithoutLosingPeriodProgress() {
        val weekly = habit("weekly", 2, 4, HabitPeriod.WEEKLY)
        val checked = buildHomeDisplaySections(date, emptyList(), listOf(weekly))
        val unchecked = buildHomeDisplaySections(date.plusDays(1), emptyList(), listOf(weekly))
        assertTrue(checked.openHabits.isEmpty())
        assertTrue(checked.completedHabits.single().isComplete)
        assertEquals(2, checked.completedHabits.single().count)
        assertEquals(4, checked.completedHabits.single().value.targetCount)
        assertFalse(unchecked.openHabits.single().isComplete)
        assertEquals(2, unchecked.openHabits.single().count)
        assertTrue(buildWeekRows(buildWeekEntries(date, emptyList(), listOf(weekly))).single().startsCompletedSection)
        assertFalse(buildWeekRows(buildWeekEntries(date.plusDays(1), emptyList(), listOf(weekly))).single().startsCompletedSection)
    }

    @Test
    fun monthlyHabitUsesTodaysCheckForBothHomeViews() {
        val monthly = habit("monthly", 2, 4, HabitPeriod.MONTHLY)
        val checked = buildHomeDisplaySections(date, emptyList(), listOf(monthly))
        assertTrue(checked.openHabits.isEmpty())
        assertEquals(2, checked.completedHabits.single().count)
        assertTrue(buildWeekRows(buildWeekEntries(date, emptyList(), listOf(monthly))).single().startsCompletedSection)
        val unchecked = buildHomeDisplaySections(date.plusDays(1), emptyList(), listOf(monthly))
        assertFalse(unchecked.openHabits.single().isComplete)
        assertEquals(2, unchecked.openHabits.single().count)
    }

    @Test
    fun undoingPeriodicCheckReturnsHabitAboveDividerAndPreservesOtherDates() {
        for (period in listOf(HabitPeriod.WEEKLY, HabitPeriod.MONTHLY)) {
            val before = habit("periodic", 2, 4, period)
            val undone = before.copy(records = before.records.filterNot { it.date == date })
            val sections = buildHomeDisplaySections(date, emptyList(), listOf(undone))
            assertTrue(sections.completedHabits.isEmpty())
            assertEquals(1, sections.openHabits.single().count)
            assertFalse(sections.openHabits.single().checkedOnDate)
            assertFalse(buildWeekRows(buildWeekEntries(date, emptyList(), listOf(undone))).single().startsCompletedSection)
        }
    }

    @Test
    fun periodQuotaDoesNotMarkAnUncheckedDateCompleted() {
        for (period in listOf(HabitPeriod.WEEKLY, HabitPeriod.MONTHLY)) {
            val completed = habit("periodic", 4, 4, period)
            val sections = buildHomeDisplaySections(date.plusDays(1), emptyList(), listOf(completed))
            assertTrue(sections.completedHabits.isEmpty())
            assertEquals(4, sections.openHabits.single().count)
            assertFalse(sections.openHabits.single().checkedOnDate)
        }
    }

    @Test
    fun dailyHabitStillNeedsItsFullDailyCount() {
        val partial = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 1, 2)))
        assertFalse(partial.openHabits.single().isComplete)
        val complete = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 2, 2)))
        assertTrue(complete.completedHabits.single().isComplete)
    }

    private fun todo(
        id: String,
        priority: TodoPriority,
        status: TodoStatus,
        position: Long = 0L,
    ) = TodoOccurrence(
        id = id,
        nominalDate = date,
        displayDate = date,
        title = id,
        priority = priority,
        status = status,
        position = position,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun habit(
        id: String,
        count: Int,
        target: Int,
        period: HabitPeriod = HabitPeriod.DAILY,
        position: Long = 0L,
    ) = HabitWeekItem(
        id = id,
        title = id,
        color = 0xFF8FA7E4,
        startDate = date.minusDays(4),
        position = position,
        weekStart = date.minusDays(4),
        versionId = "v-$id",
        period = period,
        targetCount = target,
        isSkipped = false,
        records = when (period) {
            HabitPeriod.DAILY -> listOf(HabitDayRecord(id, date, count, false, Instant.EPOCH))
            HabitPeriod.WEEKLY, HabitPeriod.MONTHLY -> (0 until count).map { offset ->
                HabitDayRecord(id, date.minusDays(offset.toLong()), 1, false, Instant.EPOCH)
            }
        },
    )
}
