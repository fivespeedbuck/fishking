package com.fishking.feature.journal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class JournalTimelineTest {
    @Test fun timePeriodsHaveExplicitClockTimesWithoutGuessingLegacyTime() {
        assertEquals("上午 09:10", journalTimeLabel(LocalTime.of(9, 10)))
        assertEquals("中午 12:00", journalTimeLabel(LocalTime.NOON))
        assertEquals("下午 15:20", journalTimeLabel(LocalTime.of(15, 20)))
        assertEquals("晚上 21:00", journalTimeLabel(LocalTime.of(21, 0)))
        assertEquals("时间未记录", journalTimeLabel(null))
    }


    @Test fun journalTagsAcceptHashSpacesAndPresetStyleSeparatorsWithoutDuplicates() {
        assertEquals(
            listOf("生活", "旅行", "宁波"),
            parseJournalTags("#生活  旅行，#宁波  #生活"),
        )
    }
}
