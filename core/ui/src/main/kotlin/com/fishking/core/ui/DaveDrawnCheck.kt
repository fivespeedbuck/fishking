package com.fishking.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** A short first stroke followed by the ascending stroke, rather than a popped-in glyph. */
@Composable
fun DaveDrawnCheck(color: Color, modifier: Modifier = Modifier, durationMillis: Int = 300) {
    // A static landing must be complete in its very first frame. A 1 ms
    // animation still paints a blank first frame before LaunchedEffect runs.
    val progress = remember { Animatable(if (durationMillis <= 1) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(durationMillis.coerceAtLeast(1)))
    }
    DaveDrawnCheck(color = color, progress = progress.value, modifier = modifier)
}

/** A gesture-driven variant used while a completion swipe is still under the finger. */
@Composable
fun DaveDrawnCheck(color: Color, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val a = Offset(size.width * .12f, size.height * .51f)
        val b = Offset(size.width * .38f, size.height * .76f)
        val c = Offset(size.width * .90f, size.height * .20f)
        fun interpolate(start: Offset, end: Offset, fraction: Float) = start + (end - start) * fraction
        val value = progress.coerceIn(0f, 1f)
        drawLine(color, a, interpolate(a, b, (value / .35f).coerceIn(0f, 1f)), 3.dp.toPx(), StrokeCap.Round)
        if (value > .35f) {
            drawLine(color, b, interpolate(b, c, ((value - .35f) / .65f).coerceIn(0f, 1f)), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}
