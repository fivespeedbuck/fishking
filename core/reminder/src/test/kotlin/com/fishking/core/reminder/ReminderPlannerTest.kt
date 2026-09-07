package com.fishking.core.reminder

import com.fishking.core.database.ActiveTodoReminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Hong_Kong")

    @Test
    fun futureReminderIsScheduledUsingOccurrenceDateOffsetAndDeviceZone() {
        val reminder = reminder(date = LocalDate.of(2026, 9, 5), dayOffset = -1, time = LocalTime.of(15, 0))

        val plan = planReminders(
            reminders = listOf(reminder),
            previouslyScheduledIds = emptySet(),
            now = Instant.parse("2026-09-04T06:00:00Z"),
            zoneId = zone,
        )

        assertEquals(Instant.parse("2026-09-04T07:00:00Z").toEpochMilli(), plan.toSchedule.single().triggerAtMillis)
        assertEquals(emptySet(), plan.toCancel)
    }

    @Test
    fun removedAndExpiredRemindersAreCancelled() {
        val expired = reminder(date = LocalDate.of(2026, 9, 4), time = LocalTime.of(9, 0))

        val plan = planReminders(
            reminders = listOf(expired),
            previouslyScheduledIds = setOf("reminder-1", "removed"),
            now = Instant.parse("2026-09-04T02:00:00Z"),
            zoneId = zone,
        )

        assertEquals(emptyList(), plan.toSchedule)
        assertEquals(setOf("reminder-1", "removed"), plan.toCancel)
    }

    private fun reminder(
        date: LocalDate,
        dayOffset: Int = 0,
        time: LocalTime,
    ) = ActiveTodoReminder(
        reminderId = "reminder-1",
        occurrenceId = "todo-1",
        title = "买牙膏",
        displayDate = date,
        dayOffset = dayOffset,
        localTime = time,
    )
}
