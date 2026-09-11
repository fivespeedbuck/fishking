package com.fishking.core.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*

/** Measure before drawing: no shrink/recompose loop or handoff-frame typography jump. */
@Composable
internal fun DaveCardTitle(
    text: String,
    color: Color,
    compact: Boolean,
    lineThroughProgress: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val steps = if (compact) listOf(14, 13, 12, 11, 10) else listOf(19, 18, 17, 16, 15, 14, 13)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val maxHeight = with(density) { (if (compact) 34.dp else 42.dp).roundToPx() }
        // A swipe changes completion progress every frame. Text fitting is
        // independent from that progress, so cache it instead of shaping the
        // same title at every candidate size during the first gesture.
        val size = remember(text, width, maxHeight, compact, measurer) {
            steps.firstOrNull { step ->
                val result = measurer.measure(
                    text = text,
                    style = TextStyle(fontSize = step.sp, lineHeight = (step * 1.10f).sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = width),
                )
                !result.hasVisualOverflow && result.size.height <= maxHeight
            } ?: steps.last()
        }
        DaveTitle(text, color = color, fontSize = size.sp, lineHeight = (size * 1.10f).sp,
            maxLines = 2, lineThroughProgress = lineThroughProgress)
    }
}

@Composable
internal fun DaveTitle(text: String, modifier: Modifier = Modifier, color: Color = DavePalette.Ink,
    fontSize: TextUnit = 19.sp, lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.SemiBold, maxLines: Int = Int.MAX_VALUE,
    textDecoration: TextDecoration = TextDecoration.None,
    lineThroughProgress: Float? = null) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, modifier = modifier.drawWithContent {
        drawContent()
        val strikeProgress = lineThroughProgress?.coerceIn(0f, 1f)
            ?: if (textDecoration == TextDecoration.LineThrough) 1f else 0f
        if (strikeProgress > 0f) layout?.let { result ->
            repeat(result.lineCount) { line ->
                val y = result.getLineBaseline(line) - (result.getLineBottom(line) - result.getLineTop(line)) * .32f
                val start = result.getLineLeft(line) - 3.dp.toPx()
                val end = result.getLineRight(line) + 3.dp.toPx()
                drawLine(color, Offset(start, y), Offset(start + (end - start) * strikeProgress, y),
                    strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight,
        maxLines = maxLines, onTextLayout = { layout = it })
}
