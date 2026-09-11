package com.fishking.feature.home

import com.fishking.core.model.ReminderSelectionRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderPickerTest {
    @Test fun dateUsesReadableChineseAndWeekday() {
        assertEquals("2026年9月11日 · 周五", reminderDateLabel(LocalDate.of(2026, 9, 11)))
    }
    @Test fun autoScrollTargetsUseFilteredRowsNotRawHourAndMinute() {
        val now = LocalDateTime.of(2026, 9, 11, 15, 11)
        val times = ReminderSelectionRules.validTimes(now.toLocalDate(), now, 1)
        assertEquals(0 to 3, reminderScrollTargets(times, LocalTime.of(15, 15)))
        assertEquals(6 to 43, reminderScrollTargets(times, LocalTime.of(21, 43)))
        assertEquals(0 to 0, reminderScrollTargets(emptyList(), null))
    }
}
