package com.fishking.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import java.time.LocalDate

internal val LocalDaveDateDrop = staticCompositionLocalOf<((String, Offset) -> Boolean)?> { null }

@Composable
fun DaveDateDropArea(
    date: LocalDate,
    onMove: (String, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onDrop: ((String, Offset) -> Boolean)? = null,
    content: @Composable BoxScope.() -> Unit) {
    // Kept as a compatibility wrapper. Date changes now happen through the
    // viewport-edge drag gesture, so no visual previous/next drop cards are laid out.
    @Suppress("UNUSED_VARIABLE") val compatibility = date to onMove
    val currentDrop by rememberUpdatedState(onDrop)
    val stableDrop = remember { { id: String, point: Offset -> currentDrop?.invoke(id, point) ?: false } }
    CompositionLocalProvider(LocalDaveDateDrop provides stableDrop) {
        Box(modifier.fillMaxSize(), content = content)
    }
}
