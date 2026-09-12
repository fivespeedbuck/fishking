package com.fishking.feature.home

import androidx.lifecycle.SavedStateHandle
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitWeekSnapshot
import com.fishking.core.model.RecurrenceRule
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoReminderSpec
import com.fishking.core.model.TodoStatus
import com.fishking.core.usecase.HabitRepository
import com.fishking.core.usecase.HomeRepository
import com.fishking.core.usecase.LifeRepository
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun notificationPermissionIsRequestedOnlyWhenAddingOnAndroid13OrNewer() {
        assertTrue(notificationPermissionNeededForAddingReminder(33, permissionGranted = false, isAlreadySelected = false))
        assertFalse(notificationPermissionNeededForAddingReminder(32, permissionGranted = false, isAlreadySelected = false))
        assertFalse(notificationPermissionNeededForAddingReminder(35, permissionGranted = true, isAlreadySelected = false))
        assertFalse(notificationPermissionNeededForAddingReminder(35, permissionGranted = false, isAlreadySelected = true))
    }

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingADateEnsuresTheWholeVisibleWeek() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)

        viewModel.selectDate(LocalDate.of(2026, 9, 3))
        advanceUntilIdle()

        assertEquals(
            (0L..6L).map { LocalDate.of(2026, 8, 31).plusDays(it) },
            home.ensuredDates,
        )
    }

    @Test
    fun weekBlankCreatesTheDraftOnTheClickedDate() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        val friday = LocalDate.of(2026, 9, 4)

        viewModel.startDraft(friday)
        viewModel.updateDraft("周五日程")
        viewModel.confirmDraft()
        advanceUntilIdle()

        assertEquals(friday, home.createdDate)
        assertEquals("周五日程", home.createdTitle)
    }

    @Test
    fun crossDayDragUsesRepositoryMoveSemantics() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        val target = LocalDate.of(2026, 9, 6)

        viewModel.moveTodo("todo-1", target)
        advanceUntilIdle()

        assertEquals("todo-1" to target, home.lastMove)
    }

    @Test
    fun directCardTimeEditPreservesOtherReminderAndDayOffset() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        home.currentReminders = listOf(
            com.fishking.core.model.TodoReminder("r1", "todo-1", -1, java.time.LocalTime.of(21, 0), 0, true, Instant.EPOCH),
            com.fishking.core.model.TodoReminder("r2", "todo-1", 0, java.time.LocalTime.of(22, 0), 1, true, Instant.EPOCH),
        )
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        viewModel.setPrimaryReminderTime("todo-1", java.time.LocalTime.of(20, 30))
        advanceUntilIdle()
        assertEquals(-1, home.savedReminders[0].dayOffset)
        assertEquals(java.time.LocalTime.of(20, 30), home.savedReminders[0].localTime)
        assertEquals(java.time.LocalTime.of(22, 0), home.savedReminders[1].localTime)
        assertEquals(TodoChangeScope.ONLY_THIS, home.reminderScope)
    }

    @Test
    fun weekModeLoadsWholeMonthsAndAdjacentMonths() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        viewModel.resetWeekMonths(LocalDate.of(2026, 9, 6))
        advanceUntilIdle()
        assertEquals((1..30).map { LocalDate.of(2026, 9, it) }, home.ensuredDates)
        viewModel.loadPreviousMonth()
        viewModel.loadNextMonth()
        advanceUntilIdle()
        assertTrue(LocalDate.of(2026, 8, 1) in home.ensuredDates)
        assertTrue(LocalDate.of(2026, 10, 31) in home.ensuredDates)
        assertEquals(92, home.ensuredDates.distinct().size)
    }

    @Test
    fun dayModeDoesNotSubscribeToWholeMonthData() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val habits = RecordingHabitRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, habits, EmptyLifeRepository)
        val todoCollector = launch { viewModel.weekTodos.collect() }
        val habitCollector = launch { viewModel.weekHabits.collect() }

        advanceUntilIdle()
        assertTrue(home.observedDates.isEmpty())
        assertTrue(habits.observedWeeks.isEmpty())

        viewModel.setWeekViewActive(true, LocalDate.of(2026, 9, 6))
        advanceUntilIdle()
        assertEquals(30, home.observedDates.distinct().size)
        assertEquals(5, habits.observedWeeks.distinct().size)

        viewModel.setWeekViewActive(false, LocalDate.of(2026, 9, 6))
        advanceUntilIdle()
        todoCollector.cancelAndJoin()
        habitCollector.cancelAndJoin()
    }

    @Test
    fun futureWeekTodoCanBeEditedWithoutChangingSelectedDate() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        val future = LocalDate.now().plusDays(3)
        viewModel.startEditing(sampleTodo().copy(displayDate = future, nominalDate = future))
        advanceUntilIdle()
        viewModel.updateEditingTitle("未来安排")
        viewModel.confirmEditing()
        advanceUntilIdle()
        assertEquals("todo-1" to "未来安排", home.lastTitleEdit)
        assertEquals("", viewModel.editingId.value)
    }

    @Test
    fun editingScopeIsAppliedWithoutRewritingUnchangedRecurrence() = runTest(dispatcher) {
        val home = RecordingHomeRepository().apply {
            currentRecurrence = com.fishking.core.model.RecurrenceFrequency.DAILY
        }
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)

        viewModel.startEditing(sampleTodo())
        advanceUntilIdle()
        viewModel.setEditingScope(TodoChangeScope.THIS_AND_FUTURE)
        viewModel.updateEditingTitle("本次以后都做")
        viewModel.confirmEditing()
        advanceUntilIdle()

        assertEquals(TodoChangeScope.THIS_AND_FUTURE, home.titleScope)
        assertEquals(TodoChangeScope.THIS_AND_FUTURE, home.reminderScope)
        assertEquals(0, home.recurrenceUpdateCount)
    }

    @Test
    fun editingColourPreviewsImmediatelyButOnlyConfirmationPersistsIt() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        val todo = sampleTodo().copy(accentColor = 1L)
        viewModel.startEditing(todo)
        advanceUntilIdle()
        viewModel.setAccentColor(todo.id, 2L)
        assertEquals(2L, viewModel.editingAccentColor.value)
        advanceUntilIdle()
        assertEquals(null, home.lastAccentEdit)
        viewModel.cancelEditing()
        viewModel.startEditing(todo)
        assertEquals(1L, viewModel.editingAccentColor.value)
        viewModel.setAccentColor(todo.id, 3L)
        viewModel.setEditingScope(TodoChangeScope.THIS_AND_FUTURE)
        advanceUntilIdle()
        viewModel.confirmEditing()
        advanceUntilIdle()
        assertEquals(Triple(todo.id, 3L, TodoChangeScope.THIS_AND_FUTURE), home.lastAccentEdit)
    }

    @Test
    fun switchingEditorsDiscardsThePreviousTodoDraft() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        val first = sampleTodo()
        val second = sampleTodo().copy(id = "todo-2", title = "第二张卡")

        viewModel.startEditing(first)
        viewModel.updateEditingTitle("不应保存的旧草稿")
        viewModel.setAccentColor(first.id, 42L)
        viewModel.startEditing(second)
        advanceUntilIdle()

        assertEquals(second.id, viewModel.editingId.value)
        assertEquals(second.title, viewModel.editingTitle.value)
        assertEquals(Long.MIN_VALUE, viewModel.editingAccentColor.value)
        assertEquals(null, home.lastTitleEdit)
        assertEquals(null, home.lastAccentEdit)
    }

    @Test
    fun operatingAnotherCardKeepsTheCurrentEditorOpen() = runTest(dispatcher) {
        val home = RecordingHomeRepository()
        val viewModel = HomeViewModel(SavedStateHandle(), home, EmptyHabitRepository, EmptyLifeRepository)
        val editing = sampleTodo()

        viewModel.startEditing(editing)
        viewModel.updateEditingTitle("仍在编辑")
        viewModel.toggleCompletion("todo-2")
        advanceUntilIdle()

        assertEquals("todo-2", home.lastCompletionToggle)
        assertEquals(editing.id, viewModel.editingId.value)
        assertEquals("仍在编辑", viewModel.editingTitle.value)
    }

    @Test
    fun startingAnotherDraftAndReturningToDateScopeClearOldDeadline() = runTest(dispatcher) {
        val viewModel = HomeViewModel(SavedStateHandle(), RecordingHomeRepository(), EmptyHabitRepository, EmptyLifeRepository)
        val date = LocalDate.of(2026, 9, 11)
        viewModel.startDraft(date)
        viewModel.setDeadline(date.plusDays(5))
        assertEquals("DEADLINE", viewModel.draftPlanScope.value)
        viewModel.setPlanScope(com.fishking.core.model.TodoPlanScope.DATE)
        assertEquals("", viewModel.draftDeadline.value)
        viewModel.setDeadline(date.plusDays(10))
        viewModel.cancelDraft()
        viewModel.startDraft(date.plusDays(1))
        assertEquals("DATE", viewModel.draftPlanScope.value)
        assertEquals("", viewModel.draftDeadline.value)
    }

    @Test
    fun cancelEditingAndRecurringDateScopeCannotKeepOldDeadline() = runTest(dispatcher) {
        val viewModel = HomeViewModel(SavedStateHandle(), RecordingHomeRepository(), EmptyHabitRepository, EmptyLifeRepository)
        viewModel.startEditing(sampleTodo().copy(planScope = com.fishking.core.model.TodoPlanScope.DEADLINE, planDeadline = LocalDate.of(2026, 9, 20)))
        viewModel.setEditingRecurrence(com.fishking.core.model.RecurrenceFrequency.DAILY)
        assertEquals("DATE", viewModel.editingPlanScope.value)
        assertEquals("", viewModel.editingDeadline.value)
        viewModel.setDeadline(LocalDate.of(2026, 9, 21))
        viewModel.cancelEditing()
        assertEquals("DATE", viewModel.editingPlanScope.value)
        assertEquals("", viewModel.editingDeadline.value)
        advanceUntilIdle()
    }
}

