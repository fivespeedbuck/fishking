package com.fishking.feature.home

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.ui.DaveInlineDraftCard
import com.fishking.core.ui.DavePalette
import com.fishking.core.ui.DaveSwipeTaskCard
import com.fishking.core.ui.DaveTodoQuickOptions
import com.fishking.core.ui.DaveTodoEditScope
import com.fishking.core.ui.DaveHabitCard
import com.fishking.core.usecase.HomeRepository
import com.fishking.core.usecase.HabitRepository
import com.fishking.core.usecase.LifeRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    repository: HomeRepository,
    habitRepository: HabitRepository,
    lifeRepository: LifeRepository,
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    weekView: Boolean,
    navigationToken: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository, habitRepository, lifeRepository)),
) {
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val weekTodos by viewModel.weekTodos.collectAsStateWithLifecycle()
    val weekHabits by viewModel.weekHabits.collectAsStateWithLifecycle()
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftVisible by viewModel.draftVisible.collectAsStateWithLifecycle()
    val draftRecurrenceName by viewModel.draftRecurrence.collectAsStateWithLifecycle()
    val draftReminderTimesValue by viewModel.draftReminderTimes.collectAsStateWithLifecycle()
    val draftAccentValue by viewModel.draftAccentColor.collectAsStateWithLifecycle()
    val draftGoalIdsValue by viewModel.draftGoalIds.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val draftPlanScope by viewModel.draftPlanScope.collectAsStateWithLifecycle()
    val editingPlanScope by viewModel.editingPlanScope.collectAsStateWithLifecycle()
    val draftDeadline by viewModel.draftDeadline.collectAsStateWithLifecycle()
    val editingDeadline by viewModel.editingDeadline.collectAsStateWithLifecycle()
    val editingTitle by viewModel.editingTitle.collectAsStateWithLifecycle()
    val editingRecurrenceName by viewModel.editingRecurrence.collectAsStateWithLifecycle()
    val editingReminderTimesValue by viewModel.editingReminderTimes.collectAsStateWithLifecycle()
    val editingScopeName by viewModel.editingScope.collectAsStateWithLifecycle()
    val draftDateEpochDay by viewModel.draftDateEpochDay.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val editingGoalIds by viewModel.editingGoalIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionPrompted = remember {
        mutableStateOf(
            context.getSharedPreferences(PERMISSIONS_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .getBoolean(NOTIFICATION_PERMISSION_PROMPTED, false),
        )
    }
    val draftReminderTimes = ReminderDrafts.decode(draftReminderTimesValue).map { it.localTime }
    val editingReminderTimes = ReminderDrafts.decode(editingReminderTimesValue).map { it.localTime }
    val draftAccent = draftAccentValue.takeUnless { it == Long.MIN_VALUE }
    val draftGoalIds = draftGoalIdsValue.split(',').filter(String::isNotBlank).toSet()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "提醒已保存；通知权限未开启，系统可能不显示提醒", Toast.LENGTH_LONG).show()
    }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun chooseReminder(previous: LocalTime?, editing: Boolean) {
        val date = if (editing) (weekTodos.values.flatten() + todos).firstOrNull { it.id == editingId }?.displayDate ?: selectedDate
            else LocalDate.ofEpochDay(draftDateEpochDay)
        val specs = ReminderDrafts.decode(if (editing) editingReminderTimesValue else draftReminderTimesValue)
        val initial = date.plusDays((specs.firstOrNull { it.localTime == previous }?.dayOffset ?: 0).toLong())
        android.app.DatePickerDialog(context, { _, year, month, day ->
            val reminderDate = LocalDate.of(year, month + 1, day)
            val time = previous ?: LocalTime.now()
            TimePickerDialog(context, { _, hour, minute ->
                val offset = java.time.temporal.ChronoUnit.DAYS.between(date, reminderDate).toInt()
                if (editing) viewModel.replaceEditingReminder(previous, LocalTime.of(hour, minute), offset)
                else viewModel.replaceDraftReminder(previous, LocalTime.of(hour, minute), offset)
                ensureNotificationPermission()
            }, time.hour, time.minute, true).apply { setTitle("通知提醒时间") }.show()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { setTitle("通知提醒日期") }.show()
    }
    val openReminderPicker: (LocalTime?) -> Unit = { chooseReminder(it, false) }
    val openEditingReminderPicker: (LocalTime?) -> Unit = { chooseReminder(it, true) }
    val onDraftReminderToggled: (LocalTime) -> Unit = { viewModel.toggleDraftReminder(it) }
    val onEditingReminderToggled: (LocalTime) -> Unit = { viewModel.toggleEditingReminder(it) }

    LaunchedEffect(selectedDate) { viewModel.selectDate(selectedDate) }
    LaunchedEffect(weekView, selectedDate, navigationToken) { if (weekView) viewModel.resetWeekMonths(selectedDate) }
    LaunchedEffect(weekView) {
        viewModel.cancelDraft()
        viewModel.cancelEditing()
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cancelDraft()
            viewModel.cancelEditing()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.fishking.core.ui.LocalDaveReorderCommit provides { id, target, after ->
            val sourceHabitDrag = parseWeekHabitDragId(id)
            val targetHabitDrag = parseWeekHabitDragId(target)
            val sourceTodo = (weekTodos.values.flatten() + todos).firstOrNull { it.id == id }
            val targetTodo = (weekTodos.values.flatten() + todos).firstOrNull { it.id == target }
            val sourceHabit = (weekHabits.values.flatten() + habits).firstOrNull { it.id == (sourceHabitDrag?.first ?: id) }
            val targetHabit = (weekHabits.values.flatten() + habits).firstOrNull { it.id == (targetHabitDrag?.first ?: target) }
            val sourceDate = sourceTodo?.displayDate ?: sourceHabitDrag?.second ?: selectedDate
            val targetDate = targetTodo?.displayDate ?: targetHabitDrag?.second ?: selectedDate
            val sourceKey = if (sourceTodo != null) "todo-$id" else sourceHabit?.let { "habit-${it.id}" }
            val targetKey = if (targetTodo != null) "todo-$target" else targetHabit?.let { "habit-${it.id}" }
            if (sourceDate == targetDate && sourceKey != null && targetKey != null) {
                viewModel.reorderHomeItem(targetDate, sourceKey, targetKey, after)
            }
        },
        com.fishking.core.ui.LocalTodoPlanning provides com.fishking.core.ui.TodoPlanningEditor(
            com.fishking.core.model.TodoPlanScope.valueOf(if (editingId.isBlank()) draftPlanScope else editingPlanScope), viewModel::setPlanScope,
            (if (editingId.isBlank()) draftDeadline else editingDeadline).takeIf { it.isNotBlank() }?.let(LocalDate::parse), viewModel::setDeadline),
        com.fishking.core.ui.LocalTodoTimeEditor provides { todo ->
        val first = todo.displayReminders.sortedWith(compareBy({ it.dayOffset }, { it.localTime })).firstOrNull()
        val initial = first?.localTime ?: LocalTime.now()
        TimePickerDialog(context, { _, hour, minute ->
            viewModel.setPrimaryReminderTime(todo.id, LocalTime.of(hour, minute))
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }, initial.hour, initial.minute, true).apply { setTitle("本次待办提醒时间") }.show()
    }) {
    if (weekView) WeekHomeScreen(
        navigationToken = navigationToken,
        selectedDate = selectedDate,
        today = LocalDate.now(),
        todosByDate = weekTodos,
        habitsByWeek = weekHabits,
        draftVisible = draftVisible,
        draftDate = LocalDate.ofEpochDay(draftDateEpochDay),
        draftTitle = draftTitle,
        draftRecurrence = RecurrenceFrequency.valueOf(draftRecurrenceName),
        draftReminderTimes = draftReminderTimes,
        draftAccent = draftAccent,
        draftGoalIds = draftGoalIds,
        editingId = editingId,
        editingTitle = editingTitle,
        editingRecurrence = RecurrenceFrequency.valueOf(editingRecurrenceName),
        editingReminderTimes = editingReminderTimes,
        editingScope = TodoChangeScope.valueOf(editingScopeName),
        goals = goals,
        editingGoalIds = editingGoalIds.toSet(),
        onStartDraft = { date -> viewModel.startDraft(date) },
        onDraftChange = viewModel::updateDraft,
        onConfirmDraft = viewModel::confirmDraft,
        onCancelEmptyDraft = viewModel::cancelDraft,
        onDraftAccentSelected = viewModel::setDraftAccentColor,
        onToggleDraftGoal = viewModel::toggleDraftGoal,
        onDraftRecurrenceSelected = viewModel::setDraftRecurrence,
        onDraftReminderToggled = onDraftReminderToggled,
        onDraftReminderAdd = { openReminderPicker(null) },
        onDraftReminderEdit = { openReminderPicker(it) },
        onDraftReminderRemove = viewModel::removeDraftReminder,
        onStartEditing = viewModel::startEditing,
        onEditingTitleChange = viewModel::updateEditingTitle,
        onConfirmEditing = viewModel::confirmEditing,
        onCancelEditing = viewModel::cancelEditing,
        onEditingRecurrenceSelected = viewModel::setEditingRecurrence,
        onEditingReminderToggled = onEditingReminderToggled,
        onEditingReminderAdd = { openEditingReminderPicker(null) },
        onEditingReminderEdit = { openEditingReminderPicker(it) },
        onEditingReminderRemove = viewModel::removeEditingReminder,
        onEditingScopeSelected = viewModel::setEditingScope,
        onStopRecurrence = viewModel::stopEditingRecurrence,
        onToggleEditingGoal = viewModel::toggleEditingGoal,
        onSetAccentColor = viewModel::setAccentColor,
        onDelete = viewModel::deleteTodo,
        onToggleCompletion = viewModel::toggleCompletion,
        onTogglePriority = viewModel::togglePriority,
        onToggleHabit = { id, date -> viewModel.toggleHabit(id, date) },
        onMoveTodo = viewModel::moveTodo,
        onMoveTodoToHomePosition = viewModel::moveTodoToHomePosition,
        onPreviousMonth = viewModel::loadPreviousMonth,
        onNextMonth = viewModel::loadNextMonth,
        modifier = modifier,
    ) else HomeScreen(
        todos = todos,
        habits = habits,
        draftVisible = draftVisible,
        draftTitle = draftTitle,
        draftAccent = draftAccent,
        draftGoalIds = draftGoalIds,
        onStartDraft = { viewModel.startDraft() },
        onDraftChange = viewModel::updateDraft,
        onConfirmDraft = viewModel::confirmDraft,
        onCancelEmptyDraft = viewModel::cancelDraft,
        onDraftAccentSelected = viewModel::setDraftAccentColor,
        onToggleDraftGoal = viewModel::toggleDraftGoal,
        draftRecurrence = RecurrenceFrequency.valueOf(draftRecurrenceName),
        draftReminderTimes = draftReminderTimes,
        onDraftRecurrenceSelected = viewModel::setDraftRecurrence,
        onDraftReminderToggled = onDraftReminderToggled,
        onDraftReminderAdd = { openReminderPicker(null) },
        onDraftReminderEdit = { openReminderPicker(it) },
        onDraftReminderRemove = viewModel::removeDraftReminder,
        editingRecurrence = RecurrenceFrequency.valueOf(editingRecurrenceName),
        editingReminderTimes = editingReminderTimes,
        editingScope = TodoChangeScope.valueOf(editingScopeName),
        onEditingRecurrenceSelected = viewModel::setEditingRecurrence,
        onEditingReminderToggled = viewModel::toggleEditingReminder,
        onEditingReminderAdd = { openEditingReminderPicker(null) },
        onEditingReminderEdit = { openEditingReminderPicker(it) },
        onEditingReminderRemove = viewModel::removeEditingReminder,
        onEditingScopeSelected = viewModel::setEditingScope,
        onStopRecurrence = viewModel::stopEditingRecurrence,
        onToggleCompletion = viewModel::toggleCompletion,
        onToggleHabit = { id -> viewModel.toggleHabit(id) },
        editingId = editingId,
        editingTitle = editingTitle,
        onStartEditing = viewModel::startEditing,
        onEditingTitleChange = viewModel::updateEditingTitle,
        onConfirmEditing = viewModel::confirmEditing,
        onCancelEditing = viewModel::cancelEditing,
        onTogglePriority = viewModel::togglePriority,
        onSetAccentColor = viewModel::setAccentColor,
        goals = goals,
        editingGoalIds = editingGoalIds.toSet(),
        onToggleEditingGoal = viewModel::toggleEditingGoal,
        onDelete = viewModel::deleteTodo,
        onReorderTodo = { id, targetId, after -> viewModel.reorderTodoWithinGroup(selectedDate, id, targetId, after) },
        selectedDate = selectedDate,
        onDateChange = onDateChange,
        onMoveTodo = viewModel::moveTodo,
        modifier = modifier,
    )
    }
}

internal fun notificationPermissionNeededForAddingReminder(
    apiLevel: Int,
    permissionGranted: Boolean,
    isAlreadySelected: Boolean,
): Boolean = apiLevel >= 33 && !permissionGranted && !isAlreadySelected

private data class PendingReminderChange(
    val previous: LocalTime?,
    val replacement: LocalTime,
)

private const val PERMISSIONS_PREFERENCES = "fishking_permissions"
private const val NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted"

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeScreen(
    todos: List<TodoOccurrence>,
    habits: List<HabitWeekItem>,
    draftVisible: Boolean,
    draftTitle: String,
    draftAccent: Long?,
    draftGoalIds: Set<String>,
    onStartDraft: () -> Unit,
    onDraftChange: (String) -> Unit,
    onConfirmDraft: () -> Unit,
    onCancelEmptyDraft: () -> Unit,
    onDraftAccentSelected: (Long?) -> Unit,
    onToggleDraftGoal: (String) -> Unit,
    draftRecurrence: RecurrenceFrequency,
    draftReminderTimes: List<LocalTime>,
    onDraftRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onDraftReminderToggled: (LocalTime) -> Unit,
    onDraftReminderAdd: () -> Unit,
    onDraftReminderEdit: (LocalTime) -> Unit,
    onDraftReminderRemove: (LocalTime) -> Unit,
    editingRecurrence: RecurrenceFrequency,
    editingReminderTimes: List<LocalTime>,
    editingScope: TodoChangeScope,
    onEditingRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onEditingReminderToggled: (LocalTime) -> Unit,
    onEditingReminderAdd: () -> Unit,
    onEditingReminderEdit: (LocalTime) -> Unit,
    onEditingReminderRemove: (LocalTime) -> Unit,
    onEditingScopeSelected: (TodoChangeScope) -> Unit,
    onStopRecurrence: () -> Unit,
    onToggleCompletion: (String) -> Unit,
    onToggleHabit: (String) -> Unit,
    editingId: String,
    editingTitle: String,
    onStartEditing: (TodoOccurrence) -> Unit,
    onEditingTitleChange: (String) -> Unit,
    onConfirmEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onTogglePriority: (String) -> Unit,
    onSetAccentColor: (String, Long?) -> Unit,
    goals: List<LifeGoalWithEvents>,
    editingGoalIds: Set<String>,
    onToggleEditingGoal: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReorderTodo: (String, String, Boolean) -> Unit,
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onMoveTodo: (String, LocalDate) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
    val listState = rememberLazyListState()
    val draftBringIntoViewRequester = remember { BringIntoViewRequester() }
    var draftContentSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(draftVisible) {
        if (draftVisible) listState.animateScrollToItem(0)
    }
    // The text field itself requests relocation when it gains focus, but the
    // quick options are measured afterwards. Re-request the whole draft after
    // its final layout and after the IME inset changes so its controls are not
    // left behind the keyboard.
    LaunchedEffect(draftVisible, imeBottom, draftContentSize) {
        if (draftVisible && draftContentSize != IntSize.Zero) {
            draftBringIntoViewRequester.bringIntoView()
        }
    }
    val currentDateChange by rememberUpdatedState(onDateChange)
    val currentDate by rememberUpdatedState(selectedDate)
    val thresholdPx = with(density) { 56.dp.toPx() }
    val maxPullPx = with(density) { 96.dp.toPx() }
    var edgePull by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var viewportBounds by remember { mutableStateOf(Rect.Zero) }

    val edgeConnection = remember(listState, thresholdPx, maxPullPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && edgePull != 0f && available.y * edgePull < 0f) {
                    val previous = edgePull
                    edgePull = if (abs(available.y) >= abs(previous)) 0f else previous + available.y
                    return Offset(0f, edgePull - previous)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = !listState.canScrollBackward && available.y > 0f
                val atBottom = !listState.canScrollForward && available.y < 0f
                if (atTop || atBottom) {
                    edgePull = (edgePull + available.y * .62f).coerceIn(-maxPullPx, maxPullPx)
                    return available.copy(x = 0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val releasedPull = edgePull
                if (abs(releasedPull) >= thresholdPx) {
                    val direction = if (releasedPull < 0f) 1 else -1
                    val travel = viewportHeightPx.takeIf { it > maxPullPx } ?: maxPullPx * 4f
                    val outgoing = if (direction > 0) -travel else travel
                    animate(
                        initialValue = releasedPull,
                        targetValue = outgoing,
                        animationSpec = tween(145),
                    ) { value, _ -> edgePull = value }
                    currentDateChange(currentDate.plusDays(direction.toLong()))
                    listState.scrollToItem(0)
                    // Give Compose a frame to replace the day's facts, then bring the
                    // new day in from the opposite edge instead of flashing in place.
                    androidx.compose.runtime.withFrameNanos { }
                    edgePull = -outgoing
                }
                animate(
                    initialValue = edgePull,
                    targetValue = 0f,
                    animationSpec = tween(if (abs(releasedPull) >= thresholdPx) 230 else 220),
                ) { value, _ -> edgePull = value }
                return if (releasedPull != 0f) available else Velocity.Zero
            }
        }
    }

    val sections = buildHomeDisplaySections(selectedDate, todos, habits)
    val todoBounds = remember(selectedDate) { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }

    com.fishking.core.ui.DaveDateDropArea(
        date = selectedDate,
        onMove = onMoveTodo,
        modifier = modifier.clipToBounds(),
        onDrop = { _, _ -> false },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                .onGloballyPositioned { viewportBounds = it.boundsInRoot() }
                .nestedScroll(edgeConnection)
                .graphicsLayer { translationY = edgePull },
        ) {
            if (draftVisible) {
                item(key = "draft") {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .bringIntoViewRequester(draftBringIntoViewRequester)
                            .onSizeChanged { draftContentSize = it },
                    ) {
                        DaveInlineDraftCard(
                            value = draftTitle,
                            onValueChange = onDraftChange,
                            onConfirm = onConfirmDraft,
                            onCancelEmpty = onCancelEmptyDraft,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
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
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
            }
            sections.open.forEach { entry ->
                item(key = entry.stableKey) {
                    when (entry) {
                        is HomeDisplayItem.Todo -> HomeTodoEntry(
                            todo = entry.value, editingId = editingId, editingTitle = editingTitle,
                            editingRecurrence = editingRecurrence, editingReminderTimes = editingReminderTimes,
                            editingScope = editingScope, goals = goals, editingGoalIds = editingGoalIds,
                            onToggleCompletion = onToggleCompletion, onStartEditing = onStartEditing,
                            onEditingTitleChange = onEditingTitleChange, onConfirmEditing = onConfirmEditing,
                            onCancelEditing = onCancelEditing, onEditingRecurrenceSelected = onEditingRecurrenceSelected,
                            onEditingReminderToggled = onEditingReminderToggled, onEditingReminderAdd = onEditingReminderAdd,
                            onEditingReminderEdit = onEditingReminderEdit, onEditingReminderRemove = onEditingReminderRemove,
                            onEditingScopeSelected = onEditingScopeSelected, onStopRecurrence = onStopRecurrence,
                            onToggleEditingGoal = onToggleEditingGoal, onSetAccentColor = onSetAccentColor,
                            onTogglePriority = onTogglePriority, onDelete = onDelete,
                            onBoundsChanged = { todoBounds[entry.value.id] = it },
                            onDragPosition = {},
                            onDragFinished = {},
                            dragGroup = "home-open",
                        )
                        is HomeDisplayItem.Habit -> DaveHabitCard(
                            title = entry.value.title, count = entry.count, targetCount = entry.value.targetCount,
                            period = entry.value.period, color = entry.value.color, isBackfilled = entry.isBackfilled,
                            intervalDays = entry.value.intervalDays,
                            checkedOnDate = entry.checkedOnDate, onClick = { onToggleHabit(entry.value.id) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            onDragFinished = {}, dragGroup = "home-open", dragId = entry.value.id,
                        )
                    }
                }
            }
            if (sections.isEmpty && !draftVisible) {
                item(key = "empty") {
                    Text(
                        text = "点右下角 ＋，直接写下一件事",
                        color = DavePalette.Ink.copy(alpha = .58f),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (sections.completed.isNotEmpty()) {
                item(key = "completed-divider") {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .height(1.dp)
                            .background(DavePalette.Divider),
                    )
                }
                sections.completedUrgent.forEach { entry ->
                    item(key = entry.stableKey) {
                        HomeTodoEntry(
                            todo = entry.value, editingId = editingId, editingTitle = editingTitle,
                            editingRecurrence = editingRecurrence, editingReminderTimes = editingReminderTimes,
                            editingScope = editingScope, goals = goals, editingGoalIds = editingGoalIds,
                            onToggleCompletion = onToggleCompletion, onStartEditing = onStartEditing,
                            onEditingTitleChange = onEditingTitleChange, onConfirmEditing = onConfirmEditing,
                            onCancelEditing = onCancelEditing, onEditingRecurrenceSelected = onEditingRecurrenceSelected,
                            onEditingReminderToggled = onEditingReminderToggled, onEditingReminderAdd = onEditingReminderAdd,
                            onEditingReminderEdit = onEditingReminderEdit, onEditingReminderRemove = onEditingReminderRemove,
                            onEditingScopeSelected = onEditingScopeSelected, onStopRecurrence = onStopRecurrence,
                            onToggleEditingGoal = onToggleEditingGoal, onSetAccentColor = onSetAccentColor,
                            onTogglePriority = onTogglePriority, onDelete = onDelete,
                            onBoundsChanged = { todoBounds[entry.value.id] = it },
                            onDragFinished = {},
                        )
                    }
                }
                sections.completedNormal.forEach { entry ->
                    item(key = entry.stableKey) {
                        HomeTodoEntry(
                            todo = entry.value, editingId = editingId, editingTitle = editingTitle,
                            editingRecurrence = editingRecurrence, editingReminderTimes = editingReminderTimes,
                            editingScope = editingScope, goals = goals, editingGoalIds = editingGoalIds,
                            onToggleCompletion = onToggleCompletion, onStartEditing = onStartEditing,
                            onEditingTitleChange = onEditingTitleChange, onConfirmEditing = onConfirmEditing,
                            onCancelEditing = onCancelEditing, onEditingRecurrenceSelected = onEditingRecurrenceSelected,
                            onEditingReminderToggled = onEditingReminderToggled, onEditingReminderAdd = onEditingReminderAdd,
                            onEditingReminderEdit = onEditingReminderEdit, onEditingReminderRemove = onEditingReminderRemove,
                            onEditingScopeSelected = onEditingScopeSelected, onStopRecurrence = onStopRecurrence,
                            onToggleEditingGoal = onToggleEditingGoal, onSetAccentColor = onSetAccentColor,
                            onTogglePriority = onTogglePriority, onDelete = onDelete,
                            onBoundsChanged = { todoBounds[entry.value.id] = it },
                            onDragFinished = {},
                        )
                    }
                }
                sections.completedHabits.forEach { entry ->
                    item(key = entry.stableKey) {
                        DaveHabitCard(
                            title = entry.value.title, count = entry.count, targetCount = entry.value.targetCount,
                            period = entry.value.period, color = entry.value.color, isBackfilled = entry.isBackfilled,
                            intervalDays = entry.value.intervalDays,
                            checkedOnDate = entry.checkedOnDate, onClick = { onToggleHabit(entry.value.id) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(112.dp)) }
        }

        if (!imeVisible) Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(DavePalette.Completed, CircleShape)
                .clickable(enabled = !draftVisible, onClick = onStartDraft),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = androidx.compose.ui.graphics.Color.White, fontSize = 39.sp)
        }
    }
}

@Composable
private fun HomeTodoEntry(
    todo: TodoOccurrence,
    editingId: String,
    editingTitle: String,
    editingRecurrence: RecurrenceFrequency,
    editingReminderTimes: List<LocalTime>,
    editingScope: TodoChangeScope,
    goals: List<LifeGoalWithEvents>,
    editingGoalIds: Set<String>,
    onToggleCompletion: (String) -> Unit,
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
    onTogglePriority: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBoundsChanged: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: (Offset) -> Unit,
    dragGroup: String = "${todo.displayDate}:open",
) {
    if (editingId == todo.id) {
        androidx.compose.foundation.layout.Column {
            DaveInlineDraftCard(
                value = editingTitle,
                autoFocus = false,
                onValueChange = onEditingTitleChange,
                onConfirm = onConfirmEditing,
                onCancelEmpty = onCancelEditing,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            DaveTodoQuickOptions(
                recurrence = editingRecurrence,
                editingTitle = editingTitle,
                onEditingTitleChange = onEditingTitleChange,
                reminderTimes = editingReminderTimes,
                onRecurrenceSelected = onEditingRecurrenceSelected,
                onReminderAdd = onEditingReminderAdd,
                onReminderEdit = onEditingReminderEdit,
                onReminderRemove = onEditingReminderRemove,
                onReminderToggled = onEditingReminderToggled,
                selectedAccent = todo.accentColor,
                onAccentSelected = { onSetAccentColor(todo.id, it) },
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    } else {
        DaveSwipeTaskCard(
            todo = todo,
            onToggleCompletion = { onToggleCompletion(todo.id) },
            onTogglePriority = { onTogglePriority(todo.id) },
            onEdit = { onStartEditing(todo) },
            onDelete = { onDelete(todo.id) },
            onDragPosition = onDragPosition,
            onDragFinished = onDragFinished,
            dragGroup = dragGroup,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .onGloballyPositioned(onBoundsChanged),
        )
    }
}
