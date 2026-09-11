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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.ui.DaveCompactDraftCard
import com.fishking.core.ui.DaveCompactHabitCard
import com.fishking.core.ui.DaveCompactTaskCard
import com.fishking.core.ui.DaveTodoQuickOptions
import com.fishking.core.ui.DaveTodoEditScope
import com.fishking.core.ui.DaveWeekDayPanel
import com.fishking.core.ui.DaveHomeSwipeTaskCard
import com.fishking.core.ui.DaveSwipeHabitCard
import com.fishking.core.ui.DaveTaskCompletionRequest
import com.fishking.core.ui.DaveCompletionFlight
import com.fishking.core.ui.DaveHabitCompletionFlight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun WeekHomeScreen(
    navigationToken: Int = 0,
    selectedDate: LocalDate,
    today: LocalDate,
    todosByDate: Map<LocalDate, List<TodoOccurrence>>,
    habitsByWeek: Map<LocalDate, List<HabitWeekItem>>,
    editingHabitId: String?,
    editingHabitDate: LocalDate?,
    onEditHabit: (HabitWeekItem, LocalDate) -> Unit,
    onDeleteHabit: (String) -> Unit,
    habitEditor: @Composable () -> Unit,
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
    onToggleHabit: (String, LocalDate) -> Unit,
    onMoveTodo: (String, LocalDate) -> Unit,
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
    val presentationByDate = remember { mutableStateMapOf<LocalDate, WeekDayPresentation>() }
    var completionScene by remember(monday) { mutableStateOf<WeekCompletionScene?>(null) }
    // Keep frame-by-frame animation values as state objects and read them only
    // inside the small grid/flight composables. Reading delegated floats here
    // invalidated the whole week screen on every frame (including every visible
    // day, editor and drag target), which is especially costly in the two-column
    // layout.
    val completionMotionTime = remember(monday) { mutableFloatStateOf(0f) }
    var completionTarget by remember(monday) { mutableStateOf<WeekEntry?>(null) }
    val completionTargetAlpha = remember(monday) { mutableFloatStateOf(0f) }
    var deletingHabit by remember(monday) { mutableStateOf<HabitWeekItem?>(null) }
    val latestTodosByDate by rememberUpdatedState(todosByDate)
    val latestHabitsByWeek by rememberUpdatedState(habitsByWeek)
    val commitTodo by rememberUpdatedState(onToggleCompletion)
    val commitHabit by rememberUpdatedState(onToggleHabit)
    deletingHabit?.let { habit ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingHabit = null },
            title = { androidx.compose.material3.Text("删除「${habit.title}」？") },
            text = { androidx.compose.material3.Text("此习惯会从主页和习惯页移除；其他习惯不受影响。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteHabit(habit.id)
                    deletingHabit = null
                }) { androidx.compose.material3.Text("删除此习惯") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingHabit = null }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
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
    LaunchedEffect(completionScene) {
        val scene = completionScene ?: return@LaunchedEffect
        completionMotionTime.floatValue = 0f
        completionTarget = null
        completionTargetAlpha.floatValue = 0f
        animate(0f, 1f, animationSpec = tween(WeekCompletionMoveMillis, easing = androidx.compose.animation.core.LinearEasing)) { value, _ ->
            completionMotionTime.floatValue = value
        }
        when (val source = scene.source) {
            is WeekEntry.Todo -> commitTodo(source.value.id)
            is WeekEntry.Habit -> commitHabit(source.value.id, scene.date)
        }
        // As on the single-day screen, hold both slots until the repository
        // acknowledges the actual state. A fixed delay cannot prove a write.
        val acknowledgement = kotlinx.coroutines.withTimeoutOrNull(4_000L) {
            val saved = androidx.compose.runtime.snapshotFlow {
                buildWeekEntries(
                    scene.date,
                    latestTodosByDate[scene.date].orEmpty().filter {
                        it.planScope == com.fishking.core.model.TodoPlanScope.DATE || it.isCompleted
                    },
                    latestHabitsByWeek[com.fishking.core.model.HabitRules.weekStart(scene.date)].orEmpty(),
                ).firstOrNull { it.stableKey == scene.source.stableKey }
            }.first { it == null || it.isComplete != scene.source.isComplete }
            WeekCompletionAcknowledgement(saved)
        }
        if (acknowledgement == null) {
            animate(1f, 0f, animationSpec = tween(260)) { value, _ -> completionMotionTime.floatValue = value }
            completionScene = null
            return@LaunchedEffect
        }
        completionTarget = acknowledgement.entry
        animate(0f, 1f, animationSpec = tween(WeekCompletionRevealMillis)) { value, _ -> completionTargetAlpha.floatValue = value }
        // Paint the fully revealed endpoint, then atomically hand the exact
        // same geometry to live data; never mix old order with a cleared scene.
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            presentationByDate[scene.date] = scene.presentation.transferred(scene.before, scene.source)
            completionScene = null
        }
    }
    com.fishking.core.ui.DaveMonthPaging(listState, !draftVisible && editingId.isBlank() && editingHabitId == null && completionScene == null, onPreviousMonth, onNextMonth)
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
            if (target.second != sourceDate) {
                // A week drag changes its date, not the day page's linear order.
                onMoveTodo(sourceId, target.second)
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
    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        userScrollEnabled = completionScene == null,
        modifier = Modifier
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
                    else com.fishking.core.ui.DaveSwipeTaskCard(todo, { onToggleCompletion(todo.id) },
                        { onStartEditing(todo) }, { onDelete(todo.id) })
                }
            }
        }
        items(dates, key = LocalDate::toEpochDay) { date ->
            val liveEntries = buildWeekEntries(date, todosByDate[date].orEmpty().filter { it.planScope == com.fishking.core.model.TodoPlanScope.DATE || it.isCompleted }, habitsByWeek[com.fishking.core.model.HabitRules.weekStart(date)].orEmpty())
            val dayScene = completionScene?.takeIf { it.date == date }
            val presentation = dayScene?.presentation ?: (presentationByDate[date] ?: WeekDayPresentation()).reconcile(liveEntries)
            val entries = dayScene?.before ?: presentation.applyTo(liveEntries)
            SideEffect {
                if (dayScene == null && presentationByDate[date] != presentation) presentationByDate[date] = presentation
            }
            DaveWeekDayPanel(
                title = date.weekDayTitle(),
                isToday = date == today,
                isCurrentWeek = com.fishking.core.model.HabitRules.weekStart(date) == com.fishking.core.model.HabitRules.weekStart(today),
                onBlankClick = {
                    // Starting a new card is another editor switch: the
                    // ViewModel discards whichever editor was open first.
                    if (!draftVisible && completionScene == null) onStartDraft(date)
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .onGloballyPositioned {
                        // Completion owns the layout during its frame loop; drag
                        // hit targets are unused then. Avoid feeding every
                        // animated re-layout back into snapshot state.
                        if (completionScene == null) dayBounds[date] = it
                    },
            ) {
                if (draftVisible && draftDate == date) {
                    DaveCompactDraftCard(
                        value = draftTitle,
                        accentColor = draftAccent?.let { androidx.compose.ui.graphics.Color(it) },
                        // A blank area creates and reveals the card. It must
                        // not also summon the IME from an easy-to-miss tap.
                        autoFocus = false,
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
                if (editingHabitId != null && editingHabitDate == date) habitEditor()
                val gridEntries = entries.filterNot {
                    (it is WeekEntry.Todo && it.value.id == editingId) ||
                        (it is WeekEntry.Habit && it.value.id == editingHabitId && date == editingHabitDate)
                }
                WeekCompletionGrid(
                    entries = gridEntries,
                    scene = dayScene,
                    motionTime = completionMotionTime,
                    target = if (dayScene == null) null else completionTarget,
                    targetAlpha = completionTargetAlpha,
                ) { entry, gridModifier ->
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
                    onToggleHabit = { id -> onToggleHabit(id, date) },
                    onEditHabit = { habit -> onEditHabit(habit, date) },
                    onDeleteHabit = { habit -> deletingHabit = habit },
                    onCompletionRequest = { request ->
                        if (completionScene == null) {
                            // Reset before ownership changes, not one frame
                            // later in LaunchedEffect after a previous scene.
                            completionMotionTime.floatValue = 0f
                            completionTargetAlpha.floatValue = 0f
                            completionTarget = null
                            val before = gridEntries
                            // Freeze both geometries before any repository
                            // write. Completion inserts beside the divider;
                            // undo is its geometric mirror.
                            completionScene = WeekCompletionScene(
                                date = date,
                                source = entry,
                                before = before,
                                request = request,
                                presentation = presentation,
                            )
                        }
                    },
                    // An open editor must not lock the rest of the week. Only
                    // the short completion transfer owns all card gestures.
                    interactionsEnabled = completionScene == null,
                    completionStampInitiallyVisible = completionTarget?.stableKey == entry.stableKey &&
                        completionTarget?.isComplete == true,
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
                                modifier = gridModifier
                                    .onGloballyPositioned { coordinates ->
                                        if (completionScene != null) return@onGloballyPositioned
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
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
    completionScene?.let { scene ->
        WeekCompletionFlightLayer(scene, viewportBounds, completionMotionTime)
    }
    }
}

@Composable
private fun WeekCompletionFlightLayer(
    scene: WeekCompletionScene,
    viewportBounds: Rect,
    motionTime: androidx.compose.runtime.FloatState,
) {
    val flightProgress = WeekCompletionFrame.at(motionTime.floatValue).flight
    when (val source = scene.source) {
        is WeekEntry.Todo -> DaveCompletionFlight(
            todo = source.value,
            request = scene.request,
            containerBounds = viewportBounds,
            progress = flightProgress,
            compact = true,
            clipToSlot = true,
        )
        is WeekEntry.Habit -> DaveHabitCompletionFlight(
            title = source.value.title,
            count = source.count,
            targetCount = source.value.targetCount,
            period = source.value.period,
            color = source.value.color,
            isBackfilled = false,
            checkedOnDate = source.checkedOnDate,
            intervalDays = source.value.intervalDays,
            request = scene.request,
            containerBounds = viewportBounds,
            progress = flightProgress,
            completing = !source.isComplete,
            compact = true,
            clipToSlot = true,
        )
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
            accentColor = todo.accentColor?.let { androidx.compose.ui.graphics.Color(it) },
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
    onToggleHabit: (String) -> Unit,
    onEditHabit: (HabitWeekItem) -> Unit,
    onDeleteHabit: (HabitWeekItem) -> Unit,
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
    onCompletionRequest: (DaveTaskCompletionRequest) -> Unit,
    interactionsEnabled: Boolean,
    completionStampInitiallyVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
    when (entry) {
        is WeekEntry.Todo -> if (editingId == entry.value.id) {
            Column(modifier = cardModifier) {
                DaveCompactDraftCard(
                    value = editingTitle,
                    accentColor = entry.value.accentColor?.let { androidx.compose.ui.graphics.Color(it) },
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
                    selectedAccent = entry.value.accentColor,
                    onAccentSelected = { onSetAccentColor(entry.value.id, it) },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
        } else DaveHomeSwipeTaskCard(
                todo = entry.value,
                onToggleCompletion = { onToggleCompletion(entry.value.id) },
                onCompletionRequest = onCompletionRequest,
                interactionsEnabled = interactionsEnabled,
                completionStampInitiallyVisible = completionStampInitiallyVisible,
                compact = true,
                clipCompletionToSlot = true,
                onEdit = { onStartEditing(entry.value) },
                onDelete = { onDelete(entry.value.id) },
                onDragFinished = { point -> onMoveTodo(entry.value.id, point) },
                onDragPosition = onDragPosition,
                dragGroup = "home-week|${entry.value.displayDate}",
                modifier = cardModifier,
            )
        is WeekEntry.Habit -> DaveSwipeHabitCard(
            title = entry.value.title,
            count = entry.count,
            targetCount = entry.value.targetCount,
            period = entry.value.period,
            color = entry.value.color,
            isBackfilled = false,
            intervalDays = entry.value.intervalDays,
            checkedOnDate = entry.checkedOnDate,
            onClick = { onToggleHabit(entry.value.id) },
            onEdit = { onEditHabit(entry.value) },
            onDelete = { onDeleteHabit(entry.value) },
            dragId = weekHabitDragId(entry.value.id, displayDate),
            dragGroup = "home-week|$displayDate",
            onDragFinished = null,
            onCompletionRequest = onCompletionRequest,
            completionCrossesDivider = entry.willCrossCompletionOnTap(),
            interactionsEnabled = interactionsEnabled,
            completionStampInitiallyVisible = completionStampInitiallyVisible,
            compact = true,
            clipCompletionToSlot = true,
            modifier = cardModifier,
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

/**
 * Per-day hand-off state. Week packing is independent of single-day order;
 * Room is toggled once after the local flight/packing transition has settled.
 */
private data class WeekCompletionScene(
    val date: LocalDate,
    val source: WeekEntry,
    val before: List<WeekEntry>,
    val request: DaveTaskCompletionRequest,
    val presentation: WeekDayPresentation,
) {
    // Both directions insert beside the divider. The source's *target*
    // completion flag determines which half owns this one destination slot.
    val after: List<WeekEntry>
        get() = before.filter { !it.isComplete && it.stableKey != source.stableKey } + source +
            before.filter { it.isComplete && it.stableKey != source.stableKey }
}

/** Distinguishes a confirmed disappearance from an actual observation timeout. */
private data class WeekCompletionAcknowledgement(val entry: WeekEntry?)

/**
 * A packed grid with invisible relocation, not cross-column tile travel.
 * Unchanged cards remain visible; changed cards switch slots only at zero alpha.
 * The source appears at the destination only after persistence acknowledges it.
 */
@Composable
private fun WeekCompletionGrid(
    entries: List<WeekEntry>,
    scene: WeekCompletionScene?,
    motionTime: androidx.compose.runtime.FloatState,
    target: WeekEntry?,
    targetAlpha: androidx.compose.runtime.FloatState,
    content: @Composable (WeekEntry, Modifier) -> Unit,
) {
    val sourceEntries = scene?.before ?: entries
    // Packing plans are immutable during a flight. Rebuilding their maps on
    // every animation frame created avoidable allocation and contributed to
    // the lower-frame-rate feel reported on the two-column week view.
    val before = remember(sourceEntries) { weekGridPlan(sourceEntries) }
    val after = remember(scene, before) {
        scene?.let { weekGridPlan(it.after, it.source.stableKey) } ?: before
    }
    Layout(
        modifier = Modifier.fillMaxWidth().clipToBounds(),
        content = {
            sourceEntries.forEach { entry ->
                key(entry.stableKey) {
                    val source = scene?.source?.stableKey == entry.stableKey
                    val changesSlot = before.slots[entry.stableKey] != after.slots[entry.stableKey]
                    content(
                        if (source && target != null) target else entry,
                        Modifier.graphicsLayer {
                            val frame = WeekCompletionFrame.at(if (scene == null) 0f else motionTime.floatValue)
                            alpha = when {
                                source -> if (target == null) 0f else targetAlpha.floatValue
                                changesSlot -> frame.changedTileAlpha
                                else -> 1f
                            }
                        },
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    val progress = WeekCompletionFrame.at(if (scene == null) 0f else motionTime.floatValue).layoutProgress
                    alpha = when {
                        before.hasDivider && after.hasDivider -> 1f
                        after.hasDivider -> progress
                        before.hasDivider -> 1f - progress
                        else -> 0f
                    }
                },
                color = com.fishking.core.ui.DavePalette.Ink.copy(alpha = .22f),
            )
        },
    ) { measurables, constraints ->
        // Reading the clock in measure invalidates placement only. The card
        // subtree no longer recomposes on every animation frame.
        val frame = WeekCompletionFrame.at(if (scene == null) 0f else motionTime.floatValue)
        val progress = frame.layoutProgress
        val width = constraints.maxWidth
        val gap = 6.dp.roundToPx()
        val cardWidth = ((width - gap) / 2).coerceAtLeast(0)
        val cardHeight = 72.dp.roundToPx()
        val cards = measurables.take(sourceEntries.size).map { it.measure(Constraints.fixed(cardWidth, cardHeight)) }
        val divider = measurables.last().measure(Constraints.fixed(width, 1.dp.roundToPx().coerceAtLeast(1)))
        fun interpolate(start: Float, end: Float) = start + (end - start) * progress
        val height = interpolate(before.height, after.height).dp.roundToPx()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            sourceEntries.forEachIndexed { index, entry ->
                val from = before.slots.getValue(entry.stableKey)
                val to = after.slots.getValue(entry.stableKey)
                val source = scene?.source?.stableKey == entry.stableKey
                // The source proxy flies above the list. Only its destination
                // copy is painted here, never a duplicate at the source slot.
                val slot = if (source || frame.useDestinationSlots) to else from
                cards[index].placeRelative(slot.column * (cardWidth + gap), slot.top.dp.roundToPx())
            }
            divider.placeRelative(0, interpolate(before.dividerTop, after.dividerTop).dp.roundToPx())
        }
    }
}

internal val WeekEntry.stableKey: String
    get() = when (this) {
        is WeekEntry.Todo -> "todo-${value.id}"
        is WeekEntry.Habit -> "habit-${value.id}"
    }

internal val WeekEntry.isComplete: Boolean
    get() = when (this) {
        is WeekEntry.Todo -> value.isCompleted
        is WeekEntry.Habit -> isComplete
    }

private fun WeekEntry.Habit.willCrossCompletionOnTap(): Boolean {
    val nextComplete = when (value.period) {
        HabitPeriod.DAILY -> if (count >= value.targetCount) false else count + 1 >= value.targetCount
        else -> !checkedOnDate
    }
    return nextComplete != isComplete
}

internal data class WeekRow(val entries: List<WeekEntry>, val startsCompletedSection: Boolean = false)

internal fun buildWeekRows(entries: List<WeekEntry>, completedLeadKey: String? = null): List<WeekRow> {
    val (completed, open) = entries.partition {
        when (it) {
            is WeekEntry.Todo -> it.value.isCompleted
            is WeekEntry.Habit -> it.isComplete
        }
    }
    val orderedCompleted = completed.sortedBy { if (it.stableKey == completedLeadKey) 0 else 1 }
    return open.chunked(2).map { WeekRow(it) } + orderedCompleted.chunked(2).mapIndexed { index, items ->
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
