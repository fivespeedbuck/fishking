package com.fishking.feature.home

import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import java.time.LocalDate

private const val WEEK_HABIT_DRAG_PREFIX = "week-habit@"

internal fun weekHabitDragId(id: String, date: LocalDate): String =
    "$WEEK_HABIT_DRAG_PREFIX$id@${date.toEpochDay()}"

internal fun parseWeekHabitDragId(value: String): Pair<String, LocalDate>? {
    if (!value.startsWith(WEEK_HABIT_DRAG_PREFIX)) return null
    val splitAt = value.lastIndexOf('@')
    if (splitAt <= WEEK_HABIT_DRAG_PREFIX.lastIndex) return null
    val id = value.substring(WEEK_HABIT_DRAG_PREFIX.length, splitAt)
    val epochDay = value.substring(splitAt + 1).toLongOrNull() ?: return null
    return id to LocalDate.ofEpochDay(epochDay)
}

/** The six fixed visual groups used by both single-day and week home views. */
internal data class HomeDisplaySections(
    val openUrgent: List<HomeDisplayItem.Todo>,
    val openNormal: List<HomeDisplayItem.Todo>,
    val openHabits: List<HomeDisplayItem.Habit>,
    val completedUrgent: List<HomeDisplayItem.Todo>,
    val completedNormal: List<HomeDisplayItem.Todo>,
    val completedHabits: List<HomeDisplayItem.Habit>,
) {
    val open: List<HomeDisplayItem>
        get() = (openUrgent + openNormal + openHabits).sortedWith(compareBy(HomeDisplayItem::position, HomeDisplayItem::stableKey))

    val completed: List<HomeDisplayItem>
        get() = (completedUrgent + completedNormal + completedHabits).sortedWith(compareBy(HomeDisplayItem::position, HomeDisplayItem::stableKey))

    val isEmpty: Boolean
        get() = open.isEmpty() && completed.isEmpty()
}

internal sealed interface HomeDisplayItem {
    val stableKey: String
    val position: Long

    data class Todo(val value: TodoOccurrence) : HomeDisplayItem {
        override val stableKey: String = "todo-${value.id}"
        override val position: Long = value.position
    }

    data class Habit(
        val value: HabitWeekItem,
        val count: Int,
        val checkedOnDate: Boolean,
        val isBackfilled: Boolean,
        val isComplete: Boolean,
    ) : HomeDisplayItem {
        override val stableKey: String = "habit-${value.id}"
        override val position: Long = value.position
    }
}

internal fun buildHomeDisplaySections(
    date: LocalDate,
    todos: List<TodoOccurrence>,
    habits: List<HabitWeekItem>,
): HomeDisplaySections {
    val todoItems = todos
        .sortedWith(compareBy<TodoOccurrence> { it.position }.thenBy { it.createdAt }.thenBy { it.id })
        .map(HomeDisplayItem::Todo)
    val habitItems = habits
        .asSequence()
        .filter { !date.isBefore(it.startDate) }
        .sortedWith(compareBy<HabitWeekItem> { it.position }.thenBy { it.id })
        .mapNotNull { habit ->
            val state = habit.dayState(date)
            if (!state.shouldAppearOnHome) return@mapNotNull null
            val dayRecord = habit.records.firstOrNull { it.date == date }
            val count = state.periodCount
            val checkedOnDate = state.actualCount > 0
            HomeDisplayItem.Habit(
                value = habit.copy(
                    title = state.rule.title,
                    color = state.rule.color,
                    versionId = state.rule.id,
                    period = state.rule.period,
                    targetCount = state.rule.targetCount,
                    scheduleDays = state.rule.scheduleDays,
                    intervalDays = state.rule.intervalDays,
                    scheduleStartDate = state.rule.scheduleStartDate,
                ),
                count = count,
                checkedOnDate = checkedOnDate,
                isBackfilled = dayRecord?.isBackfilled == true,
                isComplete = state.isCompleteOnDate,
            )
        }
        .toList()

    return HomeDisplaySections(
        openUrgent = todoItems.filter { !it.value.isCompleted && it.value.priority == TodoPriority.URGENT },
        openNormal = todoItems.filter { !it.value.isCompleted && it.value.priority == TodoPriority.NORMAL },
        openHabits = habitItems.filterNot(HomeDisplayItem.Habit::isComplete),
        completedUrgent = todoItems.filter { it.value.isCompleted && it.value.priority == TodoPriority.URGENT },
        completedNormal = todoItems.filter { it.value.isCompleted && it.value.priority == TodoPriority.NORMAL },
        completedHabits = habitItems.filter(HomeDisplayItem.Habit::isComplete),
    )
}
