package com.fishking.feature.home

import android.Manifest
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.ui.DaveInlineDraftCard
import com.fishking.core.ui.DavePalette
import com.fishking.core.ui.DaveHomeSwipeTaskCard
import com.fishking.core.ui.DaveTodoQuickOptions
import com.fishking.core.ui.DaveTodoEditScope
import com.fishking.core.ui.DaveHabitCard
import com.fishking.core.ui.DaveSwipeHabitCard
import com.fishking.core.ui.DaveHabitEditor
import com.fishking.core.ui.DaveHabitCompletionFlight
import com.fishking.core.ui.DaveScreenFloatingAddAction
import com.fishking.core.ui.DaveListInlineAddAction
import com.fishking.core.ui.FishKingSection
import com.fishking.core.usecase.HomeRepository
import com.fishking.core.usecase.HabitRepository
import com.fishking.core.usecase.LifeRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

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
    val editingAccentValue by viewModel.editingAccentColor.collectAsStateWithLifecycle()
    val editingRecurrenceName by viewModel.editingRecurrence.collectAsStateWithLifecycle()
    val editingReminderTimesValue by viewModel.editingReminderTimes.collectAsStateWithLifecycle()
    val editingScopeName by viewModel.editingScope.collectAsStateWithLifecycle()
    val draftDateEpochDay by viewModel.draftDateEpochDay.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val editingGoalIds by viewModel.editingGoalIds.collectAsStateWithLifecycle()
    val habitEditingId by viewModel.habitEditingId.collectAsStateWithLifecycle()
    val habitEditingDate by viewModel.habitEditingDate.collectAsStateWithLifecycle()
    val habitEditingTitle by viewModel.habitEditingTitle.collectAsStateWithLifecycle()
    val habitEditingPeriod by viewModel.habitEditingPeriod.collectAsStateWithLifecycle()
    val habitEditingTarget by viewModel.habitEditingTarget.collectAsStateWithLifecycle()
    val habitEditingIntervalDays by viewModel.habitEditingIntervalDays.collectAsStateWithLifecycle()
    val habitEditingScheduleStartDate by viewModel.habitEditingScheduleStartDate.collectAsStateWithLifecycle()
    val habitEditingScheduleDays by viewModel.habitEditingScheduleDays.collectAsStateWithLifecycle()
    val habitEditingColor by viewModel.habitEditingColor.collectAsStateWithLifecycle()
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
    val editingAccent = editingAccentValue.takeUnless { it == Long.MIN_VALUE }
    fun TodoOccurrence.withEditorPreview() = if (id == editingId) copy(accentColor = editingAccent) else this
    val draftGoalIds = draftGoalIdsValue.split(',').filter(String::isNotBlank).toSet()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "提醒已保存；通知权限未开启，系统可能不显示提醒", Toast.LENGTH_LONG).show()
    }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var reminderPicker by remember { mutableStateOf<ReminderPickerRequest?>(null) }
    fun chooseReminder(previous: LocalTime?, editing: Boolean) {
        val owner = editingId
        val date = if (editing) (weekTodos.values.flatten() + todos).firstOrNull { it.id == editingId }?.displayDate ?: selectedDate
            else LocalDate.ofEpochDay(draftDateEpochDay)
        val specs = ReminderDrafts.decode(if (editing) editingReminderTimesValue else draftReminderTimesValue)
        val initial = date.plusDays((specs.firstOrNull { it.localTime == previous }?.dayOffset ?: 0).toLong())
        val scope = com.fishking.core.model.TodoPlanScope.valueOf(if (editing) editingPlanScope else draftPlanScope)
        val deadline = com.fishking.core.model.ReminderSelectionRules.effectiveDeadline(scope,
            (if (editing) editingDeadline else draftDeadline).takeIf { it.isNotBlank() }?.let(LocalDate::parse))
        reminderPicker = ReminderPickerRequest(date, deadline, initial, previous) { time, offset ->
            if (editing && viewModel.editingId.value != owner) return@ReminderPickerRequest
            if (editing) viewModel.replaceEditingReminder(previous, time, offset)
            else viewModel.replaceDraftReminder(previous, time, offset)
            ensureNotificationPermission()
        }
    }
    reminderPicker?.let { request -> ReminderPicker(request) { reminderPicker = null } }
    val openReminderPicker: (LocalTime?) -> Unit = { chooseReminder(it, false) }
    val openEditingReminderPicker: (LocalTime?) -> Unit = { chooseReminder(it, true) }
    val onDraftReminderToggled: (LocalTime) -> Unit = { viewModel.toggleDraftReminder(it) }
    val onEditingReminderToggled: (LocalTime) -> Unit = { viewModel.toggleEditingReminder(it) }

    LaunchedEffect(selectedDate) { viewModel.selectDate(selectedDate) }
    LaunchedEffect(weekView, selectedDate, navigationToken) { if (weekView) viewModel.resetWeekMonths(selectedDate) }
    LaunchedEffect(weekView) {
        viewModel.cancelDraft()
        viewModel.cancelEditing()
        viewModel.cancelHabitEditing()
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cancelDraft()
            viewModel.cancelEditing()
            viewModel.cancelHabitEditing()
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
            if (!weekView && sourceDate == targetDate && sourceKey != null && targetKey != null) {
                viewModel.reorderHomeItem(targetDate, sourceKey, targetKey, after)
            }
        },
        com.fishking.core.ui.LocalTodoPlanning provides com.fishking.core.ui.TodoPlanningEditor(
            com.fishking.core.model.TodoPlanScope.valueOf(if (editingId.isBlank()) draftPlanScope else editingPlanScope), viewModel::setPlanScope,
            (if (editingId.isBlank()) draftDeadline else editingDeadline).takeIf { it.isNotBlank() }?.let(LocalDate::parse), viewModel::setDeadline,
            date = if (editingId.isBlank()) LocalDate.ofEpochDay(draftDateEpochDay)
                else (weekTodos.values.flatten() + todos).firstOrNull { it.id == editingId }?.displayDate ?: selectedDate,
            onDate = { date ->
                if (editingId.isBlank()) viewModel.setDraftDate(date) else viewModel.moveTodo(editingId, date)
            }),
        com.fishking.core.ui.LocalTodoTimeEditor provides { todo ->
        val first = todo.displayReminders.sortedWith(compareBy({ it.dayOffset }, { it.localTime })).firstOrNull()
        reminderPicker = ReminderPickerRequest(todo.displayDate,
            com.fishking.core.model.ReminderSelectionRules.effectiveDeadline(todo.planScope, todo.planDeadline),
            todo.displayDate.plusDays((first?.dayOffset ?: 0).toLong()), first?.localTime) { time, offset ->
            viewModel.setPrimaryReminderTime(todo.id, time, offset)
            ensureNotificationPermission()
        }
    }) {
    if (weekView) WeekHomeScreen(
        navigationToken = navigationToken,
        selectedDate = selectedDate,
        today = LocalDate.now(),
        todosByDate = weekTodos.mapValues { (_, entries) -> entries.map { it.withEditorPreview() } },
        habitsByWeek = weekHabits,
        editingHabitId = habitEditingId,
        editingHabitDate = habitEditingDate,
        onEditHabit = { habit, date -> viewModel.startEditingHabit(habit, date) },
        onDeleteHabit = viewModel::deleteHabit,
        habitEditor = {
            DaveHabitEditor(
                title = habitEditingTitle, period = habitEditingPeriod, target = habitEditingTarget,
                intervalDays = habitEditingIntervalDays, scheduleStartDate = habitEditingScheduleStartDate,
                scheduleDays = habitEditingScheduleDays, color = habitEditingColor,
                onTitleChange = viewModel::updateHabitEditingTitle, onPeriodChange = viewModel::setHabitEditingPeriod,
                onTargetChange = viewModel::setHabitEditingTarget, onIntervalDaysChange = viewModel::setHabitEditingIntervalDays,
                onScheduleStartDateChange = viewModel::setHabitEditingScheduleStartDate,
                onScheduleDayToggle = viewModel::toggleHabitEditingScheduleDay, onColorChange = viewModel::setHabitEditingColor,
                onConfirm = viewModel::confirmHabitEditing, onCancel = viewModel::cancelHabitEditing,
            )
        },
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
        onToggleHabit = { id, date -> viewModel.toggleHabit(id, date) },
        onMoveTodo = viewModel::moveTodo,
        onPreviousMonth = viewModel::loadPreviousMonth,
        onNextMonth = viewModel::loadNextMonth,
        modifier = modifier,
    ) else HomeScreen(
        todos = todos.map { it.withEditorPreview() },
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
        habitEditingId = habitEditingId,
        habitEditingTitle = habitEditingTitle,
        habitEditingPeriod = habitEditingPeriod,
        habitEditingTarget = habitEditingTarget,
        habitEditingIntervalDays = habitEditingIntervalDays,
        habitEditingScheduleStartDate = habitEditingScheduleStartDate,
        habitEditingScheduleDays = habitEditingScheduleDays,
        habitEditingColor = habitEditingColor,
        onStartHabitEditing = viewModel::startEditingHabit,
        onHabitEditingTitleChange = viewModel::updateHabitEditingTitle,
        onHabitEditingPeriodChange = viewModel::setHabitEditingPeriod,
        onHabitEditingTargetChange = viewModel::setHabitEditingTarget,
        onHabitEditingIntervalDaysChange = viewModel::setHabitEditingIntervalDays,
        onHabitEditingScheduleStartDateChange = viewModel::setHabitEditingScheduleStartDate,
        onHabitEditingScheduleDayToggle = viewModel::toggleHabitEditingScheduleDay,
        onHabitEditingColorChange = viewModel::setHabitEditingColor,
        onConfirmHabitEditing = viewModel::confirmHabitEditing,
        onCancelHabitEditing = viewModel::cancelHabitEditing,
        onDeleteHabit = viewModel::deleteHabit,
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
    habitEditingId: String?,
    habitEditingTitle: String,
    habitEditingPeriod: HabitPeriod,
    habitEditingTarget: Int,
    habitEditingIntervalDays: Int,
    habitEditingScheduleStartDate: LocalDate,
    habitEditingScheduleDays: Set<Int>,
    habitEditingColor: Long,
    onStartHabitEditing: (HabitWeekItem) -> Unit,
    onHabitEditingTitleChange: (String) -> Unit,
    onHabitEditingPeriodChange: (HabitPeriod) -> Unit,
    onHabitEditingTargetChange: (Int) -> Unit,
    onHabitEditingIntervalDaysChange: (Int) -> Unit,
    onHabitEditingScheduleStartDateChange: (LocalDate) -> Unit,
    onHabitEditingScheduleDayToggle: (Int) -> Unit,
    onHabitEditingColorChange: (Long?) -> Unit,
    onConfirmHabitEditing: () -> Unit,
    onCancelHabitEditing: () -> Unit,
    onDeleteHabit: (String) -> Unit,
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
    val listState = rememberLazyListState()
    var deletingHabit by remember { mutableStateOf<HabitWeekItem?>(null) }
    deletingHabit?.let { habit ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingHabit = null },
            title = { Text("删除「${habit.title}」？") },
            text = { Text("此习惯会从主页和习惯页移除；其他习惯不受影响。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteHabit(habit.id)
                    deletingHabit = null
                }) { Text("删除此习惯") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingHabit = null }) { Text("取消") }
            },
        )
    }
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
    val currentSections by rememberUpdatedState(sections)
    val currentTodos by rememberUpdatedState(todos)
    val currentHabits by rememberUpdatedState(habits)
    val commitCompletion by rememberUpdatedState(onToggleCompletion)
    val context = LocalContext.current
    var dayOrder by remember(selectedDate) { mutableStateOf<HomeDayOrder?>(null) }
    val itemsAtRest = dayOrder?.applyTo(sections) ?: sections.singleDayItems()
    val measuredSlots = remember(selectedDate) { mutableMapOf<String, Int>() }
    val measuredBounds = remember(selectedDate) { mutableMapOf<String, Rect>() }
    var completionScene by remember(selectedDate) { mutableStateOf<HomeCompletionScene?>(null) }
    var motionTime by remember(selectedDate) { mutableFloatStateOf(0f) }
    var targetAlpha by remember(selectedDate) { mutableFloatStateOf(0f) }
    var targetItem by remember(selectedDate) { mutableStateOf<HomeDisplayItem?>(null) }

    // There is one owner and one clock. The source item no longer starts a
    // second coroutine or persists independently when its gesture completes.
    LaunchedEffect(completionScene) {
        val scene = completionScene ?: return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(720, easing = androidx.compose.animation.core.LinearEasing)) { value, _ ->
            motionTime = value
        }
        // Both slots have reached their final geometry; the outgoing card is
        // fully outside the viewport. Commit exactly once, keeping the frozen
        // scene until Room acknowledges the state instead of letting a Flow
        // emission change the list layout in the middle of the animation.
        when (val source = scene.source) {
            is HomeDisplayItem.Todo -> commitCompletion(source.value.id)
            is HomeDisplayItem.Habit -> onToggleHabit(source.value.id)
        }
        val saved = kotlinx.coroutines.withTimeoutOrNull(4_000L) {
            androidx.compose.runtime.snapshotFlow {
                when (val source = scene.source) {
                    is HomeDisplayItem.Todo -> currentTodos.firstOrNull { it.id == source.value.id }
                        ?.let { HomeDisplayItem.Todo(it) }
                    is HomeDisplayItem.Habit -> buildHomeDisplaySections(selectedDate, currentTodos, currentHabits)
                        .open.plus(buildHomeDisplaySections(selectedDate, currentTodos, currentHabits).completed)
                        .firstOrNull { it.stableKey == source.stableKey }
                }
            }.first { current -> current == null || current.isCompleteOnHome != scene.source.isCompleteOnHome }
        }
        if (saved == null) {
            // A rejected/deleted record must not strand the page in a blank
            // target slot. Reopen the source gap and return to the real facts.
            animate(1f, 0f, animationSpec = tween(260)) { value, _ -> motionTime = value }
            completionScene = null
            if (when (val source = scene.source) {
                    is HomeDisplayItem.Todo -> currentTodos.any { it.id == source.value.id }
                    is HomeDisplayItem.Habit -> currentHabits.any { it.id == source.value.id }
                }) {
                Toast.makeText(context, "完成状态暂未保存，请重试", Toast.LENGTH_SHORT).show()
            }
            return@LaunchedEffect
        }
        targetItem = saved
        animate(0f, 1f, animationSpec = tween(300)) { value, _ -> targetAlpha = value }
        dayOrder = HomeDayOrder.capture(scene.before.moveAcrossDivider(saved), currentSections)
        // The target has exactly the same pixels before and after this switch;
        // there is no temporary prepend followed by a canonical-order snap.
        completionScene = null
    }

    val scene = completionScene
    val displayedItems = scene?.before ?: itemsAtRest
    val frame = HomeCompletionFrame.at(motionTime)
    val slotHeights = scene?.let { homeCompletionSlotHeights(it.sourceSlotHeightPx, frame.reflow) }
    val renderEntry: @Composable (HomeDisplayItem) -> Unit = { entry ->
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
                interactionsEnabled = scene == null,
                completionStampInitiallyVisible = dayOrder?.completedKeys?.contains(entry.stableKey) == true,
                onCompletionRequest = { request ->
                    if (completionScene == null) {
                        motionTime = 0f
                        targetAlpha = 0f
                        targetItem = null
                        completionScene = HomeCompletionScene(
                            source = entry,
                            before = itemsAtRest,
                            request = request,
                            sourceSlotHeightPx = measuredSlots[entry.stableKey]
                                ?: (request.bounds.height + with(density) { 8.dp.toPx() }).roundToInt(),
                        )
                    }
                },
                onDragPosition = {},
                onDragFinished = if (scene == null) ({ _: Offset -> }) else null,
                dragGroup = if (entry.value.isCompleted) "home-completed" else "home-open",
            )
            is HomeDisplayItem.Habit -> {
                if (habitEditingId == entry.value.id) {
                    DaveHabitEditor(
                        title = habitEditingTitle,
                        period = habitEditingPeriod,
                        target = habitEditingTarget,
                        intervalDays = habitEditingIntervalDays,
                        scheduleStartDate = habitEditingScheduleStartDate,
                        scheduleDays = habitEditingScheduleDays,
                        color = habitEditingColor,
                        onTitleChange = onHabitEditingTitleChange,
                        onPeriodChange = onHabitEditingPeriodChange,
                        onTargetChange = onHabitEditingTargetChange,
                        onIntervalDaysChange = onHabitEditingIntervalDaysChange,
                        onScheduleStartDateChange = onHabitEditingScheduleStartDateChange,
                        onScheduleDayToggle = onHabitEditingScheduleDayToggle,
                        onColorChange = onHabitEditingColorChange,
                        onConfirm = onConfirmHabitEditing,
                        onCancel = onCancelHabitEditing,
                    )
                } else {
                    val startHabitCompletion: (com.fishking.core.ui.DaveTaskCompletionRequest) -> Unit = { request ->
                        if (completionScene == null) {
                            motionTime = 0f
                            targetAlpha = 0f
                            targetItem = null
                            completionScene = HomeCompletionScene(
                                source = entry,
                                before = itemsAtRest,
                                request = request,
                                sourceSlotHeightPx = measuredSlots[entry.stableKey]
                                    ?: (request.bounds.height + with(density) { 8.dp.toPx() }).roundToInt(),
                            )
                        }
                    }
                    val onHabitTap = {
                        if (completionScene == null) {
                            if (!entry.willCrossCompletionOnTap()) {
                                onToggleHabit(entry.value.id)
                            } else {
                                val bounds = measuredBounds[entry.stableKey]
                                if (bounds == null || bounds.width <= 0f) {
                                    onToggleHabit(entry.value.id)
                                } else {
                                    startHabitCompletion(com.fishking.core.ui.DaveTaskCompletionRequest(bounds, 0f, 0f))
                                }
                            }
                        }
                    }
                    DaveSwipeHabitCard(
                        title = entry.value.title, count = entry.count, targetCount = entry.value.targetCount,
                        period = entry.value.period, color = entry.value.color, isBackfilled = entry.isBackfilled,
                        intervalDays = entry.value.intervalDays,
                        checkedOnDate = entry.checkedOnDate,
                        onClick = onHabitTap,
                        onEdit = { if (completionScene == null) onStartHabitEditing(entry.value) },
                        onDelete = { if (completionScene == null) deletingHabit = entry.value },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        onDragFinished = if (scene == null && !entry.isComplete) ({ _: Offset -> }) else null,
                        dragGroup = if (entry.isComplete) "home-completed" else "home-open",
                        dragId = entry.value.id,
                        onCompletionRequest = startHabitCompletion,
                        completionCrossesDivider = entry.willCrossCompletionOnTap(),
                        interactionsEnabled = scene == null,
                        checkAnimationMillis = if (dayOrder?.completedKeys?.contains(entry.stableKey) == true) 1 else 300,
                    )
                }
            }
        }
    }

    com.fishking.core.ui.DaveDateDropArea(
        date = selectedDate,
        onMove = onMoveTodo,
        modifier = modifier,
        onDrop = { _, _ -> false },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = scene == null,
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
                            accentColor = draftAccent?.let(::Color),
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
            displayedItems.open.forEach { entry ->
                item(key = if (scene?.sourceKey == entry.stableKey) "completion-source-gap-${entry.stableKey}" else entry.stableKey) {
                    if (scene != null && entry.stableKey == scene.sourceKey) {
                        HomeCompletionGap(requireNotNull(slotHeights).source)
                    } else {
                        Box(
                            Modifier
                                .onSizeChanged { measuredSlots[entry.stableKey] = it.height }
                                .onGloballyPositioned { measuredBounds[entry.stableKey] = it.boundsInRoot() },
                        ) {
                            renderEntry(entry)
                        }
                    }
                }
            }
            // Undo inserts its growing slot directly BEFORE the divider.
            // The unchanged open cards therefore never move.
            if (scene != null && scene.source.isCompleteOnHome) {
                item(key = scene.destinationKey) {
                    HomeCompletionTarget(
                        heightPx = requireNotNull(slotHeights).destination,
                        item = targetItem,
                        alpha = targetAlpha,
                    )
                }
            }
            if (displayedItems.completed.isNotEmpty() || scene != null) {
                item(key = "completed-divider") {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .height(1.dp)
                            .graphicsLayer {
                                alpha = if (scene != null && displayedItems.completed.isEmpty()) frame.reflow else 1f
                            }
                            .background(DavePalette.Divider),
                    )
                }
            }
            // Complete inserts its growing slot directly AFTER the divider.
            // Its growth exactly cancels source shrink for all existing
            // completed cards, so they remain stationary throughout.
            if (scene != null && !scene.source.isCompleteOnHome) {
                item(key = scene.destinationKey) {
                    HomeCompletionTarget(
                        heightPx = requireNotNull(slotHeights).destination,
                        item = targetItem,
                        alpha = targetAlpha,
                    )
                }
            }
            displayedItems.completed.forEach { entry ->
                item(key = if (scene?.sourceKey == entry.stableKey) "completion-source-gap-${entry.stableKey}" else entry.stableKey) {
                    if (scene != null && entry.stableKey == scene.sourceKey) {
                        HomeCompletionGap(requireNotNull(slotHeights).source)
                    } else {
                        Box(
                            Modifier
                                .onSizeChanged { measuredSlots[entry.stableKey] = it.height }
                                .onGloballyPositioned { measuredBounds[entry.stableKey] = it.boundsInRoot() },
                        ) {
                            renderEntry(entry)
                        }
                    }
                }
            }
            if (!draftVisible && scene == null) {
                item(key = "home-inline-add") {
                    DaveListInlineAddAction(
                        contentDescription = "新建当天待办",
                        enabled = true,
                        onClick = onStartDraft,
                    )
                }
            }
            item(key = "home-footer") { Spacer(Modifier.height(112.dp)) }
        }

        if (scene != null) {
            // Absorb new touches while the transaction owns the two slots.
            // This does not clip the visual-only flight drawn above it.
            Box(
                Modifier.matchParentSize().zIndex(10f).pointerInput(scene.source.stableKey) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            )
            when (val source = scene.source) {
                is HomeDisplayItem.Todo -> com.fishking.core.ui.DaveCompletionFlight(
                    todo = source.value,
                    request = scene.request,
                    containerBounds = viewportBounds,
                    progress = frame.flight,
                )
                is HomeDisplayItem.Habit -> DaveHabitCompletionFlight(
                    title = source.value.title,
                    count = source.count,
                    targetCount = source.value.targetCount,
                    period = source.value.period,
                    color = source.value.color,
                    isBackfilled = source.isBackfilled,
                    checkedOnDate = source.checkedOnDate,
                    intervalDays = source.value.intervalDays,
                    request = scene.request,
                    containerBounds = viewportBounds,
                    progress = frame.flight,
                    completing = !source.isComplete,
                )
            }
        }

        DaveScreenFloatingAddAction(
            section = FishKingSection.HOME,
            enabled = !draftVisible && scene == null,
            onClick = onStartDraft,
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
        )
    }
}

