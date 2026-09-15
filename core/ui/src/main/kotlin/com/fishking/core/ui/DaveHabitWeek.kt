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
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
    editingHabitId: String? = null,
    habitEditor: (@Composable () -> Unit)? = null,
) {
    val isCurrentWeek = !today.isBefore(snapshot.weekStart) && !today.isAfter(snapshot.weekEnd)
    val skin = LocalHabitWeekSkin.current
    val unified = skin == HabitWeekSkin.UNIFIED_CARD
    val panelShape = RoundedCornerShape(13.dp)
    androidx.compose.runtime.CompositionLocalProvider(LocalDaveReorderCommit provides onReorderHabit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(
                if (unified) DavePalette.Card
                else if (isCurrentWeek) DavePalette.CurrentWeek else DavePalette.OtherWeek,
            )
            .border(1.dp, DavePalette.WeekBorder, panelShape),
    ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = if (unified) Arrangement.Top else Arrangement.spacedBy(8.dp),
    ) {
        WeekDayHeader(snapshot.weekStart, today, isCurrentWeek, unified)
        if (recurringTodos.isNotEmpty()) {
            recurringTodos
                .groupBy { it.seriesId ?: it.id }
                .values
                .sortedBy { series -> series.minOf { it.position } }
                .forEach { series ->
                    RecurringTodoWeekRow(series, snapshot.weekStart, today, onToggleRecurringTodo, onEditRecurringTodo, unified)
                }
        }
        snapshot.items.forEach { habit ->
            key(habit.id) {
                if (isCurrentWeek && habit.id == editingHabitId && habitEditor != null) {
                    habitEditor()
                } else {
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
                        reorderEnabled = isCurrentWeek && editingHabitId == null,
                        unified = unified,
                    )
                }
            }
        }
        footer?.invoke()
    }
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
    unified: Boolean,
) {
    val ordered = occurrences.sortedBy { it.displayDate }
    val representative = ordered.first()
    val color = representative.accentColor?.let(::Color) ?: DavePalette.Habit
    val byDate = ordered.associateBy { it.displayDate }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(if (unified) Color.Transparent else DavePalette.Card)
            .habitTodayStripe(weekStart, today, unified)
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
private fun WeekDayHeader(weekStart: LocalDate, today: LocalDate, isCurrentWeek: Boolean, unified: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .habitTodayStripe(weekStart, today, unified)
            .padding(horizontal = 10.dp),
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
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (!unified && date == today) DavePalette.HeaderGreen.copy(alpha = .13f) else Color.Transparent)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date.dayOfWeek.shortChinese(), color = DavePalette.Meta, fontSize = 10.sp)
                Box(contentAlignment = Alignment.BottomEnd) {
                    Text(
                        date.dayOfMonth.toString(),
                        color = if (date == today) DavePalette.HeaderGreenDark else DavePalette.Ink,
                        fontSize = 12.sp,
                        fontWeight = if (date == today) FontWeight.ExtraBold else FontWeight.Bold,
                        modifier = Modifier.padding(end = if (date == today) 7.dp else 0.dp),
                    )
                    if (date == today) Text(
                        "今",
                        color = DavePalette.HeaderGreenDark,
                        fontSize = 7.sp,
                        lineHeight = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
    unified: Boolean = false,
    liftedPreview: Boolean = false,
) {
    val actionWidthDp = if (allowActions) 132.dp else 48.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidthPx = with(density) { actionWidthDp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember(habit.id, habit.weekStart) { mutableFloatStateOf(0f) }
    var settleJob by remember(habit.id, habit.weekStart) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var actionsVisible by remember(habit.id, habit.weekStart) { mutableStateOf(false) }
    val displayDate = remember(habit.weekStart, today) {
        maxOf(habit.weekStart, minOf(today, habit.weekStart.plusDays(6)))
    }
    val displayRule = remember(habit, displayDate) { habit.ruleOn(displayDate) }
    val dayPresentations = remember(habit, today) {
        (0..6).map { dayOffset ->
            val date = habit.weekStart.plusDays(dayOffset.toLong())
            val record = habit.records.firstOrNull { it.date == date }
            val state = habit.dayState(date)
            val cutoff = minOf(today, when (state.rule.period) {
                HabitPeriod.MONTHLY -> java.time.YearMonth.from(date).atEndOfMonth()
                else -> habit.weekStart.plusDays(6)
            })
            val periodTargetReached = when (state.rule.period) {
                HabitPeriod.WEEKLY -> cutoff >= habit.weekStart && habit.records.count {
                    it.date in habit.weekStart..cutoff && it.count > 0
                } >= state.rule.targetCount
                HabitPeriod.MONTHLY -> cutoff >= java.time.YearMonth.from(date).atDay(1) && habit.records.count {
                    java.time.YearMonth.from(it.date) == java.time.YearMonth.from(date) &&
                        !it.date.isAfter(cutoff) && it.count > 0
                } >= state.rule.targetCount
                else -> false
            }
            HabitDayPresentation(
                date = date,
                period = state.rule.period,
                count = state.actualCount,
                targetCount = state.rule.targetCount,
                color = state.rule.color,
                isBackfilled = record?.isBackfilled == true,
                enabled = !date.isAfter(today),
                // The trajectory is a calendar, not the home-page due queue.
                // A missed fixed-cadence slot can remain due on Home, but it
                // must not turn every later non-slot day into a planned circle.
                planned = state.isPlannedDate && !(periodTargetReached && state.actualCount == 0),
                title = state.rule.title,
            )
        }
    }
    fun settle(target: Float, after: (() -> Unit)? = null) {
        settleJob?.cancel()
        if (target >= 0f) actionsVisible = false
        settleJob = scope.launch {
            animate(offsetX.floatValue, target, animationSpec = tween(180)) { value, _ -> offsetX.floatValue = value }
            actionsVisible = target < 0f
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
                unified = unified,
                liftedPreview = true,
            )
        },
        onPosition = null,
        onDrop = {},
        group = "habit-order",
    )
    Box(
        modifier = Modifier.fillMaxWidth().then(lift).clip(RoundedCornerShape(7.dp))
            .background(if (!unified || liftedPreview || actionsVisible || offsetX.floatValue != 0f) DavePalette.Card else Color.Transparent)
            .habitTodayStripe(habit.weekStart, today, unified && !liftedPreview && !actionsVisible && offsetX.floatValue == 0f),
    ) {
        if ((allowActions || onDeleteHabit != null) && actionsVisible) {
            DaveCardActionStrip(
                actions = buildList {
                    if (allowActions) {
                        add(DaveCardAction(
                            icon = Icons.Outlined.Edit,
                            description = "编辑打卡项目",
                            color = DavePalette.HeaderGreen,
                            onClick = { settle(0f) { onEdit(habit) } },
                        ))
                        add(DaveCardAction(
                            icon = if (habit.isSkipped) Icons.Outlined.Replay else Icons.Outlined.SkipNext,
                            description = if (habit.isSkipped) "恢复本周要求" else "跳过本周要求",
                            color = DavePalette.Plan,
                            onClick = { settle(0f) { onToggleSkip(habit.id, habit.weekStart) } },
                        ))
                    }
                    add(DaveCardAction(
                        icon = if (allowActions) Icons.Outlined.StopCircle else Icons.Outlined.DeleteOutline,
                        description = if (allowActions) "从本周起结束打卡项目" else "删除此习惯",
                        color = DavePalette.Urgent,
                        onClick = { settle(0f) { if (allowActions) onEndFromWeek(habit.id, habit.weekStart) else onDeleteHabit?.invoke(habit) } },
                    ))
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(actionWidthDp)
                    .height(66.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .offset { IntOffset(offsetX.floatValue.roundToInt(), 0) }
                // The row owns its today stripe; editors between rows never inherit it.
                // The revealed action icons share this existing card's background.
                .background(if (!unified || liftedPreview || actionsVisible || offsetX.floatValue != 0f) DavePalette.Card else Color.Transparent)
                .then(
                    if (allowActions || onDeleteHabit != null) Modifier.pointerInput(habit.id, habit.weekStart) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                settleJob?.cancel()
                                offsetX.floatValue = (offsetX.floatValue + amount).coerceIn(-actionWidthPx, 0f)
                                actionsVisible = offsetX.floatValue < -1f
                            },
                            onDragEnd = {
                                settle(if (offsetX.floatValue <= -actionWidthPx * .42f) -actionWidthPx else 0f)
                            },
                            onDragCancel = { settle(0f) },
                        )
                    } else Modifier,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val visibleTitle = remember(displayRule.title) { splitDaveTaskText(displayRule.title).title }
            Column(modifier = Modifier.width(92.dp).padding(end = 3.dp)) {
                Text(
                    text = visibleTitle,
                    color = Color(displayRule.color).copy(alpha = if (historical) .70f else 1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textDecoration = if (habit.isSkipped) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.padding(end = 2.dp),
                )
                if (displayRule.period != HabitPeriod.DAILY) Text(
                    text = when (displayRule.period) {
                        HabitPeriod.WEEKLY,
                        HabitPeriod.MONTHLY,
                        -> habitPeriodProgress(habit.effectiveCountFor(displayDate), displayRule.targetCount, displayRule.period)
                        HabitPeriod.EVERY_N_DAYS -> "每${displayRule.intervalDays}天"
                        HabitPeriod.AFTER_COMPLETION_N_DAYS -> "间隔${displayRule.intervalDays}天"
                        HabitPeriod.DAILY -> ""
                    },
                    color = Color(displayRule.color), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            dayPresentations.forEach { day ->
                HabitDayCircle(
                    period = day.period,
                    count = day.count,
                    targetCount = day.targetCount,
                    color = Color(day.color),
                    isBackfilled = day.isBackfilled,
                    enabled = day.enabled,
                    planned = day.planned,
                    isToday = day.date == today,
                    individualTodayHighlight = !unified,
                    description = "${day.title} ${day.date.monthValue}月${day.date.dayOfMonth}日",
                    onClick = { onToggle(habit.id, day.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (habit.isSkipped) {
            // Draw-only overlay: it has no pointer input, so every day circle below remains tappable.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.floatValue.roundToInt(), 0) }
                    .height(66.dp)
                    .align(Alignment.Center),
            ) {
                drawLine(
                    color = Color(displayRule.color).copy(alpha = if (historical) .70f else 1f),
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

/** Applied only to calendar/trajectory rows, never the full panel or an editor. */
private fun Modifier.habitTodayStripe(weekStart: LocalDate, today: LocalDate, enabled: Boolean): Modifier {
    val offset = today.toEpochDay() - weekStart.toEpochDay()
    if (!enabled || offset !in 0L..6L) return this
    return drawBehind {
        val horizontalInset = 10.dp.toPx()
        val titleWidth = 92.dp.toPx()
        val dayWidth = (size.width - horizontalInset * 2f - titleWidth) / 7f
        drawRect(
            color = Color(0xFFEAF7F0),
            topLeft = androidx.compose.ui.geometry.Offset(
                horizontalInset + titleWidth + dayWidth * offset.toFloat(),
                0f,
            ),
            size = androidx.compose.ui.geometry.Size(dayWidth, size.height),
        )
    }
}

private data class HabitDayPresentation(
    val date: LocalDate,
    val period: HabitPeriod,
    val count: Int,
    val targetCount: Int,
    val color: Long,
    val isBackfilled: Boolean,
    val enabled: Boolean,
    val planned: Boolean,
    val title: String,
)

internal fun habitBackfillLabelColor(color: Color, enabled: Boolean): Color =
    color.copy(alpha = if (enabled) 1f else .4f)

@Composable
private fun HabitDayCircle(
    period: HabitPeriod,
    count: Int,
    targetCount: Int,
    color: Color,
    isBackfilled: Boolean,
    enabled: Boolean,
    planned: Boolean,
    isToday: Boolean,
    individualTodayHighlight: Boolean,
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
        HabitPeriod.EVERY_N_DAYS,
        HabitPeriod.AFTER_COMPLETION_N_DAYS,
        -> count > 0
    }
    val emptyMark = !planned && count == 0
    val visualColor = color
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (individualTodayHighlight && isToday) DavePalette.HeaderGreen.copy(alpha = .10f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            val targetModifier = Modifier
                .align(Alignment.Center)
                .size(if (emptyMark) 33.dp else 29.dp)
                .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
                .semantics {
                        contentDescription = buildString {
                            append(description)
                            if (period == HabitPeriod.DAILY) append(" $count/$targetCount")
                            else append(if (count > 0) " 已打卡" else " 未打卡")
                            if (!planned && count == 0) append(" 空集")
                            if (isBackfilled) append(" 补记")
                        }
                        if (!enabled) disabled()
                    }
            Box(
                modifier = if (emptyMark) targetModifier
                else targetModifier.clip(CircleShape).border(2.dp, visualColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (complete) {
                    DaveDrawnCheck(color, Modifier.size(23.dp))
                } else if (!planned) {
                    Canvas(Modifier.size(33.dp)) {
                        val emptyColor = color.copy(alpha = .75f)
                        val outlineStroke = 2.dp.toPx()
                        val slashRadius = 16.5.dp.toPx()
                        val slashAxis = slashRadius / kotlin.math.sqrt(2f)
                        drawCircle(
                            color = emptyColor,
                            radius = 13.5.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(outlineStroke),
                        )
                        drawLine(
                            color = emptyColor,
                            start = androidx.compose.ui.geometry.Offset(center.x - slashAxis, center.y + slashAxis),
                            end = androidx.compose.ui.geometry.Offset(center.x + slashAxis, center.y - slashAxis),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            if (period == HabitPeriod.DAILY) {
                Text(
                    text = "$count/$targetCount" + if (isBackfilled) " 补" else "",
                    color = habitBackfillLabelColor(color, enabled),
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
            if (isBackfilled && period != HabitPeriod.DAILY) {
                Text(
                    text = "补",
                    color = habitBackfillLabelColor(color, enabled),
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
