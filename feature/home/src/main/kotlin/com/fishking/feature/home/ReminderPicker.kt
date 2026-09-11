package com.fishking.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fishking.core.model.ReminderSelectionRules
import com.fishking.core.ui.DavePalette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal data class ReminderPickerRequest(
    val todoDate: LocalDate,
    val deadline: LocalDate?,
    val previousDate: LocalDate?,
    val previousTime: LocalTime?,
    val onSelect: (LocalTime, Int) -> Unit,
)

/** Compact paper/green date and time surface; no platform clock-dial theme. */
@Composable
internal fun ReminderPicker(request: ReminderPickerRequest, onDismiss: () -> Unit) {
    var now by remember(request) { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(request) {
        while (true) { kotlinx.coroutines.delay(15_000); now = LocalDateTime.now() }
    }
    val dates = ReminderSelectionRules.validDates(now.toLocalDate(), request.todoDate, request.deadline)
        .filter { ReminderSelectionRules.validTimes(it, now, 1).isNotEmpty() }
    var date by remember(request) { mutableStateOf(request.previousDate?.takeIf { it in dates } ?: dates.firstOrNull()) }
    var month by remember(request) { mutableStateOf(YearMonth.from(date ?: now.toLocalDate())) }
    var choosingDate by remember(request) { mutableStateOf(dates.size > 1) }
    var time by remember(request) { mutableStateOf(request.previousTime) }
    LaunchedEffect(date, now) {
        if (date !in dates) {
            date = dates.firstOrNull()
            date?.let { month = YearMonth.from(it) }
        }
        time = date?.let { ReminderSelectionRules.initialTime(it, now, time) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(DavePalette.JournalPaper, RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text("通知提醒", color = DavePalette.HeaderGreenDark, fontSize = 18.sp)
            Text("高优先级系统通知，不是持续响铃闹钟", color = DavePalette.Meta, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            if (dates.isEmpty()) {
                Text("没有可用的未来提醒时间", color = DavePalette.Meta, modifier = Modifier.padding(vertical = 20.dp))
            } else if (choosingDate) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = DavePalette.Meta, modifier = Modifier.clickable(enabled = month > YearMonth.from(dates.first())) { month = month.minusMonths(1) }.padding(12.dp))
                    Text("${month.year}年${month.monthValue}月", color = DavePalette.Ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("›", color = DavePalette.Meta, modifier = Modifier.clickable(enabled = month < YearMonth.from(dates.last())) { month = month.plusMonths(1) }.padding(12.dp))
                }
                Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, color = DavePalette.Meta, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
                val first = month.atDay(1).dayOfWeek.value - 1
                repeat((first + month.lengthOfMonth() + 6) / 7) { row ->
                    Row {
                        repeat(7) { column ->
                            val day = row * 7 + column - first + 1
                            val candidate = if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
                            val enabled = candidate in dates
                            Text(candidate?.dayOfMonth?.toString().orEmpty(), textAlign = TextAlign.Center,
                                color = if (enabled) DavePalette.Ink else DavePalette.Meta.copy(alpha = .28f),
                                modifier = Modifier.weight(1f).clickable(enabled = enabled) { date = candidate; choosingDate = false }.padding(vertical = 12.dp))
                        }
                    }
                }
            } else {
                Text(date?.let(::reminderDateLabel).orEmpty(), color = DavePalette.HeaderGreenDark,
                    modifier = Modifier.clickable(enabled = dates.size > 1) { choosingDate = true }.padding(vertical = 8.dp))
                val times = date?.let { ReminderSelectionRules.validTimes(it, now, 1) }.orEmpty()
                val selectedHour = time?.hour ?: times.firstOrNull()?.hour ?: 0
                val hours = times.map { it.hour }.distinct()
                val minutes = times.filter { it.hour == selectedHour }
                val targets = reminderScrollTargets(times, time)
                // Start near the existing value once. Clicking a date, hour or
                // minute must not call scrollToItem again: doing so promoted
                // every selected row to the top of its own picker column.
                val hourState = rememberLazyListState(initialFirstVisibleItemIndex = targets.first)
                val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = targets.second)
                Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(Modifier.weight(1f), state = hourState) {
                        items(hours) { hour ->
                            Text("%02d 时".format(hour), color = DavePalette.Ink, modifier = Modifier.fillMaxWidth()
                                .background(if (hour == selectedHour) DavePalette.HeaderGreen.copy(alpha = .18f) else DavePalette.JournalPaper, RoundedCornerShape(8.dp))
                                .clickable { time = times.firstOrNull { it.hour == hour && it.minute == time?.minute } ?: times.first { it.hour == hour } }.padding(10.dp))
                        }
                    }
                    LazyColumn(Modifier.weight(1f), state = minuteState) {
                        items(minutes) { candidate ->
                            Text("%02d 分".format(candidate.minute), color = DavePalette.Ink, modifier = Modifier.fillMaxWidth()
                                .background(if (candidate == time) DavePalette.HeaderGreen.copy(alpha = .18f) else DavePalette.JournalPaper, RoundedCornerShape(8.dp))
                                .clickable { time = candidate }.padding(10.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("取消", color = DavePalette.Meta, modifier = Modifier.clickable { onDismiss() }.padding(12.dp))
                val valid = date in dates && time != null && date?.let { ReminderSelectionRules.isValidTime(it, time!!, now) } == true
                Text("确定", color = if (valid) DavePalette.HeaderGreenDark else DavePalette.Meta.copy(alpha = .35f),
                    modifier = Modifier.clickable(enabled = valid) {
                        val fresh = LocalDateTime.now()
                        if (ReminderSelectionRules.isValidTime(date!!, time!!, fresh)) {
                            request.onSelect(time!!, ChronoUnit.DAYS.between(request.todoDate, date).toInt()); onDismiss()
                        } else now = fresh
                    }.padding(12.dp))
            }
        }
    }
}

internal fun reminderDateLabel(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · 周${"一二三四五六日"[date.dayOfWeek.value - 1]}"

internal fun reminderScrollTargets(times: List<LocalTime>, selected: LocalTime?): Pair<Int, Int> {
    val value = selected?.takeIf { it in times } ?: times.firstOrNull() ?: return 0 to 0
    val hours = times.map { it.hour }.distinct()
    val minutes = times.filter { it.hour == value.hour }
    return hours.indexOf(value.hour).coerceAtLeast(0) to minutes.indexOf(value).coerceAtLeast(0)
}