private data class HomeCompletionScene(
    val source: HomeDisplayItem,
    val before: HomeDayItems,
    val request: com.fishking.core.ui.DaveTaskCompletionRequest,
    val sourceSlotHeightPx: Int,
) {
    val sourceKey: String get() = source.stableKey
    // The only visible item retains its business key at the target and at rest.
    // The collapsing source is a separately keyed, draw-free spacer.
    val destinationKey: String get() = source.stableKey
}

@Composable
private fun HomeCompletionGap(heightPx: Int) {
    val density = LocalDensity.current
    Spacer(Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() }))
}

@Composable
private fun HomeCompletionTarget(heightPx: Int, item: HomeDisplayItem?, alpha: Float) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() })
            .clipToBounds(),
    ) {
        if (item != null) {
            Box(Modifier.graphicsLayer { this.alpha = alpha }) {
                when (item) {
                    is HomeDisplayItem.Todo -> com.fishking.core.ui.DaveTaskCard(
                        todo = item.value,
                        onToggleCompletion = {},
                        readOnly = true,
                    // CLEAR has already been resolved by the shared gesture /
                    // flight clock; landing must not start it again.
                    completionStampInitiallyVisible = item.isCompleteOnHome,
                        checkAnimationMillis = 1,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                    is HomeDisplayItem.Habit -> DaveHabitCard(
                        title = item.value.title,
                        count = item.count,
                        targetCount = item.value.targetCount,
                        period = item.value.period,
                        color = item.value.color,
                        isBackfilled = item.isBackfilled,
                        checkedOnDate = item.checkedOnDate,
                        onClick = {},
                        readOnly = true,
                        intervalDays = item.value.intervalDays,
                        completionStampInitiallyVisible = item.isCompleteOnHome,
                        checkAnimationMillis = 1,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
            }
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
    onCompletionRequest: (com.fishking.core.ui.DaveTaskCompletionRequest) -> Unit,
    interactionsEnabled: Boolean = true,
    completionStampInitiallyVisible: Boolean = false,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)?,
    dragGroup: String = "${todo.displayDate}:open",
) {
    if (editingId == todo.id) {
        androidx.compose.foundation.layout.Column {
            DaveInlineDraftCard(
                value = editingTitle,
                accentColor = todo.accentColor?.let(::Color),
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
        DaveHomeSwipeTaskCard(
            todo = todo,
            onToggleCompletion = { onToggleCompletion(todo.id) },
            onEdit = { onStartEditing(todo) },
            onDelete = { onDelete(todo.id) },
            onDragPosition = onDragPosition,
            onDragFinished = onDragFinished,
            dragGroup = dragGroup,
            onSetAccentColor = { color -> onSetAccentColor(todo.id, color) },
            onOpenArrange = { onStartEditing(todo) },
            onOpenTags = { onStartEditing(todo) },
            onCompletionRequest = onCompletionRequest,
            interactionsEnabled = interactionsEnabled,
            completionStampInitiallyVisible = completionStampInitiallyVisible,
            checkAnimationMillis = if (completionStampInitiallyVisible) 1 else 300,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp),
        )
    }
}
