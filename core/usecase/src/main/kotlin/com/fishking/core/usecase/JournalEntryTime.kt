package com.fishking.core.usecase

import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Optional legacy metadata must never make otherwise intact journal content unreadable. */
internal fun parseStoredJournalTime(value: String?): LocalTime? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalTime.parse(value)
    } catch (_: DateTimeParseException) {
        // Unknown is not midnight or the current time. Keep the stored value
        // untouched and expose the existing "time not recorded" presentation.
        null
    }
}
