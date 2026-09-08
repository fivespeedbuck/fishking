@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fishking.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoStatus
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.HabitPeriod
import java.time.LocalTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DaveHabitCard(
    title: String,
    count: Int,
    targetCount: Int,
    period: HabitPeriod,
    color: Long,
    isBackfilled: Boolean,
    checkedOnDate: Boolean = count > 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    dragGroup: String = "habit-home",
    dragId: String = title,
) {
    val progressColor = Color(color)
    val complete = if (period == HabitPeriod.DAILY) count >= targetCount else checkedOnDate
    val visuallyComplete = complete
    val titleParts = remember(title) { splitDaveTaskText(title) }
    val lift = rememberDaveLiftModifier(
        key = dragId,
        enabled = !readOnly && onDragFinished != null,
        draw = { DaveHabitCard(title, count, targetCount, period, color, isBackfilled, checkedOnDate, {}, readOnly = true, dragId = dragId) },
        onPosition = onDragPosition,
        onDrop = onDragFinished,
        group = dragGroup,
    )
    Row(
        modifier = modifier.then(lift)
            .fillMaxWidth()
            .height(DaveTaskCardHeight)
            .background(DavePalette.Card, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .then(if (readOnly) Modifier else Modifier.clickable(onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(14.dp)
                .height(DaveTaskCardHeight)
                .background(if (visuallyComplete) DavePalette.Completed else DavePalette.Habit, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)),
        )
        Box(
            modifier = Modifier
                .padding(start = 17.dp)
                .size(34.dp)
                .then(
                    Modifier.border(3.dp, progressColor, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (visuallyComplete) {
                DaveDrawnCheck(progressColor, Modifier.size(28.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            DaveTitle(
                text = titleParts.title,
                color = progressColor,
                fontSize = 19.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textDecoration = if (visuallyComplete) TextDecoration.LineThrough else TextDecoration.None,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$count/$targetCount", color = progressColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text((listOf(habitPeriodTag(period)) + titleParts.tags).distinct().joinToString(" ") { "#$it" }, color = progressColor, fontSize = 12.sp, maxLines = 1)
                if (isBackfilled) Text("补", color = DavePalette.Meta, fontSize = 10.sp)
            }
        }
        AnimatedVisibility(
            visibleState = rememberCompletionStamp(visuallyComplete),
            enter = fadeIn(tween(CompletionTransitionMillis.toInt())) + scaleIn(tween(CompletionTransitionMillis.toInt()), initialScale = .55f),
        ) {
            Box(
                modifier = Modifier
                    .rotate(8f)
                    .border(2.dp, DavePalette.Completed, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text("CLEAR", color = DavePalette.Completed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun DaveTaskCard(
    todo: TodoOccurrence,
    onToggleCompletion: () -> Unit,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    var dragOffset by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var cardBounds by remember(todo.id) { mutableStateOf(Rect.Zero) }
    var dragStartPointer by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragging by remember(todo.id) { mutableStateOf(false) }
    val dragLayer = LocalDaveDragController.current
    val currentDragPosition by rememberUpdatedState(onDragPosition)
    val currentDragFinished by rememberUpdatedState(onDragFinished)
    val completed = todo.isCompleted
    DisposableEffect(todo.id) { onDispose { if (dragging) dragLayer?.finish() } }
    val contentAlpha by animateFloatAsState(
        targetValue = if (completed) .7f else 1f,
        animationSpec = tween(430),
        label = "task completion alpha",
    )
    val stripe = when {
        completed -> DavePalette.Completed
        todo.fromLifeGoal -> DavePalette.Life
        todo.planScope != com.fishking.core.model.TodoPlanScope.DATE -> DavePalette.Plan
        todo.priority == TodoPriority.URGENT -> DavePalette.Urgent
        else -> DavePalette.Normal
    }
    val cardColor = DavePalette.Card
    val foreground = todo.accentColor?.let(::Color) ?: DavePalette.Ink
    val markColor = todo.accentColor?.let(::Color) ?: if (completed) DavePalette.Completed else Color.Black
    val titleParts = remember(todo.title) { splitDaveTaskText(todo.title) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DaveTodoCardHeight)
            .zIndex(if (dragging) 5f else 0f)
            .graphicsLayer {
                alpha = if (dragging && dragLayer != null) 0f else 1f
                translationX = if (dragLayer == null) dragOffset.x else 0f
                translationY = if (dragLayer == null) dragOffset.y else 0f
                shadowElevation = if (dragging) 12.dp.toPx() else 0f
            }
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .clip(RoundedCornerShape(13.dp))
            .background(cardColor, RoundedCornerShape(13.dp))
            .then(if (readOnly) Modifier else Modifier.clickable(onClick = onToggleCompletion)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(DaveTodoCardHeight)
                .background(stripe, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
                .then(
                    if (!readOnly && !todo.isCompleted && onDragFinished != null) Modifier.pointerInput(todo.id, todo.isCompleted) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                dragging = true
                                dragOffset = Offset.Zero
                                dragStartPointer = cardBounds.topLeft + local
                                dragLayer?.start(cardBounds) { DaveTaskCard(todo, {}, readOnly = true) }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                dragLayer?.move(dragOffset)
                                currentDragPosition?.invoke(dragStartPointer + dragOffset)
                            },
                            onDragEnd = {
                                val dropPoint = dragStartPointer + dragOffset
                                dragging = false
                                dragOffset = Offset.Zero
                                dragLayer?.finish()
                                currentDragFinished?.invoke(dropPoint)
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = Offset.Zero
                                dragLayer?.finish()
                            },
                        )
                    } else Modifier,
                )
                .semantics { if (onDragFinished != null) contentDescription = "长按拖动待办" },
        )
        Box(
            modifier = Modifier
                .padding(start = 17.dp)
                .size(34.dp)
                .border(3.dp, markColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                DaveDrawnCheck(markColor, Modifier.size(28.dp))
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp).alpha(contentAlpha),
            verticalArrangement = Arrangement.Center,
        ) {
            DaveTitle(
                text = titleParts.title,
                color = foreground,
                fontSize = 19.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (completed) "1/1" else "0/1", color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
                if (todoTimeLabel(todo).isNotEmpty()) DaveTodoTime(todo, {}, modifier = Modifier.alignByBaseline())
                Text(
                    (listOf(todoTypeTag(todo)) + listOfNotNull(todoDeadlineTag(todo)) + titleParts.tags)
                        .distinct().joinToString(" ") { "#$it" },
                    color = todo.accentColor?.let(::Color) ?: DavePalette.Meta,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
        }
        AnimatedVisibility(
            visibleState = rememberCompletionStamp(completed),
            enter = fadeIn(tween(430)) + scaleIn(tween(430), initialScale = .55f),
        ) {
            Box(
                modifier = Modifier
                    .rotate(8f)
                    .border(2.dp, DavePalette.Completed, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text("CLEAR", color = DavePalette.Completed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun DaveSwipeTaskCard(
    todo: TodoOccurrence,
    onToggleCompletion: () -> Unit,
    onTogglePriority: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    dragGroup: String = "${todo.displayDate}:open",
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidth = with(density) { 136.dp.toPx() }
    val priorityThreshold = with(density) { 62.dp.toPx() }
    val scope = rememberCoroutineScope()
    var offsetX by remember(todo.id) { mutableStateOf(0f) }

    fun settle(target: Float, after: (() -> Unit)? = null) {
        scope.launch {
            animate(offsetX, target, animationSpec = tween(180)) { value, _ -> offsetX = value }
            after?.invoke()
        }
    }

    fun requestCompletionToggle() {
        onToggleCompletion()
    }

    val lift = rememberDaveLiftModifier(todo.id, !todo.isCompleted,
        { DaveTaskCard(todo, {}, readOnly = true) }, onDragPosition, onDragFinished, dragGroup)
    Box(modifier = modifier.fillMaxWidth().height(DaveTodoCardHeight).then(lift).clip(RoundedCornerShape(13.dp))) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).height(DaveTodoCardHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeAction(
                color = DavePalette.Habit,
                description = "编辑待办",
                icon = { Icon(Icons.Outlined.Edit, null, tint = Color.White) },
                onClick = { settle(0f, onEdit) },
            )
            SwipeAction(
                color = DavePalette.Urgent,
                description = "删除待办",
                icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Color.White) },
                onClick = { settle(0f, onDelete) },
            )
        }
        DaveTaskCard(
            todo = todo,
            onToggleCompletion = {
                if (offsetX < -1f) settle(0f) else requestCompletionToggle()
            },
            onDragPosition = null,
            onDragFinished = null,
            modifier = Modifier
                .zIndex(1f)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(todo.id, todo.isCompleted) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val maxRight = if (todo.isCompleted) 0f else priorityThreshold * 1.55f
                            offsetX = (offsetX + amount).coerceIn(-actionWidth, maxRight)
                        },
                        onDragEnd = {
                            when {
                                offsetX >= priorityThreshold && !todo.isCompleted -> settle(0f, onTogglePriority)
                                offsetX <= -actionWidth * .48f -> settle(-actionWidth)
                                else -> settle(0f)
                            }
                        },
                        onDragCancel = { settle(0f) },
                    )
                },
        )

    }
}

@Composable
private fun SwipeAction(
    color: Color,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(DaveTodoCardHeight)
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

data class DaveAccentOption(val argb: Long?, val color: Color)

val DaveAccentOptions = listOf(
    DaveAccentOption(null, DavePalette.Card),
    DaveAccentOption(0xFF355C52, Color(0xFF355C52)),
    DaveAccentOption(0xFF8FA7E4, Color(0xFF8FA7E4)),
    DaveAccentOption(0xFFB5D0A8, Color(0xFFB5D0A8)),
    DaveAccentOption(0xFFED8FAE, Color(0xFFED8FAE)),
    DaveAccentOption(0xFFF3CB6C, Color(0xFFF3CB6C)),
)

@Composable
fun DaveTodoQuickOptions(
    recurrence: RecurrenceFrequency,
    reminderTimes: List<LocalTime>,
    onRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onReminderAdd: () -> Unit,
    onReminderEdit: (LocalTime) -> Unit,
    onReminderRemove: (LocalTime) -> Unit,
    selectedAccent: Long? = null,
    onAccentSelected: ((Long?) -> Unit)? = null,
    onReminderToggled: ((LocalTime) -> Unit)? = null,
    editingTitle: String? = null,
    onEditingTitleChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var paletteExpanded by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    if (paletteExpanded && onAccentSelected != null) DaveColorPicker(selectedAccent, onAccentSelected, { paletteExpanded = false })
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAccentSelected != null) {
                Box {
                    QuickIconOption(
                        icon = Icons.Outlined.Palette,
                        description = "选择内容颜色",
                        selected = selectedAccent != null,
                        onClick = { paletteExpanded = true },
                    )

                }
            }
            QuickIconOption(
                icon = Icons.Outlined.AddAlarm,
                description = "添加自定义提醒时间",
                selected = reminderTimes.isNotEmpty(),
                onClick = onReminderAdd,
            )
            DavePlanningOption()
            if (editingTitle != null && onEditingTitleChange != null) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .clip(CircleShape)
                        .background(if (tagsExpanded) DavePalette.HeaderGreen else Color.White.copy(alpha = .5f), CircleShape)
                        .border(1.dp, DavePalette.Divider, CircleShape)
                        .clickable {
                            if (!tagsExpanded) tagDraft = splitDaveTaskText(editingTitle).tags.joinToString(" ")
                            tagsExpanded = !tagsExpanded
                        }
                        .semantics { contentDescription = "编辑待办TAG" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#", color = if (tagsExpanded) Color.White else DavePalette.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tagsExpanded && editingTitle != null && onEditingTitleChange != null) {
            Column(Modifier.fillMaxWidth().background(DavePalette.Card, RoundedCornerShape(9.dp)).padding(10.dp)) {
                Text("自定义 TAG · 多个标签用空格分隔", color = DavePalette.Meta, fontSize = 12.sp)
                val presets = LocalPresetTags.current
                if (presets.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { tag -> androidx.compose.material3.TextButton(onClick = {
                        val selectedTags = tagDraft.split(Regex("[\\s#,，]+" )).filter(String::isNotBlank).toMutableSet()
                        if (!selectedTags.add(tag)) selectedTags.remove(tag)
                        tagDraft = selectedTags.joinToString(" ")
                    }) { Text("#$tag", color = DavePalette.Meta) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                            .semantics { contentDescription = "输入自定义TAG" },
                        singleLine = true,
                        textStyle = TextStyle(color = DavePalette.Ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(DavePalette.HeaderGreen),
                        decorationBox = { input ->
                            Box { if (tagDraft.isEmpty()) Text("例如：工作 生活", color = DavePalette.Meta.copy(alpha = .6f), fontSize = 16.sp); input() }
                        },
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        onEditingTitleChange(replaceDaveTaskTags(editingTitle, tagDraft))
                        tagsExpanded = false
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }) { Text("应用", color = DavePalette.HeaderGreenDark) }
                }
                Text("类型 TAG 自动显示；应用后点卡片 ✓ 保存", color = DavePalette.Meta, fontSize = 11.sp)
            }
        }
        if (reminderTimes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reminderTimes.sorted().forEach { time ->
                    ReminderTimeChip(
                        time = time,
                        onEdit = { onReminderEdit(time) },
                        onRemove = { onReminderRemove(time) },
                    )
                }
            }
        }
    }
}

@Composable
fun DaveTodoEditScope(
    scope: TodoChangeScope,
    onScopeSelected: (TodoChangeScope) -> Unit,
    onStopRecurrence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("循环修改范围", color = DavePalette.Meta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        TodoScopeOption(
            label = "仅本次",
            selected = scope == TodoChangeScope.ONLY_THIS,
            onClick = { onScopeSelected(TodoChangeScope.ONLY_THIS) },
        )
        TodoScopeOption(
            label = "本次及以后",
            selected = scope == TodoChangeScope.THIS_AND_FUTURE,
            onClick = { onScopeSelected(TodoChangeScope.THIS_AND_FUTURE) },
        )
        TodoScopeOption(
            label = "停止循环",
            selected = false,
            onClick = onStopRecurrence,
            destructive = true,
        )
    }
}

@Composable
private fun TodoScopeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val foreground = if (destructive) DavePalette.Urgent else DavePalette.HeaderGreenDark
    Text(
        text = label,
        color = foreground,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DavePalette.CurrentWeek else DavePalette.CardMuted.copy(alpha = .72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun ReminderTimeChip(
    time: LocalTime,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(DavePalette.CardMuted.copy(alpha = .82f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d:%02d".format(time.hour, time.minute),
            color = DavePalette.Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(start = 8.dp, top = 5.dp, bottom = 5.dp),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "删除 ${time} 提醒",
                tint = DavePalette.Meta,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
fun DaveHabitQuickOptions(
    period: HabitPeriod,
    targetCount: Int,
    onPeriodSelected: (HabitPeriod) -> Unit,
    onTargetCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickIconOption(
            icon = Icons.Outlined.Today,
            description = "每日目标",
            selected = period == HabitPeriod.DAILY,
            onClick = { onPeriodSelected(HabitPeriod.DAILY) },
        )
        QuickIconOption(
            icon = Icons.Outlined.DateRange,
            description = "每周目标",
            selected = period == HabitPeriod.WEEKLY,
            onClick = { onPeriodSelected(HabitPeriod.WEEKLY) },
        )
        QuickIconOption(
            icon = Icons.Outlined.CalendarMonth,
            description = "每月指定日期",
            selected = period == HabitPeriod.MONTHLY,
            onClick = { onPeriodSelected(HabitPeriod.MONTHLY) },
        )
        Spacer(Modifier.weight(1f))
        QuickIconOption(
            icon = Icons.Outlined.Remove,
            description = "减少目标次数",
            selected = false,
            onClick = { if (targetCount > 1) onTargetCountChanged(targetCount - 1) },
        )
        Text(
            text = targetCount.toString(),
            color = DavePalette.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        QuickIconOption(
            icon = Icons.Outlined.Add,
            description = "增加目标次数",
            selected = false,
            onClick = { onTargetCountChanged(targetCount + 1) },
        )
    }
}

@Composable
private fun QuickIconOption(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) DavePalette.HeaderGreen else Color.White.copy(alpha = .5f), RoundedCornerShape(50))
            .border(1.dp, if (selected) DavePalette.HeaderGreenDark else DavePalette.Divider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else DavePalette.Ink,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun DaveAccentPalette(selected: Long?, onSelected: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).clip(CircleShape).background(selected?.let(::Color) ?: DavePalette.Ink, CircleShape)
            .clickable { open = true }.semantics { contentDescription = "打开拾色器" })
        Text("自选颜色", color = DavePalette.Ink, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(10.dp))
    }
    if (open) DaveColorPicker(selected, onSelected, { open = false })
}

@Composable
fun DaveInlineDraftCard(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    placeholder: String = "输入今天要做的事…",
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        bringIntoViewRequester.bringIntoView()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DaveTaskCardHeight)
            .background(DavePalette.Card, RoundedCornerShape(13.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(DaveTaskCardHeight)
                .background(DavePalette.Normal, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)),
        )
        Box(
            modifier = Modifier
                .padding(start = 17.dp)
                .size(34.dp)
                .border(3.dp, Color.Black.copy(alpha = .35f), CircleShape),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp)
                .focusRequester(focusRequester)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) hadFocus = true
                    if (hadFocus && !state.isFocused && value.isBlank()) onCancelEmpty()
                },
            textStyle = TextStyle(color = DavePalette.Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(DavePalette.HeaderGreen),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (value.isNotBlank()) {
                    onConfirm()
                    focusManager.clearFocus()
                }
            }),
            decorationBox = { input ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = DavePalette.Ink.copy(alpha = .38f), fontSize = 17.sp)
                    input()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DavePalette.Completed, CircleShape)
                .clickable(enabled = value.isNotBlank(), onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .padding(start = 7.dp, end = 10.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(DavePalette.CardMuted, CircleShape)
                .clickable {
                    focusManager.clearFocus()
                    onCancelEmpty()
                }
                .semantics { contentDescription = "取消新建" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = DavePalette.Ink, modifier = Modifier.size(19.dp))
        }
    }
}

private val DaveTaskCardHeight = 72.dp
private val DaveTodoCardHeight = 88.dp

@Composable
private fun rememberCompletionStamp(completed: Boolean): MutableTransitionState<Boolean> {
    val transition = remember { MutableTransitionState(false) }
    LaunchedEffect(completed) { transition.targetState = completed }
    return transition
}

private const val CompletionTransitionMillis = 430L
