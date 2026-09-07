package com.fishking.core.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import com.fishking.core.model.*

data class TodoPlanningEditor(val scope: TodoPlanScope, val onSelected: (TodoPlanScope) -> Unit,
    val deadline: java.time.LocalDate? = null, val onDeadline: (java.time.LocalDate) -> Unit = {})
val LocalTodoPlanning = staticCompositionLocalOf<TodoPlanningEditor?> { null }

@Composable
internal fun DavePlanningOption() {
    val editor = LocalTodoPlanning.current ?: return
    var expanded by remember { mutableStateOf(false) }
    var chooseDate by remember { mutableStateOf(false) }
    if (chooseDate) DaveCalendar(editor.deadline ?: java.time.LocalDate.now(), { editor.onSelected(TodoPlanScope.DEADLINE); editor.onDeadline(it); chooseDate = false }, { chooseDate = false })
    Box {
        val selected = editor.scope != TodoPlanScope.DATE
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (selected) DavePalette.HeaderGreen else Color.White.copy(alpha = .5f))
                .border(1.dp, if (selected) DavePalette.HeaderGreen.copy(alpha = .65f) else DavePalette.Divider, androidx.compose.foundation.shape.CircleShape)
                .clickable { expanded = true }
                .semantics { contentDescription = if (selected) "安排待办范围：${editor.scope.tag}" else "安排待办范围" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = if (selected) Color.White else DavePalette.Ink, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            listOf(TodoPlanScope.DEADLINE, TodoPlanScope.WEEK, TodoPlanScope.MONTH, TodoPlanScope.THREE_MONTHS, TodoPlanScope.HALF_YEAR, TodoPlanScope.YEAR).forEach { scope -> DropdownMenuItem(text = { Text(if (scope == TodoPlanScope.DEADLINE) "截止日期" else scope.tag) },
                onClick = { if (scope == TodoPlanScope.DEADLINE) chooseDate = true else editor.onSelected(scope); expanded = false }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("恢复为指定日期待办") }, onClick = { editor.onSelected(TodoPlanScope.DATE); expanded = false })
        }
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
