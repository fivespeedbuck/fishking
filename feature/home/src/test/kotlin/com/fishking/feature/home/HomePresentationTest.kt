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
    fun weeklyHabitCompletesOnlyAfterItsWeeklyQuota() {
        val weekly = habit("weekly", 1, 2, HabitPeriod.WEEKLY)
        val checked = buildHomeDisplaySections(date, emptyList(), listOf(weekly))
        val unchecked = buildHomeDisplaySections(date.plusDays(1), emptyList(), listOf(weekly))
        assertFalse(checked.openHabits.single().isComplete)
        assertEquals(1, checked.openHabits.single().count)
        assertFalse(unchecked.openHabits.single().isComplete)
        assertEquals(1, unchecked.openHabits.single().count)
        assertFalse(buildWeekRows(buildWeekEntries(date, emptyList(), listOf(weekly))).single().startsCompletedSection)

        val complete = buildHomeDisplaySections(date, emptyList(), listOf(habit("weekly", 2, 2, HabitPeriod.WEEKLY)))
        assertTrue(complete.completedHabits.single().isComplete)
        assertEquals(2, complete.completedHabits.single().count)
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
            HabitPeriod.WEEKLY -> (0 until count).map { offset ->
                HabitDayRecord(id, date.minusDays(offset.toLong()), 1, false, Instant.EPOCH)
            }
            HabitPeriod.MONTHLY -> listOf(HabitDayRecord(id, date, count, false, Instant.EPOCH))
        },
    )
}
