package com.fishking.core.model

import java.time.LocalDate

data class HabitDaySummary(
    val habitId: String,
    val title: String,
    val color: Long,
    val date: LocalDate,
    val count: Int,
    val targetCount: Int,
    val period: HabitPeriod,
    val isBackfilled: Boolean,
    val position: Long,
    val displayCount: Int = count,
)

data class DailyReview(
    val date: LocalDate,
    val completedTodos: List<TodoOccurrence>,
    val checkedHabits: List<HabitDaySummary>,
    val openTodos: List<TodoOccurrence> = emptyList(),
) {
    val isEmpty: Boolean get() = completedTodos.isEmpty() && checkedHabits.isEmpty()
}
