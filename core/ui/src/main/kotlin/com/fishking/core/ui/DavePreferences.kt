package com.fishking.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class HabitWeekSkin(val preferenceValue: String) {
    UNIFIED_CARD("habit_unified"),
    SPACED_CARDS("habit_spaced");

    companion object {
        fun fromPreference(value: String?): HabitWeekSkin = when (value) {
            SPACED_CARDS.preferenceValue -> SPACED_CARDS
            // The removed paper/Dave choices were global backgrounds. Both safely
            // migrate to the new default habit-only presentation.
            else -> UNIFIED_CARD
        }
    }
}

val LocalHabitWeekSkin = staticCompositionLocalOf { HabitWeekSkin.UNIFIED_CARD }
val LocalPresetTags = staticCompositionLocalOf<List<String>> { emptyList() }
