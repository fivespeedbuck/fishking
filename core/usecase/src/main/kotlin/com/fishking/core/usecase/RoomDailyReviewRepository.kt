package com.fishking.core.usecase

import com.fishking.core.model.DailyReview
import com.fishking.core.model.HabitDaySummary
import com.fishking.core.model.HabitRules
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

class RoomDailyReviewRepository(
    private val homeRepository: HomeRepository,
    private val habitRepository: HabitRepository,
) : DailyReviewRepository {
    override fun observe(date: LocalDate) = combine(
        homeRepository.observeTodos(date),
        habitRepository.observeWeek(HabitRules.weekStart(date)),
    ) { todos, habits ->
        DailyReview(
            date = date,
            completedTodos = todos.filter { it.isCompleted },
            openTodos = todos.filterNot { it.isCompleted },
            checkedHabits = habits.asSequence()
                .filter { !date.isBefore(it.startDate) }
                .mapNotNull { habit ->
                    val record = habit.records.firstOrNull { it.date == date } ?: return@mapNotNull null
                    if (record.count <= 0) return@mapNotNull null
                    val state = habit.dayState(date)
                    HabitDaySummary(
                        habitId = habit.id,
                        title = state.rule.title,
                        color = state.rule.color,
                        date = date,
                        count = record.count,
                        targetCount = state.rule.targetCount,
                        period = state.rule.period,
                        isBackfilled = record.isBackfilled,
                        position = habit.position,
                        displayCount = when (state.rule.period) {
                            com.fishking.core.model.HabitPeriod.DAILY -> record.count
                            else -> state.periodCount
                        },
                        intervalDays = state.rule.intervalDays,
                    )
                }
                .sortedBy(HabitDaySummary::position)
                .toList(),
        )
    }
}
