package com.fishking.core.reminder

import com.fishking.core.database.ActiveTodoReminder
import java.time.Instant
import java.time.ZoneId

data class ScheduledTodoReminder(
    val reminderId: String,
    val occurrenceId: String,
    val title: String,
    val triggerAtMillis: Long,
)

data class ReminderPlan(
    val toSchedule: List<ScheduledTodoReminder>,
    val toCancel: Set<String>,
)

fun planReminders(
    reminders: List<ActiveTodoReminder>,
    previouslyScheduledIds: Set<String>,
    now: Instant,
    zoneId: ZoneId,
): ReminderPlan {
    val future = reminders.mapNotNull { reminder ->
        val triggerAt = reminder.displayDate
            .plusDays(reminder.dayOffset.toLong())
            .atTime(reminder.localTime)
            .atZone(zoneId)
            .toInstant()
        triggerAt.takeIf { it.isAfter(now) }?.let {
            ScheduledTodoReminder(
                reminderId = reminder.reminderId,
                occurrenceId = reminder.occurrenceId,
                title = reminder.title,
                triggerAtMillis = it.toEpochMilli(),
            )
        }
    }
    val futureIds = future.mapTo(mutableSetOf(), ScheduledTodoReminder::reminderId)
    return ReminderPlan(
        toSchedule = future,
        toCancel = previouslyScheduledIds - futureIds,
    )
}
