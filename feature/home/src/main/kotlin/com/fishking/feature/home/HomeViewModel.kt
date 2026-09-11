package com.fishking.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.RecurrenceRule
import com.fishking.core.model.TodoReminderSpec
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.HabitRules
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.usecase.HomeRepository
import com.fishking.core.usecase.HabitRepository
import com.fishking.core.usecase.LifeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HomeRepository,
    private val habitRepository: HabitRepository,
    lifeRepository: LifeRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private var editingLoadJob: Job? = null

    val todos: StateFlow<List<TodoOccurrence>> = selectedDate
        .flatMapLatest(repository::observeTodos)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habits: StateFlow<List<HabitWeekItem>> = selectedDate
        .flatMapLatest { date -> habitRepository.observeWeek(HabitRules.weekStart(date)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val visibleMonths = MutableStateFlow(java.time.YearMonth.now() to java.time.YearMonth.now())
    val weekTodos: StateFlow<Map<LocalDate, List<TodoOccurrence>>> = visibleMonths
        .flatMapLatest { months ->
            val dates = monthDates(months)
            combine(dates.map(repository::observeTodos)) { values ->
                dates.zip(values.toList()).toMap()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weekHabits: StateFlow<Map<LocalDate, List<HabitWeekItem>>> = visibleMonths.flatMapLatest { months ->
        val weeks = monthDates(months).map(HabitRules::weekStart).distinct()
        combine(weeks.map(habitRepository::observeWeek)) { values -> weeks.zip(values.toList()).toMap() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun resetWeekMonths(date: LocalDate) {
        val month = java.time.YearMonth.from(date)
        visibleMonths.value = month to month
        ensureMonth(month)
    }
    fun loadPreviousMonth() {
        val month = visibleMonths.value.first.minusMonths(1)
        visibleMonths.value = month to visibleMonths.value.second
        ensureMonth(month)
    }
    fun loadNextMonth() {
        val month = visibleMonths.value.second.plusMonths(1)
        visibleMonths.value = visibleMonths.value.first to month
        ensureMonth(month)
    }
    private fun monthDates(months: Pair<java.time.YearMonth, java.time.YearMonth>): List<LocalDate> =
        generateSequence(months.first.atDay(1)) { it.plusDays(1).takeUnless { date -> date.isAfter(months.second.atEndOfMonth()) } }.toList()

    private fun ensureMonth(month: java.time.YearMonth) {
        viewModelScope.launch { (1..month.lengthOfMonth()).forEach { repository.ensureOccurrences(month.atDay(it)) } }
    }

    val draftTitle = savedStateHandle.getStateFlow(DRAFT_TITLE, "")
    val draftPlanScope = savedStateHandle.getStateFlow("draftPlanScope", "DATE")
    val editingPlanScope = savedStateHandle.getStateFlow("editingPlanScope", "DATE")
    val draftDeadline = savedStateHandle.getStateFlow("draftDeadline", "")
    val editingDeadline = savedStateHandle.getStateFlow("editingDeadline", "")
    fun setDeadline(date: LocalDate) {
        savedStateHandle[if (editingId.value.isNotBlank()) "editingDeadline" else "draftDeadline"] = date.toString()
        setPlanScope(com.fishking.core.model.TodoPlanScope.DEADLINE)
    }
    fun setPlanScope(scope: com.fishking.core.model.TodoPlanScope) {
        if (editingId.value.isNotBlank()) {
            savedStateHandle["editingPlanScope"] = scope.name
            if (scope != com.fishking.core.model.TodoPlanScope.DEADLINE) savedStateHandle["editingDeadline"] = ""
            if (scope != com.fishking.core.model.TodoPlanScope.DATE) savedStateHandle[EDITING_RECURRENCE] = RecurrenceFrequency.ONCE.name
        } else {
            savedStateHandle["draftPlanScope"] = scope.name
            if (scope != com.fishking.core.model.TodoPlanScope.DEADLINE) savedStateHandle["draftDeadline"] = ""
            if (scope != com.fishking.core.model.TodoPlanScope.DATE) savedStateHandle[DRAFT_RECURRENCE] = RecurrenceFrequency.ONCE.name
        }
    }
    val draftVisible = savedStateHandle.getStateFlow(DRAFT_VISIBLE, false)
    val draftRecurrence = savedStateHandle.getStateFlow(DRAFT_RECURRENCE, RecurrenceFrequency.ONCE.name)
    val draftReminderTimes = savedStateHandle.getStateFlow(DRAFT_REMINDERS, "")
    val draftAccentColor = savedStateHandle.getStateFlow(DRAFT_ACCENT, NO_ACCENT)
    val draftGoalIds = savedStateHandle.getStateFlow(DRAFT_GOAL_IDS, "")
    val editingId = savedStateHandle.getStateFlow(EDITING_ID, "")
    val editingTitle = savedStateHandle.getStateFlow(EDITING_TITLE, "")
    val editingAccentColor = savedStateHandle.getStateFlow(EDITING_ACCENT, NO_ACCENT)
    val editingRecurrence = savedStateHandle.getStateFlow(EDITING_RECURRENCE, RecurrenceFrequency.ONCE.name)
    val editingReminderTimes = savedStateHandle.getStateFlow(EDITING_REMINDERS, "")
    val editingScope = savedStateHandle.getStateFlow(EDITING_SCOPE, TodoChangeScope.ONLY_THIS.name)
    val habitEditingId = MutableStateFlow<String?>(null)
    val habitEditingDate = MutableStateFlow<LocalDate?>(null)
    val habitEditingTitle = MutableStateFlow("")
    val habitEditingPeriod = MutableStateFlow(HabitPeriod.DAILY)
    val habitEditingTarget = MutableStateFlow(1)
    val habitEditingIntervalDays = MutableStateFlow(DEFAULT_HABIT_INTERVAL_DAYS)
    val habitEditingScheduleStartDate = MutableStateFlow(LocalDate.now())
    val habitEditingScheduleDays = MutableStateFlow<Set<Int>>(emptySet())
    val habitEditingColor = MutableStateFlow(DEFAULT_HABIT_COLOR)
    val draftDateEpochDay = savedStateHandle.getStateFlow(DRAFT_DATE, LocalDate.now().toEpochDay())
    val goals: StateFlow<List<LifeGoalWithEvents>> = lifeRepository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val editingGoalIds: StateFlow<List<String>> = editingId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repository.observeLinkedGoalIds(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        if (selectedDate.value != date) {
            cancelDraft()
            cancelEditing()
            cancelHabitEditing()
        }
        selectedDate.value = date
        viewModelScope.launch {
            val monday = HabitRules.weekStart(date)
            (0L..6L).forEach { offset -> repository.ensureOccurrences(monday.plusDays(offset)) }
        }
    }

    fun startDraft(date: LocalDate = selectedDate.value) {
        savedStateHandle["draftPlanScope"] = "DATE"
        savedStateHandle["draftDeadline"] = ""
        cancelEditing()
        cancelHabitEditing()
        savedStateHandle[DRAFT_DATE] = date.toEpochDay()
        savedStateHandle[DRAFT_TITLE] = ""
        savedStateHandle[DRAFT_VISIBLE] = true
        savedStateHandle[DRAFT_RECURRENCE] = RecurrenceFrequency.ONCE.name
        savedStateHandle[DRAFT_REMINDERS] = ""
        savedStateHandle[DRAFT_ACCENT] = NO_ACCENT
        savedStateHandle[DRAFT_GOAL_IDS] = ""
    }

    fun updateDraft(value: String) {
        savedStateHandle[DRAFT_TITLE] = value.replace('\n', ' ')
    }

    fun setDraftDate(date: LocalDate) {
        savedStateHandle[DRAFT_DATE] = date.toEpochDay()
    }

    fun cancelDraft() {
        savedStateHandle[DRAFT_VISIBLE] = false
        savedStateHandle[DRAFT_TITLE] = ""
        savedStateHandle["draftPlanScope"] = "DATE"
        savedStateHandle["draftDeadline"] = ""
    }

    fun cancelDraftIfBlank() {
        if (draftTitle.value.isBlank()) cancelDraft()
    }

    fun confirmDraft() {
        val title = draftTitle.value.trim()
        if (title.isEmpty()) return
        val date = LocalDate.ofEpochDay(draftDateEpochDay.value)
        viewModelScope.launch {
            val frequency = RecurrenceFrequency.valueOf(draftRecurrence.value)
            val reminders = ReminderDrafts.decode(draftReminderTimes.value)
            val id = repository.createTodo(
                title = title,
                date = date,
                recurrence = RecurrenceRule(frequency),
                reminders = reminders,
                linkedGoalIds = draftGoalIds.value.split(',').filter(String::isNotBlank),
                planScope = com.fishking.core.model.TodoPlanScope.valueOf(draftPlanScope.value),
                planDeadline = draftDeadline.value.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            )
            draftAccentColor.value.takeUnless { it == NO_ACCENT }?.let { repository.setAccentColor(id, it) }
            cancelDraft()
        }
    }

    fun setDraftAccentColor(value: Long?) {
        savedStateHandle[DRAFT_ACCENT] = value ?: NO_ACCENT
    }

    fun toggleDraftGoal(goalId: String) {
        val values = draftGoalIds.value.split(',').filter(String::isNotBlank).toMutableSet()
        if (!values.add(goalId)) values.remove(goalId)
        savedStateHandle[DRAFT_GOAL_IDS] = values.joinToString(",")
    }

    fun setDraftRecurrence(value: RecurrenceFrequency) {
        if (value != RecurrenceFrequency.ONCE) {
            savedStateHandle["draftPlanScope"] = "DATE"
            savedStateHandle["draftDeadline"] = ""
        }
        savedStateHandle[DRAFT_RECURRENCE] = value.name
    }

    fun toggleDraftReminder(time: LocalTime) {
        val hasTime = ReminderDrafts.decode(draftReminderTimes.value).any { it.localTime == time }
        savedStateHandle[DRAFT_REMINDERS] = ReminderDrafts.replace(draftReminderTimes.value, if (hasTime) time else null, if (hasTime) null else time)
    }
    fun removeDraftReminder(time: LocalTime) {
        savedStateHandle[DRAFT_REMINDERS] = ReminderDrafts.replace(draftReminderTimes.value, time, null)
    }
    fun replaceDraftReminder(previous: LocalTime?, replacement: LocalTime, dayOffset: Int? = null) {
        savedStateHandle[DRAFT_REMINDERS] = ReminderDrafts.replace(draftReminderTimes.value, previous, replacement, dayOffset)
    }

    fun startEditing(todo: TodoOccurrence) {
        // There is only one editor. Switching cards explicitly discards the
        // previous local draft and cancels its asynchronous metadata load.
        cancelEditing()
        savedStateHandle["editingPlanScope"] = todo.planScope.name
        savedStateHandle["editingDeadline"] = todo.planDeadline?.toString().orEmpty()
        cancelDraft()
        cancelHabitEditing()
        savedStateHandle[EDITING_ID] = todo.id
        savedStateHandle[EDITING_TITLE] = todo.title
        savedStateHandle[EDITING_ACCENT] = todo.accentColor ?: NO_ACCENT
        savedStateHandle[EDITING_RECURRENCE] = RecurrenceFrequency.ONCE.name
        savedStateHandle[EDITING_REMINDERS] = ""
        savedStateHandle[EDITING_SCOPE] = TodoChangeScope.ONLY_THIS.name
        editingLoadJob = viewModelScope.launch {
            val recurrence = repository.recurrenceFor(todo.id)?.frequency ?: RecurrenceFrequency.ONCE
            val reminders = repository.remindersFor(todo.id).sortedBy { it.position }
                .map { TodoReminderSpec(it.dayOffset, it.localTime, it.position, it.isEnabled) }
            // A slow Room read from the previous card must never overwrite the
            // editor that the user has already switched to.
            if (editingId.value != todo.id) return@launch
            savedStateHandle[EDITING_RECURRENCE] = recurrence.name
            savedStateHandle[EDITING_REMINDERS] = ReminderDrafts.encode(reminders)
            editingLoadJob = null
        }
    }

    fun updateEditingTitle(value: String) {
        savedStateHandle[EDITING_TITLE] = value.replace('\n', ' ')
    }

    fun cancelEditing() {
        editingLoadJob?.cancel()
        editingLoadJob = null
        savedStateHandle[EDITING_ID] = ""
        savedStateHandle[EDITING_TITLE] = ""
        savedStateHandle[EDITING_ACCENT] = NO_ACCENT
        savedStateHandle[EDITING_RECURRENCE] = RecurrenceFrequency.ONCE.name
        savedStateHandle[EDITING_REMINDERS] = ""
        savedStateHandle[EDITING_SCOPE] = TodoChangeScope.ONLY_THIS.name
        savedStateHandle["editingPlanScope"] = "DATE"
        savedStateHandle["editingDeadline"] = ""
    }

    fun cancelEditingIfBlank() {
        if (editingTitle.value.isBlank()) cancelEditing()
    }

    fun confirmEditing() {
        val id = editingId.value
        val title = editingTitle.value.trim()
        if (id.isEmpty() || title.isEmpty()) return
        val accent = editingAccentColor.value.takeUnless { it == NO_ACCENT }
        viewModelScope.launch {
            val scope = TodoChangeScope.valueOf(editingScope.value)
            val selectedRecurrence = RecurrenceFrequency.valueOf(editingRecurrence.value)
            repository.updateTitle(id, title, scope)
            repository.setAccentColor(id, accent, scope)
            val currentRecurrence = repository.recurrenceFor(id)?.frequency ?: RecurrenceFrequency.ONCE
            if (currentRecurrence != selectedRecurrence) {
                repository.updateRecurrence(id, RecurrenceRule(selectedRecurrence))
            }
            repository.setPlanScope(id, com.fishking.core.model.TodoPlanScope.valueOf(editingPlanScope.value), editingDeadline.value.takeIf { it.isNotBlank() }?.let(LocalDate::parse))
            repository.setReminders(
                id,
                ReminderDrafts.decode(editingReminderTimes.value),
                scope = scope,
            )
            cancelEditing()
        }
    }

    fun setEditingScope(value: TodoChangeScope) {
        savedStateHandle[EDITING_SCOPE] = value.name
    }

    fun stopEditingRecurrence() {
        val id = editingId.value.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            repository.stopRecurrence(id)
            cancelEditing()
        }
    }

    fun setEditingRecurrence(value: RecurrenceFrequency) {
        if (value != RecurrenceFrequency.ONCE) {
            savedStateHandle["editingPlanScope"] = "DATE"
            savedStateHandle["editingDeadline"] = ""
        }
        savedStateHandle[EDITING_RECURRENCE] = value.name
    }

    fun toggleEditingReminder(time: LocalTime) {
        val hasTime = ReminderDrafts.decode(editingReminderTimes.value).any { it.localTime == time }
        savedStateHandle[EDITING_REMINDERS] = ReminderDrafts.replace(editingReminderTimes.value, if (hasTime) time else null, if (hasTime) null else time)
    }
    fun removeEditingReminder(time: LocalTime) {
        savedStateHandle[EDITING_REMINDERS] = ReminderDrafts.replace(editingReminderTimes.value, time, null)
    }
    fun replaceEditingReminder(previous: LocalTime?, replacement: LocalTime, dayOffset: Int? = null) {
        savedStateHandle[EDITING_REMINDERS] = ReminderDrafts.replace(editingReminderTimes.value, previous, replacement, dayOffset)
    }

    fun toggleCompletion(id: String) {
        viewModelScope.launch { repository.toggleCompletion(id) }
    }

    fun togglePriority(id: String) {
        viewModelScope.launch { repository.togglePriority(id) }
    }

    fun toggleEditingGoal(goalId: String) {
        val occurrenceId = editingId.value.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch { repository.toggleGoalLink(occurrenceId, goalId) }
    }

    fun setAccentColor(id: String, color: Long?) {
        if (editingId.value == id) {
            // Colour is part of this edit draft: preview synchronously, persist
            // with the same confirmation/scope as its title, discard on cancel.
            savedStateHandle[EDITING_ACCENT] = color ?: NO_ACCENT
        } else viewModelScope.launch { repository.setAccentColor(id, color) }
    }

    fun setPrimaryReminderTime(id: String, time: LocalTime, dayOffset: Int? = null) {
        viewModelScope.launch {
            val reminders = repository.remindersFor(id).sortedWith(compareBy({ it.dayOffset }, { it.localTime }))
            val firstEnabled = reminders.indexOfFirst { it.isEnabled }
            val specs = reminders.mapIndexed { index, reminder ->
                TodoReminderSpec(if (index == firstEnabled) dayOffset ?: reminder.dayOffset else reminder.dayOffset,
                    if (index == firstEnabled) time else reminder.localTime, reminder.position, reminder.isEnabled)
            }.toMutableList()
            if (firstEnabled < 0) specs.add(TodoReminderSpec(dayOffset = dayOffset ?: 0, localTime = time))
            repository.setReminders(id, specs, TodoChangeScope.ONLY_THIS)
        }
    }

    fun toggleHabit(id: String, date: LocalDate = selectedDate.value) {
        viewModelScope.launch { habitRepository.toggleCheckIn(id, date) }
    }

    fun setHabitColor(habit: HabitWeekItem, color: Long, date: LocalDate = selectedDate.value) {
        val rule = habit.ruleOn(date)
        viewModelScope.launch {
            habitRepository.updateHabitWithSchedule(
                habitId = habit.id,
                effectiveFromDate = date,
                title = rule.title,
                color = color,
                period = rule.period,
                targetCount = rule.targetCount,
                scheduleDays = rule.scheduleDays,
                intervalDays = rule.intervalDays,
                scheduleStartDate = rule.scheduleStartDate,
            )
        }
    }

    fun startEditingHabit(habit: HabitWeekItem) = startEditingHabit(habit, selectedDate.value)

    fun startEditingHabit(habit: HabitWeekItem, date: LocalDate) {
        cancelDraft()
        cancelEditing()
        val rule = habit.ruleOn(date)
        habitEditingId.value = habit.id
        habitEditingDate.value = date
        habitEditingTitle.value = rule.title
        habitEditingPeriod.value = rule.period
        habitEditingTarget.value = rule.targetCount
        habitEditingIntervalDays.value = rule.intervalDays
        habitEditingScheduleStartDate.value = rule.scheduleStartDate
        habitEditingScheduleDays.value = rule.scheduleDays
        habitEditingColor.value = rule.color
    }

    fun updateHabitEditingTitle(value: String) {
        habitEditingTitle.value = value.replace('\n', ' ')
    }

    fun setHabitEditingPeriod(value: HabitPeriod) {
        if (habitEditingPeriod.value == value) return
        habitEditingPeriod.value = value
        habitEditingTarget.value = if (value.isIntervalMode()) {
            1
        } else {
            habitEditingTarget.value.coerceAtMost(value.maximumTargetCount())
        }
        habitEditingScheduleDays.value = emptySet()
    }

    fun setHabitEditingTarget(value: Int) {
        habitEditingTarget.value = value.coerceIn(1, habitEditingPeriod.value.maximumTargetCount())
    }

    fun setHabitEditingIntervalDays(value: Int) {
        habitEditingIntervalDays.value = value.coerceIn(1, MAX_HABIT_INTERVAL_DAYS)
    }

    fun setHabitEditingScheduleStartDate(value: LocalDate) {
        habitEditingScheduleStartDate.value = value
    }

    fun toggleHabitEditingScheduleDay(value: Int) {
        habitEditingScheduleDays.value = habitEditingScheduleDays.value.toMutableSet().apply {
            if (!add(value)) remove(value)
        }
    }

    fun setHabitEditingColor(value: Long?) {
        habitEditingColor.value = value ?: DEFAULT_HABIT_COLOR
    }

    fun cancelHabitEditing() {
        habitEditingId.value = null
        habitEditingDate.value = null
        habitEditingTitle.value = ""
    }

    fun confirmHabitEditing() {
        val habitId = habitEditingId.value ?: return
        val editingDate = habitEditingDate.value ?: selectedDate.value
        val title = habitEditingTitle.value.trim()
        if (title.isEmpty()) return
        if (habitScheduleError(habitEditingPeriod.value, habitEditingTarget.value, habitEditingScheduleDays.value) != null) return
        val effectiveDate = if (habitEditingPeriod.value.isIntervalMode()) {
            minOf(editingDate, habitEditingScheduleStartDate.value)
        } else editingDate
        viewModelScope.launch {
            habitRepository.updateHabitWithSchedule(
                habitId = habitId,
                effectiveFromDate = effectiveDate,
                title = title,
                color = habitEditingColor.value,
                period = habitEditingPeriod.value,
                targetCount = if (habitEditingPeriod.value.isIntervalMode()) 1 else habitEditingTarget.value,
                scheduleDays = if (habitEditingPeriod.value.isIntervalMode()) emptySet() else habitEditingScheduleDays.value,
                intervalDays = habitEditingIntervalDays.value,
                scheduleStartDate = habitEditingScheduleStartDate.value,
                replaceFutureSchedule = true,
            )
            cancelHabitEditing()
        }
    }

    fun deleteHabit(habitId: String) {
        if (habitEditingId.value == habitId) cancelHabitEditing()
        viewModelScope.launch { habitRepository.deleteHabit(habitId) }
    }

    fun moveTodo(id: String, targetDate: LocalDate) {
        viewModelScope.launch { repository.moveTodo(id, targetDate) }
    }

    fun moveTodoRelative(id: String, targetId: String, placeAfterTarget: Boolean) {
        viewModelScope.launch { repository.moveTodoRelative(id, targetId, placeAfterTarget) }
    }

    fun moveTodoToHomePosition(
        id: String,
        targetDate: LocalDate,
        targetKey: String?,
        placeAfterTarget: Boolean,
    ) {
        val dayTodos = if (targetDate == selectedDate.value) todos.value else weekTodos.value[targetDate].orEmpty()
        val week = weekHabits.value[com.fishking.core.model.HabitRules.weekStart(targetDate)].orEmpty()
        val sourceKey = "todo-$id"
        val ordered = buildHomeDisplaySections(targetDate, dayTodos, week).open
            .map { it.stableKey }
            .filterNot { it == sourceKey }
            .toMutableList()
        val targetIndex = targetKey?.let(ordered::indexOf) ?: -1
        val insertionIndex = if (targetIndex >= 0) {
            targetIndex + if (placeAfterTarget) 1 else 0
        } else {
            ordered.size
        }
        ordered.add(insertionIndex.coerceIn(0, ordered.size), sourceKey)
        viewModelScope.launch {
            if (repository.moveTodo(id, targetDate)) {
                repository.reorderHomeItems(targetDate, ordered)
            }
        }
    }

    fun reorderTodoWithinGroup(date: LocalDate, id: String, targetId: String, placeAfterTarget: Boolean) {
        val dayTodos = if (date == selectedDate.value) todos.value else weekTodos.value[date].orEmpty()
        val source = dayTodos.firstOrNull { it.id == id } ?: return
        val target = dayTodos.firstOrNull { it.id == targetId } ?: return
        if (source.status != target.status || source.priority != target.priority) return
        val reordered = dayTodos
            .filter { it.status == source.status && it.priority == source.priority }
            .sortedWith(compareBy<TodoOccurrence> { it.position }.thenBy { it.id })
            .map { it.id }
            .toMutableList()
        if (!reordered.remove(id)) return
        val targetIndex = reordered.indexOf(targetId)
        if (targetIndex < 0) return
        reordered.add((targetIndex + if (placeAfterTarget) 1 else 0).coerceAtMost(reordered.size), id)
        viewModelScope.launch { repository.reorderTodos(date, reordered) }
    }

    fun reorderHomeItem(date: LocalDate, sourceKey: String, targetKey: String, placeAfterTarget: Boolean) {
        if (sourceKey == targetKey) return
        val dayTodos = if (date == selectedDate.value) todos.value else weekTodos.value[date].orEmpty()
        val week = weekHabits.value[com.fishking.core.model.HabitRules.weekStart(date)].orEmpty()
        val ordered = buildHomeDisplaySections(date, dayTodos, week).open.map { it.stableKey }.toMutableList()
        if (!ordered.remove(sourceKey)) return
        val targetIndex = ordered.indexOf(targetKey)
        if (targetIndex < 0) return
        ordered.add((targetIndex + if (placeAfterTarget) 1 else 0).coerceIn(0, ordered.size), sourceKey)
        viewModelScope.launch { repository.reorderHomeItems(date, ordered) }
    }

    fun deleteTodo(id: String) {
        if (editingId.value == id) cancelEditing()
        viewModelScope.launch { repository.deleteTodo(id) }
    }

    companion object {
        private const val DRAFT_TITLE = "home_draft_title"
        private const val DRAFT_VISIBLE = "home_draft_visible"
        private const val DRAFT_RECURRENCE = "home_draft_recurrence"
        private const val DRAFT_REMINDERS = "home_draft_reminders"
        private const val DRAFT_ACCENT = "home_draft_accent"
        private const val DRAFT_GOAL_IDS = "home_draft_goal_ids"
        private const val EDITING_ID = "home_editing_id"
        private const val EDITING_TITLE = "home_editing_title"
        private const val EDITING_ACCENT = "home_editing_accent"
        private const val EDITING_RECURRENCE = "home_editing_recurrence"
        private const val EDITING_REMINDERS = "home_editing_reminders"
        private const val EDITING_SCOPE = "home_editing_scope"
        private const val DRAFT_DATE = "home_draft_date"
        private const val NO_ACCENT = Long.MIN_VALUE
        private const val DEFAULT_HABIT_COLOR = 0xFF8FA7E4L
        private const val DEFAULT_HABIT_INTERVAL_DAYS = 3
        private const val MAX_HABIT_INTERVAL_DAYS = 365

        fun factory(
            repository: HomeRepository,
            habitRepository: HabitRepository,
            lifeRepository: LifeRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(createSavedStateHandle(), repository, habitRepository, lifeRepository) }
        }
    }
}

private fun habitScheduleError(period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>): String? {
    if (scheduleDays.isEmpty() || period == HabitPeriod.DAILY || period.isIntervalMode()) return null
    return when {
        period == HabitPeriod.WEEKLY && scheduleDays.size < targetCount -> "weekly schedule is smaller than target"
        period == HabitPeriod.MONTHLY && scheduleDays.size < targetCount -> "monthly schedule is smaller than target"
        else -> null
    }
}

private fun HabitPeriod.maximumTargetCount(): Int = when (this) {
    HabitPeriod.DAILY -> 99
    HabitPeriod.WEEKLY -> 7
    HabitPeriod.MONTHLY -> 31
    HabitPeriod.EVERY_N_DAYS,
    HabitPeriod.AFTER_COMPLETION_N_DAYS,
    -> 1
}

private fun HabitPeriod.isIntervalMode(): Boolean =
    this == HabitPeriod.EVERY_N_DAYS || this == HabitPeriod.AFTER_COMPLETION_N_DAYS
