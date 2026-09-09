package com.fishking.feature.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import com.fishking.core.ui.DaveHabitQuickOptions
import com.fishking.core.ui.DaveHabitCadenceOptions
import com.fishking.core.ui.DaveInlineDraftCard
import com.fishking.core.ui.DavePalette
import com.fishking.core.ui.DaveAccentPalette
import com.fishking.core.ui.LocalPresetTags
import com.fishking.core.ui.daveTaskTags
import com.fishking.core.ui.replaceDaveTaskTags
import com.fishking.core.usecase.HabitRepository
import java.time.LocalDate
import java.time.DayOfWeek
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

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
    val pendingBackfillAnchor by viewModel.pendingBackfillAnchor.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
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
                        "${last}原计划 ${pending.preview.nextDueDate?.monthValue}月${pending.preview.nextDueDate?.dayOfMonth}日再做。仍要补记 ${pending.date.monthValue}月${pending.date.dayOfMonth}日吗？这一步只补历史，保存后可另选是否重算。"
                    } else {
                        "${last}原计划 ${pending.preview.nextDueDate?.monthValue}月${pending.preview.nextDueDate?.dayOfMonth}日再做。仍要记录今天完成吗？确认后会从今天重新计算间隔。"
                    },
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::confirmEarlyCheckIn) { Text(if (historical) "仍然补记" else "仍然完成") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = viewModel::cancelEarlyCheckIn) { Text("取消") } },
        )
    }
    pendingBackfillAnchor?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissBackfillAnchor,
            title = { Text("是否重算间隔？") },
            text = { Text("这次补记默认只保留历史，不改变现在的计划。要从 ${pending.date.monthValue}月${pending.date.dayOfMonth}日重新计算吗？") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::confirmBackfillAnchor) { Text("从这次重算") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = viewModel::dismissBackfillAnchor) { Text("只补记") } },
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
                        isEmpty = true,
                        currentDate = currentDate,
                        viewModel = viewModel,
                        modifier = activeEditorModifier,
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
                            HabitEditor(
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
                    footer = if (snapshot.weekStart == currentWeek && editingId == null) {
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
                                isEmpty = snapshot.items.isEmpty(),
                                currentDate = currentDate,
                                viewModel = viewModel,
                                modifier = activeEditorModifier,
                            )
                        }
                    } else null,
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
    if (!imeVisible) Box(Modifier.align(Alignment.BottomEnd).padding(22.dp)) {
        Box(Modifier.size(62.dp).clip(CircleShape)
            .background(DavePalette.Completed, CircleShape)
            .clickable(enabled = !draftVisible && editingId == null) { viewModel.cancelEditing(); viewModel.startDraft() }
            .semantics { contentDescription = "新建打卡习惯" }, contentAlignment = Alignment.Center) {
            Text("+", color = androidx.compose.ui.graphics.Color.White, fontSize = 39.sp)
        }
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
    isEmpty: Boolean,
    currentDate: LocalDate,
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier,
) {
    if (draftVisible) {
        Column(modifier = modifier) {
            DaveInlineDraftCard(
                value = draftTitle,
                onValueChange = viewModel::updateDraft,
                onConfirm = { viewModel.confirmDraft(currentDate) },
                onCancelEmpty = viewModel::cancelDraft,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            DaveHabitQuickOptions(
                period = draftPeriod,
                targetCount = draftTarget,
                intervalDays = draftIntervalDays,
                onPeriodSelected = viewModel::setPeriod,
                onTargetCountChanged = viewModel::setTarget,
                onIntervalDaysChanged = viewModel::setIntervalDays,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HabitTagAndCadenceOptions(draftTitle, viewModel::updateDraft, draftPeriod, viewModel::setPeriod)
            HabitSchedulePicker(draftPeriod, draftScheduleDays, viewModel::toggleDraftScheduleDay)
            HabitScheduleStartOption(draftPeriod, draftScheduleStartDate, viewModel::setScheduleStartDate)
            HabitTargetSummary(draftPeriod, draftTarget, draftIntervalDays, draftScheduleDays)
            DaveAccentPalette(
                selected = draftColor,
                onSelected = viewModel::setDraftColor,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    } else if (isEmpty) {
        Text("点击右下角 + 建立打卡项目", color = DavePalette.Ink.copy(alpha = .58f), fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun HabitEditor(
    title: String,
    period: HabitPeriod,
    target: Int,
    intervalDays: Int,
    scheduleStartDate: LocalDate,
    scheduleDays: Set<Int>,
    color: Long,
    onTitleChange: (String) -> Unit,
    onPeriodChange: (HabitPeriod) -> Unit,
    onTargetChange: (Int) -> Unit,
    onIntervalDaysChange: (Int) -> Unit,
    onScheduleStartDateChange: (LocalDate) -> Unit,
    onScheduleDayToggle: (Int) -> Unit,
    onColorChange: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        DaveInlineDraftCard(
            value = title,
            autoFocus = false,
            onValueChange = onTitleChange,
            onConfirm = onConfirm,
            onCancelEmpty = onCancel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        DaveHabitQuickOptions(
            period = period,
            targetCount = target,
            intervalDays = intervalDays,
            onPeriodSelected = onPeriodChange,
            onTargetCountChanged = onTargetChange,
            onIntervalDaysChanged = onIntervalDaysChange,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        HabitTagAndCadenceOptions(title, onTitleChange, period, onPeriodChange)
        HabitSchedulePicker(period, scheduleDays, onScheduleDayToggle)
        HabitScheduleStartOption(period, scheduleStartDate, onScheduleStartDateChange)
        HabitTargetSummary(period, target, intervalDays, scheduleDays)
        DaveAccentPalette(
            selected = color,
            onSelected = onColorChange,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun HabitTagAndCadenceOptions(
    title: String,
    onTitleChange: (String) -> Unit,
    period: HabitPeriod,
    onPeriodChange: (HabitPeriod) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    var draft by remember(title) { mutableStateOf(daveTaskTags(title).joinToString(" ")) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (expanded) DavePalette.HeaderGreen else androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), CircleShape)
                    .border(1.dp, if (expanded) DavePalette.HeaderGreenDark else DavePalette.Divider, CircleShape)
                    .clickable { expanded = !expanded }
                    .semantics { contentDescription = "编辑习惯TAG" },
                contentAlignment = Alignment.Center,
            ) { Text("#", color = if (expanded) androidx.compose.ui.graphics.Color.White else DavePalette.Ink, fontSize = 21.sp) }
            DaveHabitCadenceOptions(
                period = period,
                onPeriodSelected = onPeriodChange,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (expanded) {
            val presets = LocalPresetTags.current
            if (presets.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                items(presets.size) { index ->
                    val tag = presets[index]
                    androidx.compose.material3.TextButton(onClick = {
                        val tags = draft.split(Regex("[\\s#,，]+")).filter(String::isNotBlank).toMutableSet()
                        if (!tags.add(tag)) tags.remove(tag)
                        draft = tags.joinToString(" ")
                    }) { Text("#$tag", color = DavePalette.Meta) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("工作 健康") },
                )
                androidx.compose.material3.TextButton(onClick = {
                    onTitleChange(replaceDaveTaskTags(title, draft)); expanded = false
                }) { Text("应用", color = DavePalette.HeaderGreenDark) }
            }
        }
    }
}

@Composable
private fun HabitSchedulePicker(period: HabitPeriod, selected: Set<Int>, onToggle: (Int) -> Unit) {
    if (period == HabitPeriod.DAILY || period.isIntervalMode()) return
    var expanded by remember(period, selected.isNotEmpty()) { mutableStateOf(selected.isNotEmpty()) }
    val values = if (period == HabitPeriod.WEEKLY) (1..7).toList() else (1..31).toList()
    val labels = if (period == HabitPeriod.WEEKLY) listOf("一", "二", "三", "四", "五", "六", "日") else values.map(Int::toString)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "收起指定日期" else if (period == HabitPeriod.WEEKLY) "指定星期（可选）" else "指定日期（可选）",
            color = DavePalette.HeaderGreenDark,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = .72f), RoundedCornerShape(50))
                .border(1.dp, DavePalette.Divider, RoundedCornerShape(50))
                .clickable { expanded = !expanded }
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
        if (selected.isNotEmpty()) {
            Text(
                text = "清空",
                color = DavePalette.Meta,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp).clickable { selected.toList().forEach(onToggle) },
            )
        }
    }
    if (!expanded) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        items(values.size) { index ->
            val value = values[index]
            val active = value in selected
            Box(
                Modifier
                    .size(if (period == HabitPeriod.WEEKLY) 38.dp else 34.dp)
                    .clip(CircleShape)
                    .background(if (active) DavePalette.HeaderGreen else androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), CircleShape)
                    .border(1.dp, if (active) DavePalette.HeaderGreen.copy(alpha = .65f) else DavePalette.Divider, CircleShape)
                    .clickable { onToggle(value) },
                contentAlignment = Alignment.Center,
            ) { Text(labels[index], color = if (active) androidx.compose.ui.graphics.Color.White else DavePalette.Ink, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun HabitScheduleStartOption(
    period: HabitPeriod,
    startDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    if (!period.isIntervalMode()) return
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "开始 ${startDate.year}/${startDate.monthValue}/${startDate.dayOfMonth}",
            color = DavePalette.HeaderGreenDark,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = .72f), RoundedCornerShape(50))
                .border(1.dp, DavePalette.Divider, RoundedCornerShape(50))
                .clickable {
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                        startDate.year,
                        startDate.monthValue - 1,
                        startDate.dayOfMonth,
                    ).show()
                }
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HabitTargetSummary(period: HabitPeriod, target: Int, intervalDays: Int, scheduleDays: Set<Int>) {
    val error = habitScheduleError(period, target, scheduleDays)
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)) {
        Text(
            text = when (period) {
                HabitPeriod.DAILY -> "每天 × $target 次"
                HabitPeriod.WEEKLY -> if (scheduleDays.isEmpty()) {
                    "每周 × $target 次 · 不限哪天"
                } else {
                    "每周 × $target 次 · ${scheduleDays.sorted().joinToString("、") { listOf("一", "二", "三", "四", "五", "六", "日")[it - 1] }}"
                }
                HabitPeriod.MONTHLY -> if (scheduleDays.isEmpty()) {
                    "每月 × $target 次 · 不限哪天"
                } else {
                    "每月 × $target 次 · ${scheduleDays.sorted().joinToString("、") { "${it}日" }}"
                }
                HabitPeriod.EVERY_N_DAYS -> "每$intervalDays 天一次 · 固定节奏"
                HabitPeriod.AFTER_COMPLETION_N_DAYS -> "完成后隔 $intervalDays 天 · 随实际完成重算"
            },
            color = DavePalette.Ink.copy(alpha = .68f),
            fontSize = 13.sp,
        )
        error?.let {
            Text(it, color = DavePalette.Urgent, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (period == HabitPeriod.MONTHLY && scheduleDays.any { it >= 29 }) {
            Text(
                "29–31 日在部分月份不存在；这些月份只会在实际存在的日期显示",
                color = DavePalette.Meta,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