private class RecordingHomeRepository : HomeRepository {
    val ensuredDates = mutableListOf<LocalDate>()
    val observedDates = mutableListOf<LocalDate>()
    var createdDate: LocalDate? = null
    var createdTitle: String? = null
    var lastMove: Pair<String, LocalDate>? = null
    var currentRecurrence: com.fishking.core.model.RecurrenceFrequency? = null
    var titleScope: TodoChangeScope? = null
    var reminderScope: TodoChangeScope? = null
    var recurrenceUpdateCount: Int = 0
    var reorderedIds: List<String> = emptyList()
    var lastTitleEdit: Pair<String, String>? = null
    var lastAccentEdit: Triple<String, Long?, TodoChangeScope>? = null
    var currentReminders = emptyList<com.fishking.core.model.TodoReminder>()
    var savedReminders = emptyList<TodoReminderSpec>()
    var lastCompletionToggle: String? = null
    override suspend fun remindersFor(occurrenceId: String) = currentReminders

    override fun observeTodos(date: LocalDate): Flow<List<TodoOccurrence>> {
        observedDates += date
        return flowOf(emptyList())
    }
    override fun observeLinkedGoalIds(occurrenceId: String): Flow<List<String>> = flowOf(emptyList())

    override suspend fun createTodo(
        title: String,
        date: LocalDate,
        recurrence: RecurrenceRule,
        reminders: List<TodoReminderSpec>,
        linkedGoalIds: List<String>,
        planScope: com.fishking.core.model.TodoPlanScope,
        planDeadline: LocalDate?,
    ): String {
        createdTitle = title
        createdDate = date
        return "todo-1"
    }

