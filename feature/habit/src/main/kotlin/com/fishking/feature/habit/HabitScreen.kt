package com.fishking.feature.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import com.fishking.core.ui.DaveHabitWeekPanel
import com.fishking.core.ui.DaveHabitEditor
import com.fishking.core.ui.DavePalette
import com.fishking.core.ui.DaveScreenFloatingAddAction
import com.fishking.core.ui.DaveListInlineAddAction
import com.fishking.core.ui.FishKingSection
import com.fishking.core.usecase.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HabitScreen(
    repository: HabitRepository,
    currentDate: LocalDate,
    continuous: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: HabitViewModel = viewModel(factory = HabitViewModel.factory(repository)),
) {
    val allWeeks by viewModel.timeline.collectAsStateWithLifecycle()
    val timeline = if (continuous) allWeeks else allWeeks.filter { it.weekStart == HabitRules.weekStart(currentDate) }
    val draftVisible by viewModel.draftVisible.collectAsStateWithLifecycle()
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftPeriod by viewModel.draftPeriod.collectAsStateWithLifecycle()
    val draftTarget by viewModel.draftTarget.collectAsStateWithLifecycle()
    val draftIntervalDays by viewModel.draftIntervalDays.collectAsStateWithLifecycle()
    val draftScheduleStartDate by viewModel.draftScheduleStartDate.collectAsStateWithLifecycle()
    val draftScheduleDays by viewModel.draftScheduleDays.collectAsStateWithLifecycle()
    val draftColor by viewModel.draftColor.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val editingTitle by viewModel.editingTitle.collectAsStateWithLifecycle()
    val editingPeriod by viewModel.editingPeriod.collectAsStateWithLifecycle()
    val editingTarget by viewModel.editingTarget.collectAsStateWithLifecycle()
    val editingIntervalDays by viewModel.editingIntervalDays.collectAsStateWithLifecycle()
    val editingScheduleStartDate by viewModel.editingScheduleStartDate.collectAsStateWithLifecycle()
    val editingScheduleDays by viewModel.editingScheduleDays.collectAsStateWithLifecycle()
    val editingColor by viewModel.editingColor.collectAsStateWithLifecycle()
    val pendingEarlyCheckIn by viewModel.pendingEarlyCheckIn.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val editorBringIntoViewRequester = remember { BringIntoViewRequester() }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    val currentWeek = HabitRules.weekStart(currentDate)
    var deletingHabit by remember { mutableStateOf<com.fishking.core.model.HabitWeekItem?>(null) }
    val deleteScope = androidx.compose.runtime.rememberCoroutineScope()
    deletingHabit?.let { habit -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { deletingHabit = null }, title = { Text("删除「${habit.title}」？") },
        text = { Text("从所有周和主页移除此习惯及其记录展示，不影响其他习惯。底层历史保留，不会清空整周。") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { deleteScope.launch { repository.deleteHabit(habit.id) }; deletingHabit = null }) { Text("删除此习惯") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deletingHabit = null }) { Text("取消") } }) }
    pendingEarlyCheckIn?.let { pending ->
        val last = pending.preview.previousCompletionDate?.let { "上次完成 ${it.monthValue}月${it.dayOfMonth}日，" }.orEmpty()
        val historical = pending.date.isBefore(currentDate)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelEarlyCheckIn,
            title = { Text("还没到计划间隔") },
            text = {
                Text(
                    if (historical) {
                        "${last}原计划 ${pending.preview.nextDueDate?.monthValue}月${pending.preview.nextDueDate?.dayOfMonth}日再做。仍要补记 ${pending.date.monthValue}月${pending.date.dayOfMonth}日吗？确认后会从这次补记重新计算间隔。"
                    } else {
                        "${last}原计划 ${pending.preview.nextDueDate?.monthValue}月${pending.preview.nextDueDate?.dayOfMonth}日再做。仍要记录今天完成吗？确认后会从今天重新计算间隔。"
                    },
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::confirmEarlyCheckIn) { Text(if (historical) "仍然补记" else "仍然完成") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = viewModel::cancelEarlyCheckIn) { Text("取消") } },
        )
    }
    LaunchedEffect(currentDate) { viewModel.setCurrentDate(currentDate) }
    com.fishking.core.ui.DaveMonthPaging(listState, continuous && !draftVisible && editingId == null, viewModel::loadPreviousMonth, viewModel::loadNextMonth)
    var positionedMode by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(continuous, timeline.map { it.weekStart }) {
        if (positionedMode != continuous && timeline.isNotEmpty()) {
            listState.scrollToItem(timeline.indexOfFirst { it.weekStart == currentWeek }.coerceAtLeast(0))
            positionedMode = continuous
        }
    }
    LaunchedEffect(draftVisible, editingId, imeBottom, editorSize) {
        if ((draftVisible || editingId != null) && editorSize != IntSize.Zero) {
            editorBringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(draftVisible, timeline.map { it.weekStart }) {
        if (draftVisible) {
            // The floating button also works after browsing older weeks. The
            // inline draft belongs to the current week, not the current viewport.
            val draftIndex = timeline.indexOfFirst { it.weekStart == currentWeek }
            listState.animateScrollToItem(draftIndex.coerceAtLeast(0))
        }
    }

    val activeEditorModifier = Modifier
        .bringIntoViewRequester(editorBringIntoViewRequester)
        .onSizeChanged { editorSize = it }

    Box(modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (timeline.isEmpty()) {
            item(key = "empty-current-week") {
                NewHabitArea(
                        draftVisible = draftVisible,
                        draftTitle = draftTitle,
                        draftPeriod = draftPeriod,
                        draftTarget = draftTarget,
                        draftIntervalDays = draftIntervalDays,
                        draftScheduleStartDate = draftScheduleStartDate,
                        draftScheduleDays = draftScheduleDays,
                        draftColor = draftColor,
                        currentDate = currentDate,
                        viewModel = viewModel,
                        modifier = activeEditorModifier,
                        showAddAction = !continuous,
                    )
            }
        } else {
            itemsIndexed(timeline, key = { _, item -> item.weekStart.toEpochDay() }) { _, snapshot ->
                DaveHabitWeekPanel(
                    snapshot = snapshot,
                    today = currentDate,
                    onToggle = viewModel::toggle,
                    onEdit = viewModel::startEditing,
                    onToggleSkip = viewModel::toggleSkip,
                    onEndFromWeek = viewModel::endFromWeek,
                    onDeleteHabit = { deletingHabit = it },
                    onReorderHabit = { source, target, after ->
                        val visibleIds = snapshot.items.map { it.id }
                        viewModel.reorderHabitRelative(visibleIds, source, target, after)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    editingHabitId = editingId,
                    habitEditor = if (editingId != null) {
                        {
                            DaveHabitEditor(
                                title = editingTitle,
                                period = editingPeriod,
                                target = editingTarget,
                                intervalDays = editingIntervalDays,
                                scheduleStartDate = editingScheduleStartDate,
                                scheduleDays = editingScheduleDays,
                                color = editingColor,
                                onTitleChange = viewModel::updateEditingTitle,
                                onPeriodChange = viewModel::setEditingPeriod,
                                onTargetChange = viewModel::setEditingTarget,
                                onIntervalDaysChange = viewModel::setEditingIntervalDays,
                                onScheduleStartDateChange = viewModel::setEditingScheduleStartDate,
                                onScheduleDayToggle = viewModel::toggleEditingScheduleDay,
                                onColorChange = viewModel::setEditingColor,
                                onConfirm = viewModel::confirmEditing,
                                onCancel = viewModel::cancelEditing,
                                modifier = activeEditorModifier,
                            )
                        }
                    } else null,
                    footer = if (snapshot.weekStart == currentWeek && editingId == null && (!continuous || draftVisible)) {
                        {
                            NewHabitArea(
                                draftVisible = draftVisible,
                                draftTitle = draftTitle,
                                draftPeriod = draftPeriod,
                                draftTarget = draftTarget,
                                draftIntervalDays = draftIntervalDays,
                                draftScheduleStartDate = draftScheduleStartDate,
                                draftScheduleDays = draftScheduleDays,
                                draftColor = draftColor,
                                currentDate = currentDate,
                                viewModel = viewModel,
                                modifier = activeEditorModifier,
                                showAddAction = !continuous,
                            )
                        }
                    } else null,
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
    if (!continuous) {
        DaveScreenFloatingAddAction(
            section = FishKingSection.HABIT,
            enabled = !draftVisible && editingId == null,
            onClick = { viewModel.cancelEditing(); viewModel.startDraft() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
        )
    }
    }
}

@Composable
private fun NewHabitArea(
    draftVisible: Boolean,
    draftTitle: String,
    draftPeriod: HabitPeriod,
    draftTarget: Int,
    draftIntervalDays: Int,
    draftScheduleStartDate: LocalDate,
    draftScheduleDays: Set<Int>,
    draftColor: Long,
    currentDate: LocalDate,
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier,
    showAddAction: Boolean,
) {
    if (draftVisible) {
        DaveHabitEditor(
            title = draftTitle,
            period = draftPeriod,
            target = draftTarget,
            intervalDays = draftIntervalDays,
            scheduleStartDate = draftScheduleStartDate,
            scheduleDays = draftScheduleDays,
            color = draftColor,
            onTitleChange = viewModel::updateDraft,
            onPeriodChange = viewModel::setPeriod,
            onTargetChange = viewModel::setTarget,
            onIntervalDaysChange = viewModel::setIntervalDays,
            onScheduleStartDateChange = viewModel::setScheduleStartDate,
            onScheduleDayToggle = viewModel::toggleDraftScheduleDay,
            onColorChange = viewModel::setDraftColor,
            onConfirm = { viewModel.confirmDraft(currentDate) },
            onCancel = viewModel::cancelDraft,
            modifier = modifier,
            autoFocus = true,
        )
    } else if (showAddAction) {
        DaveListInlineAddAction(
            contentDescription = "新建打卡项目",
            enabled = true,
            onClick = viewModel::startDraft,
        )
    }
}
