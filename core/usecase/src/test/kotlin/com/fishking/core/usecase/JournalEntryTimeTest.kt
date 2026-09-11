package com.fishking.core.usecase

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalEntryTimeTest {
    @Test fun validStoredTimesKeepTheirExactPrecision() {
        for (time in listOf(LocalTime.MIDNIGHT, LocalTime.of(9, 10), LocalTime.of(9, 10, 25, 123_000_000))) {
            assertEquals(time, parseStoredJournalTime(time.toString()))
        }
    }

    @Test fun missingOrMalformedOptionalTimesRemainUnknown() {
        for (time in listOf(null, "", " ", "not-a-time", "25:90", "09:10:00+08:00", "2026-09-11T09:10")) {
            assertNull(parseStoredJournalTime(time))
        }
    }
}
