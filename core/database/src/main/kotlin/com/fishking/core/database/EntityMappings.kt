package com.fishking.core.database

import com.fishking.core.model.Habit
import com.fishking.core.model.HabitDayRecord
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitVersion
import com.fishking.core.model.LifeGoal
import com.fishking.core.model.LifeGoalEvent
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.MonthlyOverflowPolicy
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.RecurrenceRule
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoReminder
import com.fishking.core.model.TodoSeriesVersion
import com.fishking.core.model.TodoStatus
import com.fishking.core.model.Tag

fun TodoOccurrenceEntity.toModel() = TodoOccurrence(
    planScope = com.fishking.core.model.TodoPlanScope.valueOf(planScope),
    planDeadline = planDeadline,
    id = id,
    seriesId = seriesId,
    seriesVersionId = seriesVersionId,
    nominalDate = nominalDate,
    displayDate = displayDate,
    title = title,
    priority = TodoPriority.valueOf(priority),
    accentColor = accentColor,
    status = TodoStatus.valueOf(status),
    completedAt = completedAt,
    position = position,
    isSeriesException = isSeriesException,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TodoOccurrence.toEntity(deletedAt: java.time.Instant? = null) = TodoOccurrenceEntity(
    planScope = planScope.name,
    planDeadline = planDeadline,
    id = id,
    seriesId = seriesId,
    seriesVersionId = seriesVersionId,
    nominalDate = nominalDate,
    displayDate = displayDate,
    title = title,
    priority = priority.name,
    accentColor = accentColor,
    status = status.name,
    completedAt = completedAt,
    position = position,
    isSeriesException = isSeriesException,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TodoSeriesVersionEntity.toModel() = TodoSeriesVersion(
    id = id,
    seriesId = seriesId,
    effectiveFromNominalDate = effectiveFromNominalDate,
    effectiveUntilExclusive = effectiveUntilExclusive,
    title = title,
    recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.valueOf(recurrenceFrequency),
        monthlyOverflowPolicy = MonthlyOverflowPolicy.valueOf(monthlyOverflowPolicy),
    ),
    priority = TodoPriority.valueOf(priority),
    accentColor = accentColor,
    createdAt = createdAt,
)

fun TodoReminderEntity.toModel() = TodoReminder(
    id = id,
    occurrenceId = occurrenceId,
    dayOffset = dayOffset,
    localTime = localTime,
    position = position,
    isEnabled = isEnabled,
    createdAt = createdAt,
)

fun HabitEntity.toModel() = Habit(
    id = id,
    title = title,
    color = color,
    startDate = startDate,
    endedFromWeek = endedFromWeek,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HabitVersionEntity.toModel() = HabitVersion(
    id = id,
    habitId = habitId,
    effectiveFromDate = effectiveFromDate,
    effectiveUntilExclusive = effectiveUntilExclusive,
    title = title,
    color = color,
    period = HabitPeriod.valueOf(period),
    targetCount = targetCount,
    scheduleDays = scheduleDays.split(',').mapNotNull(String::toIntOrNull).toSet(),
    intervalDays = intervalDays,
    scheduleStartDate = scheduleStartDate,
    createdAt = createdAt,
)

fun HabitDayRecordEntity.toModel() = HabitDayRecord(
    habitId = habitId,
    date = date,
    count = count,
    isBackfilled = isBackfilled,
    affectsScheduleAnchor = affectsScheduleAnchor,
    updatedAt = updatedAt,
)

fun LifeGoalEntity.toModel() = LifeGoal(
    id = id,
    title = title,
    note = note,
    type = LifeGoalType.valueOf(type),
    position = position,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    accentColor = accentColor,
)

fun LifeGoalEventEntity.toModel() = LifeGoalEvent(
    id = id,
    goalId = goalId,
    occurredOn = occurredOn,
    result = LifeGoalResult.valueOf(result),
    source = LifeGoalEventSource.valueOf(source),
    sourceTodoOccurrenceId = sourceTodoOccurrenceId,
    position = position,
    createdAt = createdAt,
)

fun TagEntity.toModel() = Tag(
    id = id,
    name = name,
    createdAt = createdAt,
)