    override suspend fun ensureOccurrences(date: LocalDate) {
        ensuredDates += date
    }

    override suspend fun toggleCompletion(occurrenceId: String) {
        lastCompletionToggle = occurrenceId
    }
    override suspend fun togglePriority(occurrenceId: String) = Unit
    override suspend fun reorderTodos(date: LocalDate, orderedIds: List<String>) {
        reorderedIds = orderedIds
    }
    override suspend fun toggleGoalLink(occurrenceId: String, goalId: String): Boolean = true
    override suspend fun updateTitle(occurrenceId: String, title: String, scope: TodoChangeScope) {
        titleScope = scope
        lastTitleEdit = occurrenceId to title
    }
    override suspend fun setAccentColor(occurrenceId: String, accentColor: Long?, scope: TodoChangeScope) {
        lastAccentEdit = Triple(occurrenceId, accentColor, scope)
    }
    override suspend fun setReminders(occurrenceId: String, reminders: List<TodoReminderSpec>, scope: TodoChangeScope) {
        reminderScope = scope
        savedReminders = reminders
    }
    override suspend fun recurrenceFor(occurrenceId: String) = currentRecurrence?.let { RecurrenceRule(it) }
    override suspend fun updateRecurrence(occurrenceId: String, recurrence: RecurrenceRule) {
        recurrenceUpdateCount++
    }

