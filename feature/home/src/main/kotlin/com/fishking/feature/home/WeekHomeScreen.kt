package com.fishking.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.ui.DaveCompactDraftCard
import com.fishking.core.ui.DaveCompactHabitCard
import com.fishking.core.ui.DaveCompactTaskCard
import com.fishking.core.ui.DaveTodoQuickOptions
import com.fishking.core.ui.DaveAccentPalette
import com.fishking.core.ui.DaveTodoEditScope
import com.fishking.core.ui.DaveWeekDayPanel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun WeekHomeScreen(
    navigationToken: Int = 0,
    selectedDate: LocalDate,
    today: LocalDate,
    todosByDate: Map<LocalDate, List<TodoOccurrence>>,
    habitsByWeek: Map<LocalDate, List<HabitWeekItem>>,
    draftVisible: Boolean,
    draftDate: LocalDate,
    draftTitle: String,
    draftRecurrence: RecurrenceFrequency,
    draftReminderTimes: List<LocalTime>,
    draftAccent: Long?,
    draftGoalIds: Set<String>,
    editingId: String,
    editingTitle: String,
    editingRecurrence: RecurrenceFrequency,
    editingReminderTimes: List<LocalTime>,
    editingScope: TodoChangeScope,
    goals: List<com.fishking.core.model.LifeGoalWithEvents>,
    editingGoalIds: Set<String>,
    onStartDraft: (LocalDate) -> Unit,
    onDraftChange: (String) -> Unit,
    onConfirmDraft: () -> Unit,
    onCancelEmptyDraft: () -> Unit,
    onDraftAccentSelected: (Long?) -> Unit,
    onToggleDraftGoal: (String) -> Unit,
    onDraftRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onDraftReminderToggled: (LocalTime) -> Unit,
    onDraftReminderAdd: () -> Unit,
    onDraftReminderEdit: (LocalTime) -> Unit,
    onDraftReminderRemove: (LocalTime) -> Unit,
    onStartEditing: (TodoOccurrence) -> Unit,
    onEditingTitleChange: (String) -> Unit,
    onConfirmEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onEditingRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onEditingReminderToggled: (LocalTime) -> Unit,
    onEditingReminderAdd: () -> Unit,
    onEditingReminderEdit: (LocalTime) -> Unit,
    onEditingReminderRemove: (LocalTime) -> Unit,
    onEditingScopeSelected: (TodoChangeScope) -> Unit,
    onStopRecurrence: () -> Unit,
    onToggleEditingGoal: (String) -> Unit,
    onSetAccentColor: (String, Long?) -> Unit,
    onDelete: (String) -> Unit,
    onToggleCompletion: (String) -> Unit,
    onTogglePriority: (String) -> Unit,
    onToggleHabit: (String, LocalDate) -> Unit,
    onMoveTodo: (String, LocalDate) -> Unit,
    onMoveTodoToHomePosition: (String, LocalDate, String?, Boolean) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monday = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1L)
    val dates = remember(todosByDate.keys) { todosByDate.keys.sorted() }
    val ongoingPlans = remember(todosByDate) {
        todosByDate.values.flatten()
            .filter { it.planScope != com.fishking.core.model.TodoPlanScope.DATE && !it.isCompleted }
            .distinctBy { it.id }
    }
    val listState = rememberLazyListState()
    var positionedToken by remember(selectedDate) { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(navigationToken, selectedDate, dates) {
        val index = dates.indexOf(selectedDate)
        if (index >= 0 && positionedToken != navigationToken) {
            // The range-plan summary is a real lazy-list item. Account for it so
            // calendar "today" navigation always lands on the requested day.
            listState.scrollToItem(index + if (ongoingPlans.isNotEmpty()) 1 else 0)
            positionedToken = navigationToken
        }
    }
    androidx.compose.runtime.LaunchedEffect(draftVisible, draftDate, monday) {
        if (draftVisible) {
            val index = dates.indexOf(draftDate)
            if (index >= 0) listState.animateScrollToItem(index + if (ongoingPlans.isNotEmpty()) 1 else 0)
        }
    }
    val dayBounds = remember(monday) { mutableStateMapOf<LocalDate, androidx.compose.ui.layout.LayoutCoordinates>() }
    val entryBounds = remember(monday) {
        mutableStateMapOf<String, Triple<String, LocalDate, androidx.compose.ui.layout.LayoutCoordinates>>()
    }
    var viewportBounds by remember { mutableStateOf(Rect.Zero) }
    val scrollScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dropHitMargin = with(density) { 28.dp.toPx() }
    com.fishking.core.ui.DaveMonthPaging(listState, !draftVisible && editingId.isBlank(), onPreviousMonth, onNextMonth)
    fun finishWeekDrag(sourceDate: LocalDate, sourceId: String, point: Offset) {
        val targetEntry = entryBounds.entries
            .asSequence()
            .filter { (dragId, value) -> dragId != sourceId && value.second in dates && value.third.isAttached }
            .filter { (_, value) ->
                val rect = value.third.boundsInRoot()
                Rect(rect.left - dropHitMargin, rect.top - dropHitMargin, rect.right + dropHitMargin, rect.bottom + dropHitMargin)
                    .contains(point)
            }
            .minByOrNull { (_, value) -> (value.third.boundsInRoot().center - point).getDistance() }
        if (targetEntry != null) {
            val target = targetEntry.value
            val after = point.y >= target.third.boundsInRoot().center.y
            if (target.second != sourceDate) {
                onMoveTodoToHomePosition(sourceId, target.second, target.first, after)
            }
            return
        }
        val targetDate = dayBounds.entries.firstOrNull { (date, coordinates) ->
            date in dates && coordinates.isAttached && coordinates.boundsInRoot().contains(point)
        }?.key
        when {
            targetDate != null && targetDate != sourceDate -> onMoveTodo(sourceId, targetDate)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { viewportBounds = it.boundsInRoot() },
    ) {
        if (ongoingPlans.isNotEmpty()) item(key = "ongoing-plans") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Text("进行中的范围待办", color = com.fishking.core.ui.DavePalette.Ink)
                ongoingPlans.forEach { todo ->
                    if (editingId == todo.id) WeekTodoEditor(todo, editingTitle, editingRecurrence, editingReminderTimes,
                        editingScope, goals, editingGoalIds, onEditingTitleChange, onConfirmEditing, onCancelEditing,
                        onEditingRecurrenceSelected, onEditingReminderToggled, onEditingReminderAdd, onEditingReminderEdit,
                        onEditingReminderRemove, onEditingScopeSelected, onStopRecurrence, onToggleEditingGoal, onSetAccentColor)
                    else com.fishking.core.ui.DaveSwipeTaskCard(todo, { onToggleCompletion(todo.id) }, { onTogglePriority(todo.id) },
                        { onStartEditing(todo) }, { onDelete(todo.id) })
                }
            }
        }
        items(dates, key = LocalDate::toEpochDay) { date ->
            val entries = buildWeekEntries(date, todosByDate[date].orEmpty().filter { it.planScope == com.fishking.core.model.TodoPlanScope.DATE || it.isCompleted }, habitsByWeek[com.fishking.core.model.HabitRules.weekStart(date)].orEmpty())
            DaveWeekDayPanel(
                title = date.weekDayTitle(),
                isToday = date == today,
                isCurrentWeek = com.fishking.core.model.HabitRules.weekStart(date) == com.fishking.core.model.HabitRules.weekStart(today),
                onBlankClick = { if (!draftVisible) onStartDraft(date) },
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .onGloballyPositioned { dayBounds[date] = it },
            ) {
                if (draftVisible && draftDate == date) {
                    DaveCompactDraftCard(
                        value = draftTitle,
                        onValueChange = onDraftChange,
                        onConfirm = onConfirmDraft,
                        onCancelEmpty = onCancelEmptyDraft,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    DaveTodoQuickOptions(
                        recurrence = draftRecurrence,
                        reminderTimes = draftReminderTimes,
                        onRecurrenceSelected = onDraftRecurrenceSelected,
                        onReminderToggled = onDraftReminderToggled,
                        onReminderAdd = onDraftReminderAdd,
                        onReminderEdit = onDraftReminderEdit,
                        onReminderRemove = onDraftReminderRemove,
                        selectedAccent = draftAccent,
                        onAccentSelected = onDraftAccentSelected,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                val editingEntry = entries.filterIsInstance<WeekEntry.Todo>().firstOrNull { it.value.id == editingId }
                if (editingEntry != null) {
                    WeekTodoEditor(
                        todo = editingEntry.value,
                        editingTitle = editingTitle,
                        editingRecurrence = editingRecurrence,
                        editingReminderTimes = editingReminderTimes,
                        editingScope = editingScope,
                        goals = goals,
                        editingGoalIds = editingGoalIds,
                        onEditingTitleChange = onEditingTitleChange,
                        onConfirmEditing = onConfirmEditing,
                        onCancelEditing = onCancelEditing,
                        onEditingRecurrenceSelected = onEditingRecurrenceSelected,
                        onEditingReminderToggled = onEditingReminderToggled,
                        onEditingReminderAdd = onEditingReminderAdd,
                        onEditingReminderEdit = onEditingReminderEdit,
                        onEditingReminderRemove = onEditingReminderRemove,
                        onEditingScopeSelected = onEditingScopeSelected,
                        onStopRecurrence = onStopRecurrence,
                        onToggleEditingGoal = onToggleEditingGoal,
                        onSetAccentColor = onSetAccentColor,
                    )
                }
                buildWeekRows(entries.filterNot { it is WeekEntry.Todo && it.value.id == editingId }).forEach { row ->
                    if (row.startsCompletedSection) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = com.fishking.core.ui.DavePalette.Ink.copy(alpha = .22f),
                        )
                    }
                    val rowEntries = row.entries
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        rowEntries.forEachIndexed { index, entry ->
                WeekEntryCard(
                    displayDate = date,
                    entry = entry,
                    editingId = editingId,
                    editingTitle = editingTitle,
                    editingRecurrence = editingRecurrence,
                    editingReminderTimes = editingReminderTimes,
                    editingScope = editingScope,
                    goals = goals,
                    editingGoalIds = editingGoalIds,
                    onToggleCompletion = onToggleCompletion,
                    onTogglePriority = onTogglePriority,
                    onToggleHabit = { id -> onToggleHabit(id, date) },
                    onStartEditing = onStartEditing,
                    onEditingTitleChange = onEditingTitleChange,
                    onConfirmEditing = onConfirmEditing,
                    onCancelEditing = onCancelEditing,
                    onEditingRecurrenceSelected = onEditingRecurrenceSelected,
                    onEditingReminderToggled = onEditingReminderToggled,
                    onEditingReminderAdd = onEditingReminderAdd,
                    onEditingReminderEdit = onEditingReminderEdit,
                    onEditingReminderRemove = onEditingReminderRemove,
                    onEditingScopeSelected = onEditingScopeSelected,
                    onStopRecurrence = onStopRecurrence,
                    onToggleEditingGoal = onToggleEditingGoal,
                    onSetAccentColor = onSetAccentColor,
                    onDelete = onDelete,
                                onMoveTodo = { id, point -> finishWeekDrag(date, id, point) },
                                onDragPosition = { point ->
                                    val edge = with(density) { 72.dp.toPx() }
                                    when {
                                        viewportBounds != Rect.Zero && point.y < viewportBounds.top + edge && listState.canScrollBackward ->
                                            scrollScope.launch { listState.scrollBy(-24f) }
                                        viewportBounds != Rect.Zero && point.y > viewportBounds.bottom - edge && listState.canScrollForward ->
                                            scrollScope.launch { listState.scrollBy(24f) }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coordinates ->
                                        val dragId = when (entry) {
                                            is WeekEntry.Todo -> entry.value.id
                                            is WeekEntry.Habit -> weekHabitDragId(entry.value.id, date)
                                        }
                                        val stableKey = when (entry) {
                                            is WeekEntry.Todo -> "todo-${entry.value.id}"
                                            is WeekEntry.Habit -> "habit-${entry.value.id}"
                                        }
                                        entryBounds[dragId] = Triple(stableKey, date, coordinates)
                                    },
                            )
                            if (index == 0) Spacer(Modifier.width(6.dp))
                        }
                        if (rowEntries.size == 1) {
                            Spacer(Modifier.width(6.dp))
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun WeekTodoEditor(
    todo: TodoOccurrence,
    editingTitle: String,
    editingRecurrence: RecurrenceFrequency,
    editingReminderTimes: List<LocalTime>,
    editingScope: TodoChangeScope,
    goals: List<com.fishking.core.model.LifeGoalWithEvents>,
    editingGoalIds: Set<String>,
    onEditingTitleChange: (String) -> Unit,
    onConfirmEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onEditingRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onEditingReminderToggled: (LocalTime) -> Unit,
    onEditingReminderAdd: () -> Unit,
    onEditingReminderEdit: (LocalTime) -> Unit,
    onEditingReminderRemove: (LocalTime) -> Unit,
    onEditingScopeSelected: (TodoChangeScope) -> Unit,
    onStopRecurrence: () -> Unit,
    onToggleEditingGoal: (String) -> Unit,
    onSetAccentColor: (String, Long?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        DaveCompactDraftCard(
            value = editingTitle,
            autoFocus = false,
            onValueChange = onEditingTitleChange,
            onConfirm = onConfirmEditing,
            onCancelEmpty = onCancelEditing,
        )
        DaveTodoQuickOptions(
            recurrence = editingRecurrence,
            editingTitle = editingTitle,
            onEditingTitleChange = onEditingTitleChange,
            reminderTimes = editingReminderTimes,
            onRecurrenceSelected = onEditingRecurrenceSelected,
            onReminderToggled = onEditingReminderToggled,
            onReminderAdd = onEditingReminderAdd,
            onReminderEdit = onEditingReminderEdit,
            onReminderRemove = onEditingReminderRemove,
            selectedAccent = todo.accentColor,
            onAccentSelected = { onSetAccentColor(todo.id, it) },
            modifier = Modifier.padding(horizontal = 0.dp),
        )
    }
}

@Composable
private fun WeekEntryCard(
    displayDate: LocalDate,
    entry: WeekEntry,
    editingId: String,
    editingTitle: String,
    editingRecurrence: RecurrenceFrequency,
    editingReminderTimes: List<LocalTime>,
    editingScope: TodoChangeScope,
    goals: List<com.fishking.core.model.LifeGoalWithEvents>,
    editingGoalIds: Set<String>,
    onToggleCompletion: (String) -> Unit,
    onTogglePriority: (String) -> Unit,
    onToggleHabit: (String) -> Unit,
    onStartEditing: (TodoOccurrence) -> Unit,
    onEditingTitleChange: (String) -> Unit,
    onConfirmEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onEditingRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onEditingReminderToggled: (LocalTime) -> Unit,
    onEditingReminderAdd: () -> Unit,
    onEditingReminderEdit: (LocalTime) -> Unit,
    onEditingReminderRemove: (LocalTime) -> Unit,
    onEditingScopeSelected: (TodoChangeScope) -> Unit,
    onStopRecurrence: () -> Unit,
    onToggleEditingGoal: (String) -> Unit,
    onSetAccentColor: (String, Long?) -> Unit,
    onDelete: (String) -> Unit,
    onMoveTodo: (String, Offset) -> Unit,
    onDragPosition: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is WeekEntry.Todo -> if (editingId == entry.value.id) {
            Column(modifier = modifier) {
                DaveCompactDraftCard(
                    value = editingTitle,
                    autoFocus = false,
                    onValueChange = onEditingTitleChange,
                    onConfirm = onConfirmEditing,
                    onCancelEmpty = onCancelEditing,
                )
                DaveAccentPalette(
                    selected = entry.value.accentColor,
                    onSelected = { onSetAccentColor(entry.value.id, it) },
                    modifier = Modifier.padding(top = 3.dp),
                )
                DaveTodoQuickOptions(
                    recurrence = editingRecurrence,
                    editingTitle = editingTitle,
                    onEditingTitleChange = onEditingTitleChange,
                    reminderTimes = editingReminderTimes,
                    onRecurrenceSelected = onEditingRecurrenceSelected,
                    onReminderToggled = onEditingReminderToggled,
                    onReminderAdd = onEditingReminderAdd,
                    onReminderEdit = onEditingReminderEdit,
                    onReminderRemove = onEditingReminderRemove,
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
        } else DaveCompactTaskCard(
                todo = entry.value,
                onToggleCompletion = { onToggleCompletion(entry.value.id) },
                onDragFinished = { point -> onMoveTodo(entry.value.id, point) },
                onDragPosition = onDragPosition,
                dragGroup = "home-week|${entry.value.displayDate}",
                onEdit = { onStartEditing(entry.value) },
                onDelete = { onDelete(entry.value.id) },
                onTogglePriority = { onTogglePriority(entry.value.id) },
                modifier = modifier,
            )
        is WeekEntry.Habit -> DaveCompactHabitCard(
            title = entry.value.title,
            count = entry.count,
            targetCount = entry.value.targetCount,
            period = entry.value.period,
            color = entry.value.color,
            intervalDays = entry.value.intervalDays,
            checkedOnDate = entry.checkedOnDate,
            onClick = { onToggleHabit(entry.value.id) },
            dragId = weekHabitDragId(entry.value.id, displayDate),
            dragGroup = "home-week|$displayDate",
            onDragFinished = {},
            modifier = modifier,
        )
    }
}

internal fun buildWeekEntries(
    date: LocalDate,
    todos: List<TodoOccurrence>,
    habits: List<HabitWeekItem>,
): List<WeekEntry> {
    val sections = buildHomeDisplaySections(date, todos, habits)
    return (sections.open + sections.completed).map { item ->
        when (item) {
            is HomeDisplayItem.Todo -> WeekEntry.Todo(item.value)
            is HomeDisplayItem.Habit -> WeekEntry.Habit(item.value, item.count, item.checkedOnDate, item.isComplete)
        }
    }
}

internal sealed interface WeekEntry {
    data class Todo(val value: TodoOccurrence) : WeekEntry
    data class Habit(
        val value: HabitWeekItem,
        val count: Int,
        val checkedOnDate: Boolean,
        val isComplete: Boolean,
    ) : WeekEntry
}

internal data class WeekRow(val entries: List<WeekEntry>, val startsCompletedSection: Boolean = false)

internal fun buildWeekRows(entries: List<WeekEntry>): List<WeekRow> {
    val (completed, open) = entries.partition {
        when (it) {
            is WeekEntry.Todo -> it.value.isCompleted
            is WeekEntry.Habit -> it.isComplete
        }
    }
    return open.chunked(2).map { WeekRow(it) } + completed.chunked(2).mapIndexed { index, items ->
        WeekRow(items, startsCompletedSection = index == 0)
    }
}

private val weekDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

private fun LocalDate.weekDayTitle(): String = "${dayOfWeek.shortChinese()} · ${format(weekDayFormatter)}"

private fun DayOfWeek.shortChinese(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
