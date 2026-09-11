package com.fishking.core.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import com.fishking.core.model.*

data class TodoPlanningEditor(val scope: TodoPlanScope, val onSelected: (TodoPlanScope) -> Unit,
    val deadline: java.time.LocalDate? = null, val onDeadline: (java.time.LocalDate) -> Unit = {},
    val date: java.time.LocalDate = java.time.LocalDate.now(), val onDate: (java.time.LocalDate) -> Unit = {})
val LocalTodoPlanning = staticCompositionLocalOf<TodoPlanningEditor?> { null }

@Composable
internal fun DavePlanningOption(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val editor = LocalTodoPlanning.current ?: return
    val selected = editor.scope != TodoPlanScope.DATE
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (selected) DavePalette.HeaderGreen else Color.White.copy(alpha = .5f))
            .border(1.dp, if (selected) DavePalette.HeaderGreen.copy(alpha = .65f) else DavePalette.Divider, androidx.compose.foundation.shape.CircleShape)
            .clickable { onExpandedChange(!expanded) }
            .semantics { contentDescription = if (selected) "安排待办范围：${editor.scope.tag}" else "安排待办范围" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = if (selected) Color.White else DavePalette.Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun DavePlanningPanel(onDismiss: () -> Unit) {
    val editor = LocalTodoPlanning.current ?: return
    var chooseDateFor by remember { mutableStateOf<TodoPlanScope?>(null) }
    chooseDateFor?.let { target ->
        DaveCalendar(
            selected = if (target == TodoPlanScope.DEADLINE) editor.deadline ?: editor.date else editor.date,
            onSelect = {
                editor.onSelected(target)
                if (target == TodoPlanScope.DEADLINE) editor.onDeadline(it) else editor.onDate(it)
                chooseDateFor = null
                onDismiss()
            },
            onDismiss = { chooseDateFor = null },
            markDescription = if (target == TodoPlanScope.DEADLINE) "截止日期" else "指定日期",
        )
    }
    Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    .clip(RoundedCornerShape(16.dp)).background(DavePalette.Card)
                    .border(1.dp, DavePalette.Divider, RoundedCornerShape(16.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf(
                    listOf(TodoPlanScope.DEADLINE, TodoPlanScope.WEEK, TodoPlanScope.MONTH),
                    listOf(TodoPlanScope.THREE_MONTHS, TodoPlanScope.HALF_YEAR, TodoPlanScope.YEAR),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { scope ->
                            val active = editor.scope == scope
                            Text(
                                if (scope == TodoPlanScope.DEADLINE) "截止日期" else scope.tag,
                                color = if (active) Color.White else DavePalette.Ink,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (active) DavePalette.HeaderGreen else Color.White.copy(alpha = .62f))
                                    .clickable {
                                        if (scope == TodoPlanScope.DEADLINE) chooseDateFor = TodoPlanScope.DEADLINE
                                        else { editor.onSelected(scope); onDismiss() }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 9.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Text(
                    "指定日期",
                    color = if (editor.scope == TodoPlanScope.DATE) Color.White else DavePalette.Ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (editor.scope == TodoPlanScope.DATE) DavePalette.HeaderGreen else Color.White.copy(alpha = .62f))
                        .clickable { chooseDateFor = TodoPlanScope.DATE }
                        .padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                )
            }
}

internal fun todoTypeTag(todo: TodoOccurrence): String = when {
    todo.fromLifeGoal -> "人生清单"
    todo.planScope == TodoPlanScope.DEADLINE && todo.planDeadline != null -> todo.planDeadline!!.let { "${it.monthValue}月${it.dayOfMonth}日前待办" }
    todo.planScope != TodoPlanScope.DATE -> todo.planScope.tag
    todo.priority == TodoPriority.URGENT -> "紧急"
    else -> "待办"
}

internal fun todoDeadlineTag(todo: TodoOccurrence, today: java.time.LocalDate = java.time.LocalDate.now()): String? {
    val date = todo.planDeadline ?: return null
    if (todo.isCompleted) return null
    val remaining = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when { remaining > 0 -> "剩余${remaining}天"; remaining == 0L -> "今天到期"; else -> "已超期${-remaining}天" }
}
