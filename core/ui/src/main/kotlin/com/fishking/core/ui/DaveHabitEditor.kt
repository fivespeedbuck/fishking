package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishking.core.model.HabitPeriod
import java.time.LocalDate

/** The one habit editor used by both the habit timeline and single-day home. */
@Composable
fun DaveHabitEditor(
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
    autoFocus: Boolean = false,
) {
    Column(modifier = modifier) {
        DaveInlineDraftCard(
            value = title,
            autoFocus = autoFocus,
            accentColor = Color(color),
            onValueChange = onTitleChange,
            onConfirm = onConfirm,
            onCancelEmpty = onCancel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        // Appearance is the first options row, immediately after the title.
        DaveMacaronPalette(
            selected = color,
            onSelected = onColorChange,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        DaveHabitQuickOptions(
            period = period,
            targetCount = target,
            intervalDays = intervalDays,
            onPeriodSelected = onPeriodChange,
            onTargetCountChanged = onTargetChange,
            onIntervalDaysChanged = onIntervalDaysChange,
        )
        HabitTagAndCadenceOptions(title, onTitleChange, period, onPeriodChange)
        HabitSchedulePicker(period, scheduleDays, onScheduleDayToggle)
        HabitScheduleStartOption(period, scheduleStartDate, onScheduleStartDateChange)
        HabitTargetSummary(period, target, intervalDays, scheduleDays)
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickIconOption(
                icon = Icons.Outlined.EventRepeat,
                description = "固定节奏：每N天一次",
                modifier = Modifier.size(44.dp),
                selected = period == HabitPeriod.EVERY_N_DAYS,
                onClick = { onPeriodChange(HabitPeriod.EVERY_N_DAYS) },
            )
            QuickIconOption(
                icon = Icons.Outlined.Timer,
                description = "间隔节奏：完成后隔N天",
                modifier = Modifier.size(44.dp),
                selected = period == HabitPeriod.AFTER_COMPLETION_N_DAYS,
                onClick = { onPeriodChange(HabitPeriod.AFTER_COMPLETION_N_DAYS) },
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (expanded) DavePalette.HeaderGreen else Color.White.copy(alpha = .55f), CircleShape)
                    .border(1.dp, if (expanded) DavePalette.HeaderGreenDark else DavePalette.Divider, CircleShape)
                    .clickable { expanded = !expanded }
                    .semantics { contentDescription = "编辑习惯TAG" },
                contentAlignment = Alignment.Center,
            ) { Text("#", color = if (expanded) Color.White else DavePalette.Ink, fontSize = 21.sp) }
            // Match the six columns above: cadence/cadence/TAG occupy the
            // first three columns and the target stepper owns the right half.
            repeat(3) { Box(Modifier.size(44.dp)) }
        }
        if (expanded) {
            Spacer(Modifier.size(10.dp))
            DaveTagEditorPanel(
                title = title,
                onTitleChange = onTitleChange,
                onDone = { expanded = false },
            )
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
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = .72f), RoundedCornerShape(50))
                .border(1.dp, DavePalette.Divider, RoundedCornerShape(50))
                .clickable { expanded = !expanded }
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
        if (selected.isNotEmpty()) Text(
            text = "清空",
            color = DavePalette.Meta,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp).clickable { selected.toList().forEach(onToggle) },
        )
    }
    if (!expanded) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(values.indices.toList()) { index ->
            val value = values[index]
            val active = value in selected
            Box(
                Modifier.size(if (period == HabitPeriod.WEEKLY) 38.dp else 34.dp)
                    .clip(CircleShape)
                    .background(if (active) DavePalette.HeaderGreen else Color.White.copy(alpha = .55f), CircleShape)
                    .border(1.dp, if (active) DavePalette.HeaderGreen.copy(alpha = .65f) else DavePalette.Divider, CircleShape)
                    .clickable { onToggle(value) },
                contentAlignment = Alignment.Center,
            ) { Text(labels[index], color = if (active) Color.White else DavePalette.Ink, fontSize = 12.sp) }
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
    var expanded by remember(startDate) { mutableStateOf(false) }
    if (expanded) {
        DaveCalendar(
            selected = startDate,
            onSelect = { onDateChange(it); expanded = false },
            onDismiss = { expanded = false },
            markDescription = "开始日期",
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "开始 ${startDate.year}/${startDate.monthValue}/${startDate.dayOfMonth}",
            color = DavePalette.HeaderGreenDark,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = .72f), RoundedCornerShape(50))
                .border(1.dp, DavePalette.Divider, RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HabitTargetSummary(period: HabitPeriod, target: Int, intervalDays: Int, scheduleDays: Set<Int>) {
    val error = habitScheduleError(period, target, scheduleDays)
    Column(Modifier.padding(horizontal = 22.dp, vertical = 2.dp)) {
        Text(
            text = when (period) {
                HabitPeriod.DAILY -> "每天 × $target 次"
                HabitPeriod.WEEKLY -> if (scheduleDays.isEmpty()) "每周 × $target 次 · 不限哪天"
                    else "每周 × $target 次 · ${scheduleDays.sorted().joinToString("、") { listOf("一", "二", "三", "四", "五", "六", "日")[it - 1] }}"
                HabitPeriod.MONTHLY -> if (scheduleDays.isEmpty()) "每月 × $target 次 · 不限哪天"
                    else "每月 × $target 次 · ${scheduleDays.sorted().joinToString("、") { "${it}日" }}"
                HabitPeriod.EVERY_N_DAYS -> "每$intervalDays 天一次 · 固定节奏"
                HabitPeriod.AFTER_COMPLETION_N_DAYS -> "完成后隔 $intervalDays 天 · 随实际完成重算"
            },
            color = DavePalette.Ink.copy(alpha = .68f),
            fontSize = 13.sp,
        )
        error?.let { Text(it, color = DavePalette.Urgent, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
        if (period == HabitPeriod.MONTHLY && scheduleDays.any { it >= 29 }) Text(
            "29–31 日在部分月份不存在；这些月份只会在实际存在的日期显示",
            color = DavePalette.Meta,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun HabitPeriod.isIntervalMode(): Boolean =
    this == HabitPeriod.EVERY_N_DAYS || this == HabitPeriod.AFTER_COMPLETION_N_DAYS

private fun habitScheduleError(period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>): String? {
    if (scheduleDays.isEmpty() || period == HabitPeriod.DAILY || period.isIntervalMode()) return null
    return when {
        period == HabitPeriod.WEEKLY && scheduleDays.size < targetCount ->
            "每周目标 $targetCount 次，但只指定了 ${scheduleDays.size} 天；请增加星期或降低次数"
        period == HabitPeriod.MONTHLY && scheduleDays.size < targetCount ->
            "每月目标 $targetCount 次，但只指定了 ${scheduleDays.size} 天；请增加日期或降低次数"
        else -> null
    }
}
