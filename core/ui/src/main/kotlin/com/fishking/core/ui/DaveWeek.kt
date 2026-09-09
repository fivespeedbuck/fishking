@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DaveWeekDayPanel(
    title: String,
    isToday: Boolean,
    isCurrentWeek: Boolean = false,
    onBlankClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isCurrentWeek) DavePalette.CurrentWeek else DavePalette.OtherWeek, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = DavePalette.WeekBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Text(
            text = title + if (isToday) " · 今天" else if (isCurrentWeek) " · 本周" else "",
            color = if (isToday) DavePalette.HeaderGreenDark else DavePalette.Ink.copy(alpha = .7f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
        )
        content()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBlankClick)
                .semantics { contentDescription = "在$title 新建日程" },
        )
    }
}

@Composable
fun DaveCompactTaskCard(
    todo: TodoOccurrence,
    onToggleCompletion: () -> Unit,
    onDragFinished: (Offset) -> Unit,
    onDragPosition: (Offset) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onTogglePriority: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    dragGroup: String = "${todo.displayDate}:open",
) {
    var dragOffset by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var cardBounds by remember(todo.id) { mutableStateOf(Rect.Zero) }
    var dragStartCenter by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragging by remember(todo.id) { mutableStateOf(false) }
    var swipeOffset by remember(todo.id) { mutableStateOf(0f) }
    val dragLayer = LocalDaveDragController.current
    val currentDragPosition by rememberUpdatedState(onDragPosition)
    val currentDragFinished by rememberUpdatedState(onDragFinished)
    DisposableEffect(todo.id) { onDispose { if (dragging) dragLayer?.finish() } }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidthPx = with(density) { 76.dp.toPx() }
    val priorityThresholdPx = with(density) { 44.dp.toPx() }
    val completed = todo.isCompleted
    val stripe = when {
        completed -> DavePalette.Completed
        todo.fromLifeGoal -> DavePalette.Life
        todo.planScope != com.fishking.core.model.TodoPlanScope.DATE -> DavePalette.Plan
        todo.priority == TodoPriority.URGENT -> DavePalette.Urgent
        else -> DavePalette.Normal
    }
    val cardColor = DavePalette.Card
    fun settleSwipe(target: Float, after: (() -> Unit)? = null) {
        scope.launch {
            animate(swipeOffset, target, animationSpec = tween(170)) { value, _ -> swipeOffset = value }
            after?.invoke()
        }
    }
    val lift = rememberDaveLiftModifier(todo.id, !readOnly && !todo.isCompleted,
        { DaveCompactTaskCard(todo, {}, {}, readOnly = true) }, onDragPosition, onDragFinished, dragGroup)
    Box(modifier = modifier.fillMaxWidth().height(64.dp).then(lift).clip(RoundedCornerShape(9.dp))) {
        if (onEdit != null || onDelete != null) {
            Row(modifier = Modifier.align(Alignment.CenterEnd).height(64.dp)) {
                onEdit?.let { action ->
                    Box(
                        modifier = Modifier.width(38.dp).height(64.dp).clip(RoundedCornerShape(8.dp)).background(DavePalette.Habit).clickable { settleSwipe(0f, action) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Edit, "编辑待办", tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
                onDelete?.let { action ->
                    Box(
                        modifier = Modifier.width(38.dp).height(64.dp).clip(RoundedCornerShape(8.dp)).background(DavePalette.Urgent).clickable { settleSwipe(0f, action) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.DeleteOutline, "删除待办", tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
            }
        }
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
            .zIndex(if (dragging) 4f else 0f)
            .graphicsLayer {
                alpha = if (dragging && dragLayer != null) 0f else 1f
                translationX = if (dragLayer == null) dragOffset.x else 0f
                translationY = if (dragLayer == null) dragOffset.y else 0f
                shadowElevation = if (dragging) 14.dp.toPx() else 0f
            }
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .background(cardColor, RoundedCornerShape(9.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(14.dp)
                .height(64.dp)
                .background(stripe, RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp))
                .semantics { contentDescription = "长按拖动待办" },
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .then(if (readOnly) Modifier else Modifier.pointerInput(todo.id, todo.isCompleted) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val maxRight = if (todo.isCompleted || onTogglePriority == null) 0f else priorityThresholdPx * 1.55f
                            swipeOffset = (swipeOffset + amount).coerceIn(-actionWidthPx, maxRight)
                        },
                        onDragEnd = {
                            when {
                                swipeOffset >= priorityThresholdPx && !todo.isCompleted && onTogglePriority != null -> settleSwipe(0f, onTogglePriority)
                                swipeOffset <= -actionWidthPx * .48f -> settleSwipe(-actionWidthPx)
                                else -> settleSwipe(0f)
                            }
                        },
                        onDragCancel = { settleSwipe(0f) },
                    )
                })
                .then(if (readOnly) Modifier else Modifier.clickable {
                    if (swipeOffset < -1f) settleSwipe(0f)
                    else onToggleCompletion()
                }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier.padding(start = 8.dp).size(23.dp).border(2.dp, todo.accentColor?.let(::Color) ?: DavePalette.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                DaveDrawnCheck(todo.accentColor?.let(::Color) ?: DavePalette.Completed, Modifier.size(19.dp))
            }
        }
        val titleParts = remember(todo.title) { splitDaveTaskText(todo.title) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 7.dp).alpha(if (completed) .68f else 1f)) {
            DaveTitle(
                text = titleParts.title,
                color = todo.accentColor?.let(::Color) ?: DavePalette.Ink,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
            )
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                Text(if (completed) "1/1" else "0/1", color = todo.accentColor?.let(::Color) ?: DavePalette.Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                if (todoTimeLabel(todo).isNotEmpty()) DaveTodoTime(todo, { onEdit?.invoke() }, compact = true)
                Text(
                    (listOf(todoTypeTag(todo)) + listOfNotNull(todoDeadlineTag(todo))).distinct().joinToString(" ") { "#$it" },
                    color = todo.accentColor?.let(::Color) ?: DavePalette.Meta,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (completed) Text("CLEAR", color = DavePalette.Completed, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 5.dp).rotate(7f))
        }
        }
        }
    }
}

@Composable
fun DaveCompactHabitCard(
    title: String,
    count: Int,
    targetCount: Int,
    period: HabitPeriod,
    color: Long,
    checkedOnDate: Boolean = count > 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragId: String = title,
    dragGroup: String = "habit-home",
    onDragFinished: ((Offset) -> Unit)? = null,
    intervalDays: Int = 1,
) {
    val progressColor = Color(color)
    val complete = if (period == HabitPeriod.DAILY) count >= targetCount else checkedOnDate
    val visuallyComplete = complete
    val titleParts = remember(title) { splitDaveTaskText(title) }
    val lift = rememberDaveLiftModifier(
        key = dragId,
        enabled = onDragFinished != null,
        draw = { DaveCompactHabitCard(title, count, targetCount, period, color, checkedOnDate, {}, dragId = dragId, intervalDays = intervalDays) },
        onPosition = null,
        onDrop = onDragFinished,
        group = dragGroup,
    )
    Row(
        modifier = modifier.then(lift).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(9.dp)).background(DavePalette.Card, RoundedCornerShape(9.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(12.dp).height(64.dp).background(if (visuallyComplete) DavePalette.Completed else DavePalette.Habit, RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)))
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(23.dp)
                .then(
                    Modifier.border(2.dp, progressColor, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (visuallyComplete) DaveDrawnCheck(progressColor, Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 7.dp)) {
            DaveTitle(titleParts.title, color = progressColor, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textDecoration = if (visuallyComplete) TextDecoration.LineThrough else TextDecoration.None)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                Text("$count/$targetCount", color = progressColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("#${habitPeriodTag(period, intervalDays)}", color = progressColor, fontSize = 9.sp, maxLines = 1)
            }
        }
        if (visuallyComplete) Text("CLEAR", color = DavePalette.Completed, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 5.dp).rotate(7f))
    }
}

@Composable
fun DaveCompactDraftCard(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        bringIntoViewRequester.bringIntoView()
    }
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).background(DavePalette.Card, RoundedCornerShape(9.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(8.dp).height(64.dp).background(DavePalette.Normal, RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focusRequester).bringIntoViewRequester(bringIntoViewRequester),
            textStyle = TextStyle(color = DavePalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(DavePalette.HeaderGreen),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (value.isBlank()) onCancelEmpty() else onConfirm()
                focusManager.clearFocus()
            }),
            decorationBox = { input ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("输入日程…", color = DavePalette.Ink.copy(alpha = .36f), fontSize = 13.sp)
                    input()
                }
            },
        )
        Box(
            modifier = Modifier.size(31.dp).clip(CircleShape).background(DavePalette.Completed, CircleShape).clickable(enabled = value.isNotBlank(), onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.padding(start = 5.dp, end = 6.dp).size(31.dp).clip(CircleShape).background(DavePalette.CardMuted, CircleShape).clickable {
                focusManager.clearFocus()
                onCancelEmpty()
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "取消新建", tint = DavePalette.Ink, modifier = Modifier.size(17.dp))
        }
    }
}
