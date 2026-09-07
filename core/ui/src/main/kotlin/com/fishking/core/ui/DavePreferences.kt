package com.fishking.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalPaperTheme = staticCompositionLocalOf { false }
val LocalPresetTags = staticCompositionLocalOf<List<String>> { emptyList() }
