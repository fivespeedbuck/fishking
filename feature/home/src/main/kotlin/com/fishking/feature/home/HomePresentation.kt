package com.fishking.feature.home

import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitPeriod
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

/** Whether the next habit tap changes its single-day completion section. */
internal fun HomeDisplayItem.Habit.willCrossCompletionOnTap(): Boolean {
    val nextComplete = when (value.period) {
        HabitPeriod.DAILY -> if (count >= value.targetCount) false else count + 1 >= value.targetCount
        else -> !checkedOnDate
    }
    return nextComplete != isComplete
}

/** The source card is gone from the list while its independent flight runs. */
internal fun HomeDisplaySections.removeOutgoingItem(
    transition: HomeCompletionTransition,
): HomeDisplaySections = copy(
    openUrgent = openUrgent.filterNot { it.value.id == transition.before.id },
    openNormal = openNormal.filterNot { it.value.id == transition.before.id },
    completedUrgent = completedUrgent.filterNot { it.value.id == transition.before.id },
    completedNormal = completedNormal.filterNot { it.value.id == transition.before.id },
)

/** Reveal the same card at the head of its destination section. */
internal fun HomeDisplaySections.revealCompletionTarget(
    transition: HomeCompletionTransition,
): HomeDisplaySections {
    val id = transition.before.id
    val target = sequenceOf(completedUrgent, completedNormal, openUrgent, openNormal)
        .flatten()
        .firstOrNull { it.value.id == id }
        ?: return this
    val without = removeOutgoingItem(transition)
    val targetTodo = HomeDisplayItem.Todo(target.value)
    return if (transition.targetCompleted) {
        if (target.value.priority == TodoPriority.URGENT) without.copy(completedUrgent = listOf(targetTodo) + without.completedUrgent)
        else without.copy(completedNormal = listOf(targetTodo) + without.completedNormal)
    } else {
        if (target.value.priority == TodoPriority.URGENT) without.copy(openUrgent = listOf(targetTodo) + without.openUrgent)
        else without.copy(openNormal = listOf(targetTodo) + without.openNormal)
    }
}

internal data class HomeCompletionTransition(
    val before: TodoOccurrence,
    val targetCompleted: Boolean,
)
