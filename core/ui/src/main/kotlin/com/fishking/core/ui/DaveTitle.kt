package com.fishking.core.ui

import androidx.compose.material3.Text
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

@Composable
internal fun DaveTitle(text: String, modifier: Modifier = Modifier, color: Color = DavePalette.Ink,
    fontSize: TextUnit = 19.sp, lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.SemiBold, maxLines: Int = Int.MAX_VALUE,
    textDecoration: TextDecoration = TextDecoration.None) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, modifier = modifier.drawWithContent {
        drawContent()
        if (textDecoration == TextDecoration.LineThrough) layout?.let { result ->
            repeat(result.lineCount) { line ->
                val y = result.getLineBaseline(line) - (result.getLineBottom(line) - result.getLineTop(line)) * .32f
                drawLine(color, Offset(result.getLineLeft(line) - 3.dp.toPx(), y),
                    Offset(result.getLineRight(line) + 3.dp.toPx(), y), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight,
        maxLines = maxLines, onTextLayout = { layout = it })
}
