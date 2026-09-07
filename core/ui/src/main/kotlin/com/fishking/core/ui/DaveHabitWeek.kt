package com.fishking.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitWeekSnapshot
import com.fishking.core.model.TodoOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DaveHabitWeekPanel(
    snapshot: HabitWeekSnapshot,
    today: LocalDate,
    recurringTodos: List<TodoOccurrence> = emptyList(),
    onToggleRecurringTodo: (String) -> Unit = {},
    onEditRecurringTodo: (TodoOccurrence) -> Unit = {},
    onToggle: (habitId: String, date: LocalDate) -> Unit,
    onEdit: (HabitWeekItem) -> Unit,
    onToggleSkip: (habitId: String, weekStart: LocalDate) -> Unit,
    onEndFromWeek: (habitId: String, weekStart: LocalDate) -> Unit,
    onDeleteHabit: ((HabitWeekItem) -> Unit)? = null,
    onReorderHabit: (sourceId: String, targetId: String, after: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val isCurrentWeek = !today.isBefore(snapshot.weekStart) && !today.isAfter(snapshot.weekEnd)
    androidx.compose.runtime.CompositionLocalProvider(LocalDaveReorderCommit provides onReorderHabit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isCurrentWeek) DavePalette.CurrentWeek else DavePalette.OtherWeek,
                RoundedCornerShape(13.dp),
            )
            .border(1.dp, DavePalette.WeekBorder, RoundedCornerShape(13.dp))
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WeekDayHeader(snapshot.weekStart, isCurrentWeek)
        if (recurringTodos.isNotEmpty()) {
            recurringTodos
                .groupBy { it.seriesId ?: it.id }
                .values
                .sortedBy { series -> series.minOf { it.position } }
                .forEach { series ->
                    RecurringTodoWeekRow(series, snapshot.weekStart, today, onToggleRecurringTodo, onEditRecurringTodo)
                }
        }
        snapshot.items.forEach { habit ->
            HabitWeekRow(
                habit = habit,
                today = today,
                allowActions = isCurrentWeek,
                onToggle = onToggle,
                onEdit = onEdit,
                onToggleSkip = onToggleSkip,
                onEndFromWeek = onEndFromWeek,
                historical = !isCurrentWeek,
                onDeleteHabit = onDeleteHabit,
                reorderEnabled = isCurrentWeek,
            )
        }
        footer?.invoke()
    }
    }
}

/**
 * Projects recurring todos into the habit grid without creating a second habit fact.
 * Every tappable mark is the original todo occurrence, so completion remains shared
 * with the home page and monthly/weekly recurrence keeps its actual scheduled dates.
 */
