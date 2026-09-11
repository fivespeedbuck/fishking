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
    fun weeklyCompletionReservesTheLeftmostCompletedSlotForTheIncomingCard() {
        val entries = buildWeekEntries(
            date,
            listOf(
                todo("done-first", TodoPriority.NORMAL, TodoStatus.COMPLETED),
                todo("done-incoming", TodoPriority.NORMAL, TodoStatus.COMPLETED),
            ),
            emptyList(),
        )

        val rows = buildWeekRows(entries, completedLeadKey = "todo-done-incoming")

        assertEquals("done-incoming", (rows.single().entries.first() as WeekEntry.Todo).value.id)
        assertTrue(rows.single().startsCompletedSection)
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
    fun offPlanCompletionStillAppearsInTodaysCompletedSection() {
        val checkedOffPlan = habit(
            id = "wed-sat",
            count = 1,
            target = 2,
            period = HabitPeriod.WEEKLY,
            scheduleDays = setOf(3, 6),
        )
        val checked = buildHomeDisplaySections(date, emptyList(), listOf(checkedOffPlan))
        assertEquals("wed-sat", checked.completedHabits.single().value.id)

        val uncheckedOffPlan = checkedOffPlan.copy(records = emptyList())
        val unchecked = buildHomeDisplaySections(date, emptyList(), listOf(uncheckedOffPlan))
        assertTrue(unchecked.openHabits.isEmpty())
        assertTrue(unchecked.completedHabits.isEmpty())
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
    fun periodQuotaHidesUncheckedFutureDaysAfterTargetIsMet() {
        for (period in listOf(HabitPeriod.WEEKLY, HabitPeriod.MONTHLY)) {
            val completed = habit("periodic", 4, 4, period)
            val sections = buildHomeDisplaySections(date.plusDays(1), emptyList(), listOf(completed))
            assertTrue(sections.completedHabits.isEmpty())
            assertTrue(sections.openHabits.isEmpty())
        }
    }

    @Test
    fun dailyHabitStillNeedsItsFullDailyCount() {
        val partial = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 1, 2)))
        assertFalse(partial.openHabits.single().isComplete)
        val complete = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 2, 2)))
        assertTrue(complete.completedHabits.single().isComplete)
    }

    @Test
    fun habitTapOnlyCrossesDividerAtDailyTargetOrPeriodicToggle() {
        val partial = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 1, 3)))
            .openHabits.single()
        assertFalse(partial.willCrossCompletionOnTap())

        val finalDaily = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 2, 3)))
            .openHabits.single()
        assertTrue(finalDaily.willCrossCompletionOnTap())

        val weeklyOpen = buildHomeDisplaySections(date, emptyList(), listOf(habit("weekly", 0, 4, HabitPeriod.WEEKLY)))
            .openHabits.single()
        assertTrue(weeklyOpen.willCrossCompletionOnTap())
    }

    @Test
    fun habitCompletionOrderMovesAcrossDividerWithoutChangingOtherCards() {
        val habit = buildHomeDisplaySections(date, emptyList(), listOf(habit("daily", 1, 1))).completedHabits.single()
        val other = buildHomeDisplaySections(date, listOf(todo("other", TodoPriority.NORMAL, TodoStatus.COMPLETED, 500L)), emptyList())
            .completed.single()
        val before = HomeDayItems(open = emptyList(), completed = listOf(habit, other))
        val undone = habit.copy(count = 0, checkedOnDate = false, isComplete = false)
        val moved = before.moveAcrossDivider(undone)
        assertEquals(listOf("habit-daily"), moved.open.map { it.stableKey })
        assertEquals(listOf("todo-other"), moved.completed.map { it.stableKey })
    }

    @Test
    fun completionSlotsConserveSourceHeightAtEveryProgress() {
        val sourceHeight = 137
        for (progress in listOf(0f, .17f, .5f, .83f, 1f)) {
            val slots = homeCompletionSlotHeights(sourceHeight, progress)
            assertEquals(sourceHeight, slots.source + slots.destination)
        }
    }

    @Test
    fun completionTransitionRemovesOutgoingTodoBeforeTargetReveal() {
        val before = todo("moving", TodoPriority.NORMAL, TodoStatus.OPEN, position = 200L)
        val after = buildHomeDisplaySections(
            date,
            listOf(before.copy(status = TodoStatus.COMPLETED)),
            emptyList(),
        )

        val held = after.removeOutgoingItem(HomeCompletionTransition(before, targetCompleted = true))

        assertTrue(held.openNormal.isEmpty())
        assertTrue(held.completedNormal.isEmpty())
    }

    @Test
    fun undoTransitionRemovesOutgoingTodoBeforeTargetReveal() {
        val before = todo("moving", TodoPriority.NORMAL, TodoStatus.COMPLETED, position = 200L)
        val after = buildHomeDisplaySections(
            date,
            listOf(before.copy(status = TodoStatus.OPEN)),
            emptyList(),
        )

        val held = after.removeOutgoingItem(HomeCompletionTransition(before, targetCompleted = false))

        assertTrue(held.completedNormal.isEmpty())
        assertTrue(held.openNormal.isEmpty())
    }

    @Test
    fun completionTargetIsRevealedAtDestinationSectionHead() {
        val before = todo("moving", TodoPriority.NORMAL, TodoStatus.OPEN, position = 200L)
        val after = buildHomeDisplaySections(
            date,
            listOf(
                before.copy(status = TodoStatus.COMPLETED),
                todo("already-done", TodoPriority.NORMAL, TodoStatus.COMPLETED, position = 300L),
            ),
            emptyList(),
        )

        val revealed = after.revealCompletionTarget(HomeCompletionTransition(before, targetCompleted = true))

        assertEquals(listOf("todo-moving", "todo-already-done"), revealed.completedNormal.map { it.stableKey })
    }

    @Test
    fun nDayHabitsUseDueStateAndActualCompletionForHomeGrouping() {
        for (period in listOf(HabitPeriod.EVERY_N_DAYS, HabitPeriod.AFTER_COMPLETION_N_DAYS)) {
            val due = buildHomeDisplaySections(date, emptyList(), listOf(habit("n-day", 0, 1, period)))
            assertEquals("n-day", due.openHabits.single().value.id)

            val done = buildHomeDisplaySections(date, emptyList(), listOf(habit("n-day", 1, 1, period)))
            assertEquals("n-day", done.completedHabits.single().value.id)
        }
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
        scheduleDays: Set<Int> = emptySet(),
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
        scheduleDays = scheduleDays,
        isSkipped = false,
        records = when (period) {
            HabitPeriod.DAILY -> listOf(HabitDayRecord(id, date, count, false, Instant.EPOCH))
            HabitPeriod.WEEKLY, HabitPeriod.MONTHLY -> (0 until count).map { offset ->
                HabitDayRecord(id, date.minusDays(offset.toLong()), 1, false, Instant.EPOCH)
            }
            HabitPeriod.EVERY_N_DAYS,
            HabitPeriod.AFTER_COMPLETION_N_DAYS,
            -> if (count > 0) listOf(HabitDayRecord(id, date, 1, false, Instant.EPOCH)) else emptyList()
        },
    )
}
