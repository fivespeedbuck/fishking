package com.fishking.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class HabitBackfillLabelColorTest {
    private val habitColor = Color(0xFFE496AE)

    @Test
    fun enabledBackfillLabelUsesHabitColor() {
        assertEquals(habitColor, habitBackfillLabelColor(habitColor, enabled = true))
    }

    @Test
    fun disabledBackfillLabelKeepsHabitColorWithDisabledAlpha() {
        assertEquals(habitColor.copy(alpha = .4f), habitBackfillLabelColor(habitColor, enabled = false))
    }
}
