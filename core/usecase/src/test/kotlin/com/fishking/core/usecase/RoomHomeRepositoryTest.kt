package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.LifeGoalEntity
import com.fishking.core.database.LifeGoalEventEntity
import com.fishking.core.database.TodoLifeGoalCrossRef
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.RecurrenceRule
import com.fishking.core.model.TodoReminderSpec
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.TodoPlanScope
import com.fishking.core.model.TodoStatus
import com.fishking.core.model.HabitPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class RoomHomeRepositoryTest {
    private lateinit var database: FishKingDatabase
    private lateinit var repository: RoomHomeRepository
    private val date = LocalDate.of(2026, 9, 4)
    private val now = Instant.parse("2026-09-04T07:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FishKingDatabase::class.java,
        ).allowMainThreadQueries().build()
        val next = AtomicInteger()
        repository = RoomHomeRepository(
            database = database,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            newId = { "generated-${next.incrementAndGet()}" },
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun cardTimeProjectionReflectsEnabledReminderChanges() = runTest {
        val id = repository.createTodo("定时清单", date)
        assertTrue(repository.observeTodos(date).first().single().displayReminders.isEmpty())
        repository.setReminders(id, listOf(TodoReminderSpec(localTime = LocalTime.of(21, 0)), TodoReminderSpec(localTime = LocalTime.of(22, 30))))
        assertEquals(listOf(LocalTime.of(21, 0), LocalTime.of(22, 30)), repository.observeTodos(date).first().single().displayReminders.map { it.localTime })
        repository.toggleCompletion(id)
        assertEquals(2, repository.observeTodos(date).first().single().displayReminders.size)
        repository.setReminders(id, emptyList())
        assertTrue(repository.observeTodos(date).first().single().displayReminders.isEmpty())
    }

    @Test
    fun blankTitleNeverCreatesRow() = runTest {
        val error = runCatching { repository.createTodo("   ", date) }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(0, database.todoDao().observeForDate(date).first().size)
    }

    @Test
    fun deadlineTodoWithoutDeadlineNeverCreatesPartialRow() = runTest {
        val error = runCatching {
            repository.createTodo(
                title = "等待截止日",
                date = date,
                planScope = TodoPlanScope.DEADLINE,
                planDeadline = null,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(database.todoDao().observePlanned().first().isEmpty())
        assertTrue(database.todoDao().observeForDate(date).first().isEmpty())
    }

    @Test
    fun scopedTodoIsInitiallyInsertedWithItsFixedCalendarDeadline() = runTest {
        val id = repository.createTodo(
            title = "本月完成",
            date = date,
            planScope = TodoPlanScope.MONTH,
        )

        val row = database.todoDao().findOccurrence(id)
        assertEquals(TodoPlanScope.MONTH.name, row?.planScope)
        assertEquals(LocalDate.of(2026, 9, 30), row?.planDeadline)
    }

    @Test
    fun scopedTodoCannotAlsoBeRecurringAndLeavesNoRows() = runTest {
        val error = runCatching {
            repository.createTodo(
                title = "冲突任务",
                date = date,
                recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
                planScope = TodoPlanScope.MONTH,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(database.todoDao().observePlanned().first().isEmpty())
        assertTrue(database.todoDao().observeForDate(date).first().isEmpty())
    }

    @Test
    fun unfinishedScopedTodoRemainsVisibleAfterItsDeadline() = runTest {
        val id = repository.createTodo(
            title = "逾期仍挂着",
            date = date,
            planScope = TodoPlanScope.WEEK,
        )
        val afterDeadline = date.plusWeeks(2)

        val visible = repository.observeTodos(afterDeadline).first()
        assertEquals(listOf(id), visible.map { it.id })
        assertEquals(TodoStatus.OPEN, visible.single().status)
        assertEquals(LocalDate.of(2026, 9, 6), visible.single().planDeadline)
    }

    @Test
    fun reorderTodosPersistsOnlyTheRequestedPriorityGroup() = runTest {
        val first = repository.createTodo("第一项", date)
        val second = repository.createTodo("第二项", date)
        val urgent = repository.createTodo("紧急项", date)
        repository.togglePriority(urgent)

        repository.reorderTodos(date, listOf(second, first))

        val rows = database.todoDao().observeForDate(date).first()
        assertEquals(listOf(urgent, second, first), rows.map { it.id })
        assertEquals(listOf(second, first), database.todoDao()
            .occurrencesInGroup(date, "OPEN", "NORMAL").map { it.id })
    }

    @Test
    fun moveTodoRelativeChangesDateAndPersistsTheHoveredSlot() = runTest {
        val source = repository.createTodo("要移动", date)
        val targetDate = date.plusDays(1)
        val targetOlder = repository.createTodo("目标旧项", targetDate)
        val targetNewer = repository.createTodo("目标新项", targetDate)

        assertTrue(repository.moveTodoRelative(source, targetNewer, placeAfterTarget = true))

        val targetRows = database.todoDao().occurrencesInGroup(targetDate, "OPEN", "NORMAL")
        assertEquals(listOf(targetNewer, source, targetOlder), targetRows.map { it.id })
        assertEquals(targetDate, targetRows.single { it.id == source }.displayDate)
        assertTrue(repository.observeTodos(date).first().none { it.id == source })
    }

    @Test
    fun completionCreatesOneAutoCheckPerLinkedGoalAndUndoKeepsManualHistory() = runTest {
        val todoId = repository.createTodo("去泰山", date)
        insertGoal("goal-a", "去泰山")
        insertGoal("goal-b", "登一座高山")
        database.todoDao().linkGoal(TodoLifeGoalCrossRef(todoId, "goal-a"))
        database.todoDao().linkGoal(TodoLifeGoalCrossRef(todoId, "goal-b"))
        database.lifeGoalDao().insertEvents(
            listOf(
                LifeGoalEventEntity(
                    id = "manual-x",
                    goalId = "goal-a",
                    occurredOn = date.minusDays(1),
                    result = LifeGoalResult.CROSS.name,
                    source = LifeGoalEventSource.MANUAL.name,
                    sourceTodoOccurrenceId = null,
                    position = 1_024L,
                    createdAt = now.minusSeconds(60),
                ),
            ),
        )

        repository.toggleCompletion(todoId)

        val afterCompletion = database.lifeGoalDao().observeActiveGoals().first()
        assertEquals(TodoStatus.COMPLETED.name, database.todoDao().findOccurrence(todoId)?.status)
        assertEquals(2, afterCompletion.sumOf { goal -> goal.events.count { it.sourceTodoOccurrenceId == todoId } })
        assertEquals(1, afterCompletion.first { it.goal.id == "goal-a" }.events.count { it.id == "manual-x" })

        repository.toggleCompletion(todoId)

        val afterUndo = database.lifeGoalDao().observeActiveGoals().first()
        assertEquals(TodoStatus.OPEN.name, database.todoDao().findOccurrence(todoId)?.status)
        assertEquals(0, afterUndo.sumOf { goal -> goal.events.count { it.sourceTodoOccurrenceId == todoId } })
        assertEquals(1, afterUndo.first { it.goal.id == "goal-a" }.events.count { it.id == "manual-x" })
    }

    @Test
    fun editingGoalLinksIsLiveAndDoesNotBackfillAlreadyCompletedTodo() = runTest {
        val todoId = repository.createTodo("去泰山", date)
        insertGoal("goal-a", "去泰山")
        repository.toggleCompletion(todoId)

        assertTrue(repository.toggleGoalLink(todoId, "goal-a"))
        assertEquals(listOf("goal-a"), repository.observeLinkedGoalIds(todoId).first())
        assertTrue(database.lifeGoalDao().observeActiveGoals().first().single().events.isEmpty())

        assertFalse(repository.toggleGoalLink(todoId, "goal-a"))
        assertTrue(repository.observeLinkedGoalIds(todoId).first().isEmpty())
    }

    @Test
    fun deletingCompletedTodoKeepsGoalEventAndCompletedTodoCannotMove() = runTest {
        val todoId = repository.createTodo("完成资产大王", date)
        insertGoal("goal-a", "完成资产大王")
        database.todoDao().linkGoal(TodoLifeGoalCrossRef(todoId, "goal-a"))
        repository.toggleCompletion(todoId)

        val moved = repository.moveTodo(todoId, date.plusDays(1))
        repository.deleteTodo(todoId)

        assertFalse(moved)
        assertEquals(0, database.todoDao().observeForDate(date).first().size)
        val event = database.lifeGoalDao().observeActiveGoals().first().single().events.single()
        assertEquals(todoId, event.sourceTodoOccurrenceId)
        assertEquals(LifeGoalResult.CHECK.name, event.result)
        assertNotNull(database.todoDao().findOccurrence(todoId)?.deletedAt)
    }

    @Test
    fun openTodoMovesToTargetDateWithoutChangingNominalIdentity() = runTest {
        val todoId = repository.createTodo("带伞", date)

        val moved = repository.moveTodo(todoId, date.plusDays(1))
        val row = database.todoDao().findOccurrence(todoId)

        assertEquals(true, moved)
        assertEquals(date, row?.nominalDate)
        assertEquals(date.plusDays(1), row?.displayDate)
        assertNull(row?.completedAt)
    }

    @Test
    fun inlineEditsPersistTitleAccentAndPriorityWithoutChangingIdentity() = runTest {
        val todoId = repository.createTodo("  原标题  ", date)

        repository.updateTitle(todoId, "  新标题  ")
        repository.setAccentColor(todoId, 0xFF8FA7E4)
        repository.togglePriority(todoId)
        val row = database.todoDao().findOccurrence(todoId)

        assertEquals("新标题", row?.title)
        assertEquals(0xFF8FA7E4, row?.accentColor)
        assertEquals("URGENT", row?.priority)
        assertEquals(date, row?.nominalDate)
    }

    @Test
    fun recurringTodoMaterializesInstancesAndCopiesMultipleReminders() = runTest {
        val todoId = repository.createTodo(
            title = "刷牙",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminders = listOf(
                TodoReminderSpec(localTime = LocalTime.of(8, 0), position = 0),
                TodoReminderSpec(localTime = LocalTime.of(22, 0), position = 1),
            ),
        )

        val tomorrow = database.todoDao().observeForDate(date.plusDays(1)).first()
        val reminders = database.todoDao().observeReminders(todoId).first()

        assertEquals(1, tomorrow.size)
        assertEquals("刷牙", tomorrow.single().title)
        assertEquals(2, reminders.size)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(22, 0)), reminders.map { it.localTime })
        assertEquals(todoId, database.todoDao().findOccurrence(todoId)?.id)
    }

    @Test
    fun ensureOccurrencesMaterializesBeyondInitialHorizonWithoutDuplicates() = runTest {
        repository.createTodo(
            title = "每周复盘",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
            reminders = listOf(TodoReminderSpec(localTime = LocalTime.of(20, 30))),
        )
        val distant = date.plusWeeks(150)

        repository.ensureOccurrences(distant)
        repository.ensureOccurrences(distant)

        val rows = database.todoDao().observeForDate(distant).first()
        assertEquals(1, rows.size)
        assertEquals(distant, rows.single().nominalDate)
        assertEquals(
            listOf(LocalTime.of(20, 30)),
            database.todoDao().observeReminders(rows.single().id).first().map { it.localTime },
        )
    }

    @Test
    fun rollingWindowExtendsRecurringOccurrencesAndTheirRemindersPastInitialHorizon() = runTest {
        repository.createTodo(
            title = "每日提醒",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminders = listOf(TodoReminderSpec(localTime = LocalTime.of(8, 30))),
        )
        val from = date.plusDays(91)
        val through = date.plusDays(100)

        repository.ensureOccurrenceWindow(from, through)

        for (day in from.datesUntil(through.plusDays(1)).toList()) {
            val occurrence = database.todoDao().observeForDate(day).first().single()
            assertEquals(
                listOf(LocalTime.of(8, 30)),
                database.todoDao().remindersForOccurrence(occurrence.id).map { it.localTime },
            )
        }
    }

    @Test
    fun deletingOnlyThisRecurringOccurrenceKeepsLaterSeriesInstances() = runTest {
        repository.createTodo(
            title = "喝水",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )
        val tomorrow = date.plusDays(1)
        val tomorrowId = database.todoDao().observeForDate(tomorrow).first().single().id

        repository.deleteTodo(tomorrowId, TodoChangeScope.ONLY_THIS)

        assertEquals(1, database.todoDao().observeForDate(date).first().size)
        assertEquals(0, database.todoDao().observeForDate(tomorrow).first().size)
        assertEquals(1, database.todoDao().observeForDate(date.plusDays(2)).first().size)
    }

    @Test
    fun deletingThisAndFutureStopsSeriesWithoutRewritingPast() = runTest {
        val firstId = repository.createTodo(
            title = "拉伸",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )
        val seriesId = database.todoDao().findOccurrence(firstId)?.seriesId!!
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id

        repository.deleteTodo(boundaryId, TodoChangeScope.THIS_AND_FUTURE)
        repository.ensureOccurrences(date.plusDays(200))

        assertEquals(1, database.todoDao().observeForDate(date).first().size)
        assertEquals(1, database.todoDao().observeForDate(date.plusDays(1)).first().size)
        assertEquals(0, database.todoDao().observeForDate(boundary).first().size)
        assertEquals(0, database.todoDao().observeForDate(date.plusDays(200)).first().size)
        assertEquals(boundary, database.todoDao().findSeries(seriesId)?.activeUntilExclusive)
    }

    @Test
    fun deletingRecurrenceKeepsSelectedOccurrenceAsStandaloneAndStopsFuture() = runTest {
        val firstId = repository.createTodo(
            title = "记账",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminders = listOf(TodoReminderSpec(localTime = LocalTime.of(22, 0))),
        )
        val seriesId = database.todoDao().findOccurrence(firstId)?.seriesId!!
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id

        repository.stopRecurrence(boundaryId)
        repository.ensureOccurrences(date.plusDays(200))

        val standalone = database.todoDao().findOccurrence(boundaryId)
        assertNull(standalone?.seriesId)
        assertNull(standalone?.seriesVersionId)
        assertEquals(boundary, standalone?.nominalDate)
        assertEquals(1, database.todoDao().observeForDate(boundary).first().size)
        assertEquals(0, database.todoDao().observeForDate(boundary.plusDays(1)).first().size)
        assertEquals(0, database.todoDao().observeForDate(date.plusDays(200)).first().size)
        assertEquals(boundary, database.todoDao().findSeries(seriesId)?.activeUntilExclusive)
        assertEquals(
            listOf(LocalTime.of(22, 0)),
            database.todoDao().observeReminders(boundaryId).first().map { it.localTime },
        )
    }

    @Test
    fun editingOnlyThisCreatesAnExceptionWithoutChangingLaterInstances() = runTest {
        val firstId = repository.createTodo(
            title = "原名称",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )

        repository.updateTitle(firstId, "只改今天", TodoChangeScope.ONLY_THIS)

        val today = database.todoDao().findOccurrence(firstId)
        assertEquals("只改今天", today?.title)
        assertEquals(true, today?.isSeriesException)
        assertEquals("原名称", database.todoDao().observeForDate(date.plusDays(1)).first().single().title)
    }

    @Test
    fun editingThisAndFutureForksSeriesAndKeepsPastUntouched() = runTest {
        val firstId = repository.createTodo(
            title = "旧名称",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )
        val oldSeriesId = database.todoDao().findOccurrence(firstId)?.seriesId!!
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id

        repository.updateTitle(boundaryId, "新名称", TodoChangeScope.THIS_AND_FUTURE)

        val changed = database.todoDao().findOccurrence(boundaryId)
        assertEquals("旧名称", database.todoDao().observeForDate(date).first().single().title)
        assertEquals("旧名称", database.todoDao().observeForDate(date.plusDays(1)).first().single().title)
        assertEquals("新名称", changed?.title)
        assertEquals(boundaryId, changed?.id)
        assertFalse(changed?.seriesId == oldSeriesId)
        assertEquals("新名称", database.todoDao().observeForDate(boundary.plusDays(1)).first().single().title)
        assertEquals(boundary, database.todoDao().findSeries(oldSeriesId)?.activeUntilExclusive)
    }

    @Test
    fun changingRecurrenceRebuildsOnlyTheSelectedAndFutureSchedule() = runTest {
        repository.createTodo(
            title = "复盘",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id

        repository.updateRecurrence(boundaryId, RecurrenceRule(RecurrenceFrequency.WEEKLY))

        assertEquals(1, database.todoDao().observeForDate(date).first().size)
        assertEquals(1, database.todoDao().observeForDate(date.plusDays(1)).first().size)
        assertEquals(boundaryId, database.todoDao().observeForDate(boundary).first().single().id)
        assertEquals(0, database.todoDao().observeForDate(boundary.plusDays(1)).first().size)
        assertEquals(1, database.todoDao().observeForDate(boundary.plusWeeks(1)).first().size)
    }

    @Test
    fun habitPageEditAtomicallyChangesRecurringTitleColorFrequencyAndWeekday() = runTest {
        val firstId = repository.createTodo(
            title = "旧周期任务",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id
        val newWeekday = boundary.plusDays(3)

        repository.updateRecurringTodo(
            occurrenceId = boundaryId,
            title = "  每周复盘  ",
            accentColor = 0xFFC9B8E8,
            recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
            targetDate = newWeekday,
        )

        assertEquals("旧周期任务", database.todoDao().findOccurrence(firstId)?.title)
        assertTrue(database.todoDao().observeForDate(boundary).first().isEmpty())
        val edited = database.todoDao().findOccurrence(boundaryId)!!
        assertEquals(boundaryId, edited.id)
        assertEquals(newWeekday, edited.displayDate)
        assertEquals("每周复盘", edited.title)
        assertEquals(0xFFC9B8E8, edited.accentColor)
        assertEquals(RecurrenceFrequency.WEEKLY, repository.recurrenceFor(boundaryId)?.frequency)
        val following = database.todoDao().observeForDate(newWeekday.plusWeeks(1)).first().single()
        assertEquals("每周复盘", following.title)
        assertEquals(0xFFC9B8E8, following.accentColor)
    }

    @Test
    fun movingThisAndFutureKeepsIdentityAndMovesTheWeeklyAnchor() = runTest {
        repository.createTodo(
            title = "周计划",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
        )
        val oldBoundary = date.plusWeeks(1)
        val boundaryId = database.todoDao().observeForDate(oldBoundary).first().single().id
        val newBoundary = oldBoundary.plusDays(2)

        val moved = repository.moveTodo(
            boundaryId,
            newBoundary,
            TodoChangeScope.THIS_AND_FUTURE,
        )

        assertEquals(true, moved)
        assertEquals(0, database.todoDao().observeForDate(oldBoundary).first().size)
        assertEquals(boundaryId, database.todoDao().observeForDate(newBoundary).first().single().id)
        assertEquals(1, database.todoDao().observeForDate(newBoundary.plusWeeks(1)).first().size)
        assertEquals(1, database.todoDao().observeForDate(date).first().size)
    }

    @Test
    fun reminderEditThisAndFuturePreservesPastReminderAndUpdatesNewSeries() = runTest {
        val firstId = repository.createTodo(
            title = "吃药",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminders = listOf(TodoReminderSpec(localTime = LocalTime.of(8, 0))),
        )
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id

        repository.setReminders(
            boundaryId,
            listOf(
                TodoReminderSpec(localTime = LocalTime.of(12, 0)),
                TodoReminderSpec(localTime = LocalTime.of(22, 0)),
            ),
            TodoChangeScope.THIS_AND_FUTURE,
        )

        val futureId = database.todoDao().observeForDate(boundary.plusDays(1)).first().single().id
        assertEquals(
            listOf(LocalTime.of(8, 0)),
            database.todoDao().observeReminders(firstId).first().map { it.localTime },
        )
        assertEquals(
            listOf(LocalTime.of(12, 0), LocalTime.of(22, 0)),
            database.todoDao().observeReminders(boundaryId).first().map { it.localTime },
        )
        assertEquals(
            listOf(LocalTime.of(12, 0), LocalTime.of(22, 0)),
            database.todoDao().observeReminders(futureId).first().map { it.localTime },
        )
    }

    @Test
    fun activeReminderProjectionTracksCompletionDeletionMoveAndTimeChanges() = runTest {
        val todoId = repository.createTodo(
            title = "明天下午买牙膏",
            date = date,
            reminders = listOf(TodoReminderSpec(localTime = LocalTime.of(15, 0))),
        )

        val initial = database.todoDao().observeActiveReminders().first().single()
        assertEquals(todoId, initial.occurrenceId)
        assertEquals(date, initial.displayDate)
        assertEquals(LocalTime.of(15, 0), initial.localTime)

        repository.toggleCompletion(todoId)
        assertTrue(database.todoDao().observeActiveReminders().first().isEmpty())

        repository.toggleCompletion(todoId)
        assertEquals(todoId, database.todoDao().observeActiveReminders().first().single().occurrenceId)

        val movedDate = date.plusDays(2)
        repository.moveTodo(todoId, movedDate)
        assertEquals(movedDate, database.todoDao().observeActiveReminders().first().single().displayDate)

        repository.setReminders(
            todoId,
            listOf(TodoReminderSpec(dayOffset = -1, localTime = LocalTime.of(21, 30))),
        )
        val changed = database.todoDao().observeActiveReminders().first().single()
        assertEquals(-1, changed.dayOffset)
        assertEquals(LocalTime.of(21, 30), changed.localTime)

        repository.deleteTodo(todoId)
        assertTrue(database.todoDao().observeActiveReminders().first().isEmpty())
    }

    @Test
    fun editingProjectionExposesCurrentRecurrenceAndReminderTimes() = runTest {
        val todoId = repository.createTodo(
            title = "每日服药",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminders = listOf(
                TodoReminderSpec(localTime = LocalTime.of(8, 0)),
                TodoReminderSpec(localTime = LocalTime.of(20, 30)),
            ),
        )

        assertEquals(RecurrenceFrequency.DAILY, repository.recurrenceFor(todoId)?.frequency)
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
            repository.remindersFor(todoId).map { it.localTime },
        )
    }

    @Test
    fun mixedHomeOrderPersistsAcrossTodoAndHabitFacts() = runTest {
        val firstTodo = repository.createTodo("第一条", date)
        val secondTodo = repository.createTodo("第二条", date)
        val habitRepository = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC)) { "mixed-habit" }
        val habit = habitRepository.createHabit("锻炼 #健康", 0xFF8FA7E4, date, HabitPeriod.DAILY, 1)

        repository.reorderHomeItems(date, listOf("todo-$firstTodo", "habit-$habit", "todo-$secondTodo"))

        assertEquals(0L, database.todoDao().findOccurrence(firstTodo)!!.position)
        assertEquals(1_024L, database.habitDao().findHabit(habit)!!.position)
        assertEquals(2_048L, database.todoDao().findOccurrence(secondTodo)!!.position)
    }

    @Test
    fun rewrittenAndDistantRecurringInstancesKeepLifeGoalLink() = runTest {
        insertGoal("goal-a", "坚持刷牙")
        repository.createTodo(
            title = "刷牙",
            date = date,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            linkedGoalIds = listOf("goal-a"),
        )
        val boundary = date.plusDays(2)
        val boundaryId = database.todoDao().observeForDate(boundary).first().single().id

        repository.updateTitle(boundaryId, "认真刷牙", TodoChangeScope.THIS_AND_FUTURE)
        val generatedFutureId = database.todoDao().observeForDate(boundary.plusDays(1)).first().single().id
        val distant = boundary.plusDays(200)
        repository.ensureOccurrences(distant)
        val distantId = database.todoDao().observeForDate(distant).first().single().id

        assertEquals(listOf("goal-a"), database.todoDao().activeLinkedGoalIds(generatedFutureId))
        assertEquals(listOf("goal-a"), database.todoDao().activeLinkedGoalIds(distantId))
    }

    private suspend fun insertGoal(id: String, title: String) {
        database.lifeGoalDao().insertGoal(
            LifeGoalEntity(
                id = id,
                title = title,
                note = null,
                type = LifeGoalType.ONE_TIME.name,
                position = if (id == "goal-a") 0 else 1,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
