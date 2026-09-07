package com.fishking.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DavePalette {
    val MintTop = Color(0xFFD1F2DC)
    val AquaBottom = Color(0xFF45B7D3)
    val HeaderGreen = Color(0xFF3FA36A)
    val HeaderGreenDark = Color(0xFF277A4B)
    val Card = Color(0xFFF2FAF8)
    val CardMuted = Color(0xFFE2EFEC)
    val JournalPaper = Color(0xFFFFFBF2)
    val Ink = Color(0xFF17272B)
    val Meta = Color(0xFF168B91)
    val Divider = Color(0x3D246C73)
    val Normal = Color(0xFFFFCA18)
    val Urgent = Color(0xFFE84B40)
    val Habit = Color(0xFF36A2CF)
    val Completed = Color(0xFF45B867)
    val Life = Color(0xFF9471C2)
    val Plan = Color(0xFFED9B3D)
    val CurrentWeek = Color(0xFFE4F4E6)
    val OtherWeek = Color.White.copy(alpha = .24f)
    val WeekBorder = Color.White.copy(alpha = .75f)
    val NavHome = Color(0xFFE9CE69)
    val NavJournal = Color(0xFFA4D5B4)
    val NavHabit = Color(0xFF62C5BE)
    val NavLife = Life
}

private val FishKingColors = lightColorScheme(
    primary = DavePalette.HeaderGreen,
    onPrimary = Color.White,
    background = DavePalette.AquaBottom,
    onBackground = DavePalette.Ink,
    surface = DavePalette.Card,
    onSurface = DavePalette.Ink,
    error = DavePalette.Urgent,
)

@Composable
fun FishKingTheme(content: @Composable () -> Unit) {
    // The Dave board stays deliberately bright in both system modes.
    MaterialTheme(
        colorScheme = FishKingColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
