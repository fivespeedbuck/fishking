package com.fishking.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishking.core.model.TodoOccurrence
import java.time.format.DateTimeFormatter

val LocalTodoTimeEditor = androidx.compose.runtime.staticCompositionLocalOf<((TodoOccurrence) -> Unit)?> { null }

internal fun todoTimeLabel(todo: TodoOccurrence): String {
    if (todo.isCompleted) return todo.completedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalTime()
        ?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()
    val reminders = todo.displayReminders.filter { it.isEnabled }.sortedWith(compareBy({ it.dayOffset }, { it.localTime }))
    val first = reminders.firstOrNull() ?: return ""
    val offset = when { first.dayOffset < 0 -> "前${-first.dayOffset}天 "
        first.dayOffset > 0 -> "后${first.dayOffset}天 "
        else -> "" }
    return offset + first.localTime.format(DateTimeFormatter.ofPattern("HH:mm")) + if (reminders.size > 1) " +${reminders.size - 1}" else ""
}

@Composable
internal fun DaveTodoTime(todo: TodoOccurrence, onClick: () -> Unit, compact: Boolean = false, modifier: Modifier = Modifier) {
    val editor = LocalTodoTimeEditor.current
    if (todoTimeLabel(todo).isEmpty()) return
    Text(todoTimeLabel(todo), color = todo.accentColor?.let(::Color) ?: DavePalette.Meta,
        fontSize = if (compact) 11.sp else 14.sp,
        modifier = modifier.then(if (todo.isCompleted) Modifier else Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (editor != null) editor(todo) else onClick() })
            .semantics { contentDescription = if (todo.isCompleted) "完成时间" else "设置待办提醒时间" })
}
