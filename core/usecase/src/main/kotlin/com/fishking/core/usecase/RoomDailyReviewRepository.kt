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
                    HabitDaySummary(
                        habitId = habit.id,
                        title = habit.title,
                        color = habit.color,
                        date = date,
                        count = record.count,
                        targetCount = habit.targetCount,
                        period = habit.period,
                        isBackfilled = record.isBackfilled,
                        position = habit.position,
                        displayCount = if (habit.period == com.fishking.core.model.HabitPeriod.WEEKLY) habit.weeklyEffectiveDayCount else record.count,
                    )
                }
                .sortedBy(HabitDaySummary::position)
                .toList(),
        )
    }
}
