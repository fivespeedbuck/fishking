package com.fishking.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalParagraphTest {
    @Test fun listMarkersArePresentationNotText() {
        assertEquals("•", journalListMarker(JournalListStyle.BULLET, 18))
        assertEquals("18.", journalListMarker(JournalListStyle.NUMBERED, 18))
        assertEquals("a.", journalListMarker(JournalListStyle.LETTERED, 1))
        assertEquals("z.", journalListMarker(JournalListStyle.LETTERED, 26))
        assertEquals("aa.", journalListMarker(JournalListStyle.LETTERED, 27))
        assertEquals("az.", journalListMarker(JournalListStyle.LETTERED, 52))
        assertEquals("ba.", journalListMarker(JournalListStyle.LETTERED, 53))
        assertEquals("", journalListMarker(JournalListStyle.CHECKLIST, 1))
    }
}
