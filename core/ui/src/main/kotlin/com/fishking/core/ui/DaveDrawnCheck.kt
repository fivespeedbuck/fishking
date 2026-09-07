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
fun DaveDrawnCheck(color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(300)) }
    Canvas(modifier) {
        val a = Offset(size.width * .12f, size.height * .51f)
        val b = Offset(size.width * .38f, size.height * .76f)
        val c = Offset(size.width * .90f, size.height * .20f)
        fun interpolate(start: Offset, end: Offset, fraction: Float) = start + (end - start) * fraction
        drawLine(color, a, interpolate(a, b, (progress.value / .35f).coerceIn(0f, 1f)), 3.dp.toPx(), StrokeCap.Round)
        if (progress.value > .35f) {
            drawLine(color, b, interpolate(b, c, ((progress.value - .35f) / .65f).coerceIn(0f, 1f)), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}
