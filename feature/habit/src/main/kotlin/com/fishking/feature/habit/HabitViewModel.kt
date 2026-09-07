package com.fishking.feature.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import com.fishking.core.model.HabitWeekSnapshot
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.usecase.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModel(private val repository: HabitRepository) : ViewModel() {
    private val currentDate = MutableStateFlow(LocalDate.now())
    private val visibleMonths = MutableStateFlow(java.time.YearMonth.now() to java.time.YearMonth.now())
    val timeline: StateFlow<List<HabitWeekSnapshot>> = combine(currentDate, visibleMonths) { date, months -> date to months }
        .flatMapLatest { (date, months) -> repository.observeTimelineRange(date,
            HabitRules.weekStart(months.first.atDay(1)), HabitRules.weekStart(months.second.atEndOfMonth())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draftVisible = MutableStateFlow(false)
    val draftTitle = MutableStateFlow("")
    val draftPeriod = MutableStateFlow(HabitPeriod.DAILY)
    val draftTarget = MutableStateFlow(1)
    val draftScheduleDays = MutableStateFlow<Set<Int>>(emptySet())
    val draftColor = MutableStateFlow(DEFAULT_COLOR)
    val editingId = MutableStateFlow<String?>(null)
    val editingTitle = MutableStateFlow("")
    val editingPeriod = MutableStateFlow(HabitPeriod.DAILY)
    val editingTarget = MutableStateFlow(1)
    val editingScheduleDays = MutableStateFlow<Set<Int>>(emptySet())
    val editingColor = MutableStateFlow(DEFAULT_COLOR)

    fun setCurrentDate(date: LocalDate) { currentDate.value = date }

    fun loadPreviousMonth() { visibleMonths.value = visibleMonths.value.let { it.first.minusMonths(1) to it.second } }
    fun loadNextMonth() { visibleMonths.value = visibleMonths.value.let { it.first to it.second.plusMonths(1) } }

    fun startDraft() {
        draftVisible.value = true
        draftTitle.value = ""
        draftPeriod.value = HabitPeriod.DAILY
        draftTarget.value = 1
        draftScheduleDays.value = emptySet()
        draftColor.value = DEFAULT_COLOR
    }

    fun updateDraft(value: String) { draftTitle.value = value.replace('\n', ' ') }
    fun setPeriod(value: HabitPeriod) {
        if (draftPeriod.value == value) return
        draftPeriod.value = value
        draftTarget.value = draftTarget.value.coerceAtMost(value.maximumTargetCount())
        draftScheduleDays.value = emptySet()
    }
    fun toggleDraftScheduleDay(value: Int) {
        draftScheduleDays.value = draftScheduleDays.value.toMutableSet().apply { if (!add(value)) remove(value) }
    }
    fun setTarget(value: Int) { draftTarget.value = value.coerceIn(1, draftPeriod.value.maximumTargetCount()) }
    fun setDraftColor(value: Long?) { draftColor.value = value ?: DEFAULT_COLOR }

    fun cancelDraft() {
        draftVisible.value = false
        draftTitle.value = ""
    }

    fun cancelDraftIfBlank() { if (draftTitle.value.isBlank()) cancelDraft() }

    fun confirmDraft(date: LocalDate) {
        val title = draftTitle.value.trim()
        if (title.isEmpty()) return
        if (habitScheduleError(draftPeriod.value, draftTarget.value, draftScheduleDays.value) != null) return
        viewModelScope.launch {
            repository.createHabit(title, draftColor.value, date, draftPeriod.value, draftTarget.value, draftScheduleDays.value)
            cancelDraft()
        }
    }

    fun startEditing(habit: HabitWeekItem) {
        cancelDraft()
        editingId.value = habit.id
        editingTitle.value = habit.title
        editingPeriod.value = habit.period
        editingTarget.value = habit.targetCount
        editingScheduleDays.value = habit.scheduleDays
        editingColor.value = habit.color
    }

    fun updateEditingTitle(value: String) { editingTitle.value = value.replace('\n', ' ') }
    fun setEditingPeriod(value: HabitPeriod) {
        if (editingPeriod.value == value) return
        editingPeriod.value = value
        editingTarget.value = editingTarget.value.coerceAtMost(value.maximumTargetCount())
        editingScheduleDays.value = emptySet()
    }
    fun toggleEditingScheduleDay(value: Int) {
        editingScheduleDays.value = editingScheduleDays.value.toMutableSet().apply { if (!add(value)) remove(value) }
    }
    fun setEditingTarget(value: Int) { editingTarget.value = value.coerceIn(1, editingPeriod.value.maximumTargetCount()) }
    fun setEditingColor(value: Long?) { editingColor.value = value ?: DEFAULT_COLOR }

    fun cancelEditing() {
        editingId.value = null
        editingTitle.value = ""
    }

    fun confirmEditing() {
        val habitId = editingId.value ?: return
        val title = editingTitle.value.trim()
        if (title.isEmpty()) return
        if (habitScheduleError(editingPeriod.value, editingTarget.value, editingScheduleDays.value) != null) return
        viewModelScope.launch {
            repository.updateHabit(
                habitId = habitId,
                effectiveFromWeek = HabitRules.weekStart(currentDate.value),
                title = title,
                color = editingColor.value,
                period = editingPeriod.value,
                targetCount = editingTarget.value,
                scheduleDays = editingScheduleDays.value,
            )
            cancelEditing()
        }
    }

    fun toggle(habitId: String, date: LocalDate) {
        viewModelScope.launch { repository.toggleCheckIn(habitId, date) }
    }

    fun toggleSkip(habitId: String, weekStart: LocalDate) {
        viewModelScope.launch { repository.toggleWeekSkip(habitId, weekStart) }
    }

    fun endFromWeek(habitId: String, weekStart: LocalDate) {
        viewModelScope.launch { repository.endHabitFromWeek(habitId, weekStart) }
    }

    fun reorderHabitRelative(visibleIds: List<String>, sourceId: String, targetId: String, after: Boolean) {
        if (sourceId == targetId) return
        val reordered = visibleIds.toMutableList()
        if (!reordered.remove(sourceId)) return
        val targetIndex = reordered.indexOf(targetId)
        if (targetIndex < 0) return
        reordered.add((targetIndex + if (after) 1 else 0).coerceIn(0, reordered.size), sourceId)
        viewModelScope.launch { repository.reorderHabits(reordered) }
    }

    companion object {
        private const val DEFAULT_COLOR = 0xFF8FA7E4L
        fun factory(repository: HabitRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HabitViewModel(repository) }
        }
    }
}

internal fun habitScheduleError(
    period: HabitPeriod,
    targetCount: Int,
    scheduleDays: Set<Int>,
): String? {
    if (scheduleDays.isEmpty() || period == HabitPeriod.DAILY) return null
    return when {
        period == HabitPeriod.WEEKLY && scheduleDays.size < targetCount ->
            "每周目标 $targetCount 次，但只指定了 ${scheduleDays.size} 天；请增加星期或降低次数"
        period == HabitPeriod.MONTHLY && scheduleDays.size < targetCount ->
            "每月目标 $targetCount 次，但只指定了 ${scheduleDays.size} 天；请增加日期或降低次数"
        else -> null
    }
}

private fun HabitPeriod.maximumTargetCount(): Int = when (this) {
    HabitPeriod.DAILY -> 99
    HabitPeriod.WEEKLY -> 7
    HabitPeriod.MONTHLY -> 31
}
