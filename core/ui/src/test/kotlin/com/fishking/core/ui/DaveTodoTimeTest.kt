package com.fishking.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoReminder
import java.time.*

class DaveTodoTimeTest {
    @Test fun showsReminderTimeAndCountWithoutLosingDayOffset() {
        val todo = TodoOccurrence(id = "t", nominalDate = LocalDate.of(2026, 9, 6), displayDate = LocalDate.of(2026, 9, 6),
            title = "任务", position = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        assertEquals("", todoTimeLabel(todo))
        val one = TodoReminder("r", "t", -1, LocalTime.of(21, 0), 0, true, Instant.EPOCH)
        assertEquals("前1天 21:00", todoTimeLabel(todo.copy(displayReminders = listOf(one))))
        assertEquals("前1天 21:00 +1", todoTimeLabel(todo.copy(displayReminders = listOf(one, one.copy(id = "r2", dayOffset = 0)))))
        assertEquals("后3天 08:15", todoTimeLabel(todo.copy(displayReminders = listOf(one.copy(dayOffset = 3, localTime = LocalTime.of(8, 15))))))
    }

    @Test fun completedCardShowsActualCompletionTimeInsteadOfReminder() {
        val todo = TodoOccurrence(id = "done", nominalDate = LocalDate.of(2026, 9, 6), displayDate = LocalDate.of(2026, 9, 6),
            title = "任务", status = com.fishking.core.model.TodoStatus.COMPLETED,
            completedAt = Instant.parse("2026-09-06T09:30:00Z"), position = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val expected = todo.completedAt!!.atZone(ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        assertEquals(expected, todoTimeLabel(todo))
    }
}