    override suspend fun moveTodo(occurrenceId: String, targetDate: LocalDate, scope: TodoChangeScope): Boolean {
        lastMove = occurrenceId to targetDate
        return true
    }

    override suspend fun deleteTodo(occurrenceId: String, scope: TodoChangeScope) = Unit
    override suspend fun stopRecurrence(occurrenceId: String) = Unit
}

private fun sampleTodo() = TodoOccurrence(
    id = "todo-1",
    nominalDate = LocalDate.of(2026, 9, 4),
    displayDate = LocalDate.of(2026, 9, 4),
    title = "原来的标题",
    status = TodoStatus.OPEN,
    position = 0L,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

private object EmptyHabitRepository : HabitRepository {
    override fun observeWeek(weekStart: LocalDate): Flow<List<HabitWeekItem>> = flowOf(emptyList())
    override fun observeTimeline(currentDate: LocalDate): Flow<List<HabitWeekSnapshot>> = flowOf(emptyList())
    override suspend fun createHabit(
        title: String,
        color: Long,
        startDate: LocalDate,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int>,
    ): String = "habit"
    override suspend fun updateHabit(
        habitId: String,
        effectiveFromWeek: LocalDate,
        title: String,
        color: Long,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int>,
    ) = Unit
    override suspend fun toggleCheckIn(habitId: String, date: LocalDate): Int? = null
    override suspend fun toggleWeekSkip(habitId: String, weekStart: LocalDate): Boolean? = null
    override suspend fun endHabitFromWeek(habitId: String, weekStart: LocalDate) = Unit
}

private class RecordingHabitRepository : HabitRepository {
    val observedWeeks = mutableListOf<LocalDate>()

    override fun observeWeek(weekStart: LocalDate): Flow<List<HabitWeekItem>> {
        observedWeeks += weekStart
        return flowOf(emptyList())
    }
    override fun observeTimeline(currentDate: LocalDate): Flow<List<HabitWeekSnapshot>> = flowOf(emptyList())
    override suspend fun createHabit(
        title: String,
        color: Long,
        startDate: LocalDate,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int>,
    ): String = "habit"
    override suspend fun updateHabit(
        habitId: String,
        effectiveFromWeek: LocalDate,
        title: String,
        color: Long,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int>,
    ) = Unit
    override suspend fun toggleCheckIn(habitId: String, date: LocalDate): Int? = null
    override suspend fun toggleWeekSkip(habitId: String, weekStart: LocalDate): Boolean? = null
    override suspend fun endHabitFromWeek(habitId: String, weekStart: LocalDate) = Unit
}

private object EmptyLifeRepository : LifeRepository {
    override fun observeGoals(): Flow<List<LifeGoalWithEvents>> = flowOf(emptyList())
    override suspend fun createGoal(title: String, note: String?, type: LifeGoalType): String = "goal"
    override suspend fun updateGoal(goalId: String, title: String, note: String?, type: LifeGoalType, accentColor: Long?) = Unit
    override suspend fun toggleManualResult(goalId: String, occurredOn: LocalDate): String? = null
    override suspend fun deleteManualEvent(eventId: String) = Unit
    override suspend fun setPosition(goalId: String, position: Long) = Unit
    override suspend fun reorderGoals(goalIds: List<String>) = Unit
    override suspend fun deleteGoal(goalId: String) = Unit
    override suspend fun addToDate(goalId: String, date: LocalDate): String? = null
}
