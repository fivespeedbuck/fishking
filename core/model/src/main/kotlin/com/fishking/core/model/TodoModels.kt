package com.fishking.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TodoPriority {
    NORMAL,
    URGENT,
}

enum class TodoPlanScope(val tag: String) { DATE("待办"), WEEK("周待办"), MONTH("月待办"), YEAR("年待办"), HALF_YEAR("半年待办"), THREE_MONTHS("三个月待办"), DEADLINE("指定日期前完成") }

enum class TodoStatus {
    OPEN,
    COMPLETED,
}

enum class TodoChangeScope {
    ONLY_THIS,
    THIS_AND_FUTURE,
}

enum class RecurrenceFrequency {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

enum class MonthlyOverflowPolicy {
    CLAMP_TO_LAST_DAY,
    SKIP_MONTH,
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.ONCE,
    val monthlyOverflowPolicy: MonthlyOverflowPolicy = MonthlyOverflowPolicy.CLAMP_TO_LAST_DAY,
)

data class TodoSeries(
    val id: String,
    val activeUntilExclusive: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TodoSeriesVersion(
    val id: String,
    val seriesId: String,
    val effectiveFromNominalDate: LocalDate,
    val effectiveUntilExclusive: LocalDate? = null,
    val title: String,
    val recurrence: RecurrenceRule,
    val priority: TodoPriority = TodoPriority.NORMAL,
    val accentColor: Long? = null,
    val createdAt: Instant,
)

data class TodoOccurrence(
    val id: String,
    val seriesId: String? = null,
    val seriesVersionId: String? = null,
    val nominalDate: LocalDate,
    val displayDate: LocalDate,
    val title: String,
    val priority: TodoPriority = TodoPriority.NORMAL,
    val accentColor: Long? = null,
    val status: TodoStatus = TodoStatus.OPEN,
    val completedAt: Instant? = null,
    val position: Long,
    val isSeriesException: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val displayReminders: List<TodoReminder> = emptyList(),
    val planScope: TodoPlanScope = TodoPlanScope.DATE,
    val fromLifeGoal: Boolean = false,
    val planDeadline: LocalDate? = null,
) {
    val isCompleted: Boolean get() = status == TodoStatus.COMPLETED
}

typealias TodoItem = TodoOccurrence

data class TodoReminder(
    val id: String,
    val occurrenceId: String,
    val dayOffset: Int = 0,
    val localTime: LocalTime,
    val position: Long,
    val isEnabled: Boolean = true,
    val createdAt: Instant,
)

/** A reminder entered in the quick-edit toolbar before it is attached to an occurrence. */
data class TodoReminderSpec(
    val dayOffset: Int = 0,
    val localTime: LocalTime,
    val position: Long = 0L,
    val isEnabled: Boolean = true,
)

data class Tag(
    val id: String,
    val name: String,
    val createdAt: Instant,
)

data class TodoLifeGoalLink(
    val occurrenceId: String,
    val lifeGoalId: String,
)