@Composable
private fun RecurringTodoWeekRow(
    occurrences: List<TodoOccurrence>,
    weekStart: LocalDate,
    today: LocalDate,
    onToggle: (String) -> Unit,
    onEdit: (TodoOccurrence) -> Unit,
) {
    val ordered = occurrences.sortedBy { it.displayDate }
    val representative = ordered.first()
    val color = representative.accentColor?.let(::Color) ?: DavePalette.Habit
    val byDate = ordered.associateBy { it.displayDate }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DavePalette.Card)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(92.dp).padding(end = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    representative.title,
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑周期待办",
                    tint = color,
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onEdit(representative) }.padding(3.dp),
                )
            }
        }
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val todo = byDate[date]
            Box(Modifier.weight(1f).height(58.dp), contentAlignment = Alignment.Center) {
                if (todo != null) {
                    Box(
                        Modifier
                            .size(29.dp)
                            .clip(CircleShape)
                            .border(2.dp, color, CircleShape)
                            .clickable(enabled = !date.isAfter(today), role = Role.Checkbox) { onToggle(todo.id) }
                            .semantics {
                                contentDescription = "${todo.title} ${date.monthValue}月${date.dayOfMonth}日" +
                                    if (todo.isCompleted) " 已完成" else " 未完成"
                                if (date.isAfter(today)) disabled()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (todo.isCompleted) DaveDrawnCheck(color, Modifier.size(23.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayHeader(weekStart: LocalDate, isCurrentWeek: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${weekStart.year}年${weekStart.monthValue}月", color = DavePalette.HeaderGreenDark, fontSize = 12.sp)
            Text("第${(weekStart.dayOfMonth - 1) / 7 + 1}周" + if (isCurrentWeek) " · 本周" else "",
                color = DavePalette.HeaderGreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date.dayOfWeek.shortChinese(), color = DavePalette.Meta, fontSize = 10.sp)
                Text(date.dayOfMonth.toString(), color = DavePalette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HabitWeekRow(
    habit: HabitWeekItem,
    today: LocalDate,
    allowActions: Boolean,
    onToggle: (habitId: String, date: LocalDate) -> Unit,
    onEdit: (HabitWeekItem) -> Unit,
    onToggleSkip: (habitId: String, weekStart: LocalDate) -> Unit,
    onEndFromWeek: (habitId: String, weekStart: LocalDate) -> Unit,
    historical: Boolean,
    onDeleteHabit: ((HabitWeekItem) -> Unit)? = null,
    reorderEnabled: Boolean = false,
) {
    val visibleTitle = remember(habit.title) { splitDaveTaskText(habit.title).title }
    val actionWidthDp = if (allowActions) 162.dp else 54.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidthPx = with(density) { actionWidthDp.toPx() }
    val scope = rememberCoroutineScope()
    var offsetX by remember(habit.id, habit.weekStart) { mutableFloatStateOf(0f) }
    fun settle(target: Float, after: (() -> Unit)? = null) {
        scope.launch {
            animate(offsetX, target, animationSpec = tween(180)) { value, _ -> offsetX = value }
            after?.invoke()
        }
    }
    val lift = rememberDaveLiftModifier(
        key = habit.id,
        enabled = reorderEnabled,
        draw = {
            HabitWeekRow(
                habit = habit,
                today = today,
                allowActions = false,
                onToggle = { _, _ -> },
                onEdit = {},
                onToggleSkip = { _, _ -> },
                onEndFromWeek = { _, _ -> },
                historical = historical,
                reorderEnabled = false,
            )
        },
        onPosition = null,
        onDrop = {},
        group = "habit-order",
    )
    Box(modifier = Modifier.fillMaxWidth().then(lift).clip(RoundedCornerShape(7.dp))) {
        if ((allowActions || onDeleteHabit != null) && offsetX < -1f) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).width(actionWidthDp).height(66.dp),
            ) {
                if (allowActions) HabitSwipeAction(
                    color = DavePalette.HeaderGreen,
                    description = "编辑打卡项目",
                    icon = Icons.Outlined.Edit,
                    onClick = { settle(0f) { onEdit(habit) } },
                )
                if (allowActions) HabitSwipeAction(
                    color = DavePalette.Meta,
                    description = if (habit.isSkipped) "恢复本周要求" else "跳过本周要求",
                    icon = if (habit.isSkipped) Icons.Outlined.Replay else Icons.Outlined.SkipNext,
                    onClick = { settle(0f) { onToggleSkip(habit.id, habit.weekStart) } },
                )
                HabitSwipeAction(
                    color = DavePalette.Urgent,
                    description = if (onDeleteHabit != null) "删除此习惯" else "从本周起结束打卡项目",
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = { settle(0f) { if (onDeleteHabit != null) onDeleteHabit(habit) else onEndFromWeek(habit.id, habit.weekStart) } },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(DavePalette.Card)
                .then(
                    if (allowActions || onDeleteHabit != null) Modifier.pointerInput(habit.id, habit.weekStart) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                offsetX = (offsetX + amount).coerceIn(-actionWidthPx, 0f)
                            },
                            onDragEnd = {
                                settle(if (offsetX <= -actionWidthPx * .42f) -actionWidthPx else 0f)
                            },
                            onDragCancel = { settle(0f) },
                        )
                    } else Modifier,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(92.dp).padding(end = 3.dp)) {
                Text(
                    text = visibleTitle,
                    color = Color(habit.color).copy(alpha = if (historical) .70f else 1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textDecoration = if (habit.isSkipped) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.padding(end = 2.dp),
                )
                if (habit.period != HabitPeriod.DAILY) Text(
                    text = "${habit.effectiveCountFor(habit.weekStart.plusDays(3))}/${habit.targetCount}",
                    color = Color(habit.color), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            repeat(7) { offset ->
                val date = habit.weekStart.plusDays(offset.toLong())
                val record = habit.records.firstOrNull { it.date == date }
                val enabled = !date.isAfter(today) && habit.isScheduledOn(date)
                HabitDayCircle(
                    period = habit.period,
                    count = record?.count ?: 0,
                    targetCount = habit.targetCount,
                    color = Color(habit.color),
                    isBackfilled = record?.isBackfilled == true,
                    enabled = enabled,
                    description = "${habit.title} ${date.monthValue}月${date.dayOfMonth}日",
                    onClick = { onToggle(habit.id, date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (habit.isSkipped) {
            // Draw-only overlay: it has no pointer input, so every day circle below remains tappable.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .height(66.dp)
                    .align(Alignment.Center),
            ) {
                drawLine(
                    color = Color(habit.color).copy(alpha = if (historical) .70f else 1f),
                    start = androidx.compose.ui.geometry.Offset(8.dp.toPx(), size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        // The decorative canvas has no pointer input, so skipped circles remain tappable.
    }
}

@Composable
private fun HabitSwipeAction(
    color: Color,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
            modifier = Modifier
                .width(54.dp)
                .height(64.dp)
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun HabitDayCircle(
    period: HabitPeriod,
    count: Int,
    targetCount: Int,
    color: Color,
    isBackfilled: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val complete = when (period) {
        HabitPeriod.DAILY -> count >= targetCount
        // A weekly target is completed by the row-level distinct-day total.
        // Each day circle only represents whether that real date was checked.
        HabitPeriod.WEEKLY -> count > 0
        HabitPeriod.MONTHLY -> count > 0
    }
    val outline = if (count > 0) color else DavePalette.Ink.copy(alpha = .28f)
    Box(
        modifier = modifier.height(58.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(29.dp)
                    .clip(CircleShape)
                    .then(
                        Modifier.border(2.dp, color, CircleShape),
                    )
                    .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
                    .semantics {
                        contentDescription = buildString {
                            append(description)
                            if (period == HabitPeriod.DAILY) append(" $count/$targetCount")
                            else append(if (count > 0) " 已打卡" else " 未打卡")
                            if (isBackfilled) append(" 补记")
                        }
                        if (!enabled) disabled()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (complete) DaveDrawnCheck(color, Modifier.size(23.dp))
            }
            if (period == HabitPeriod.DAILY) {
                Text(
                    text = "$count/$targetCount" + if (isBackfilled) " 补" else "",
                    color = color.copy(alpha = if (enabled) 1f else .4f),
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .zIndex(2f)
                        .background(DavePalette.Card.copy(alpha = .92f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 1.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (isBackfilled && period == HabitPeriod.WEEKLY) {
                Text(
                    text = "补",
                    color = DavePalette.Meta,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .zIndex(3f)
                        .background(DavePalette.Card.copy(alpha = .92f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 1.dp),
                )
            }
        }
    }
}

private val rangeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.SIMPLIFIED_CHINESE)

private fun LocalDate.weekTitle(): String {
    val weekOfMonth = (dayOfMonth - 1) / 7 + 1
    return "${year}年${monthValue}月 第${weekOfMonth}周"
}

private fun DayOfWeek.shortChinese(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
