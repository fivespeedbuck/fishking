package com.fishking.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val DaveMacaronColors = listOf(
    0xFFE4A0B2L, 0xFFEBC083L, 0xFFD9C66BL, 0xFF9BC18FL,
    0xFF7DBEB7L, 0xFF8FAED6L, 0xFFB29AD3L, 0xFFDB9A89L,
)

/** Equal-width, readable macaron colours plus the rainbow; no unused trailing strip. */
@Composable
fun DaveMacaronPalette(
    selected: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    includeDefaultInk: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val presets = DaveMacaronColors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (includeDefaultInk) {
            val active = selected == null
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                    .background(DavePalette.Ink, CircleShape)
                    .border(if (active) 3.dp else 1.dp, if (active) DavePalette.HeaderGreenDark else Color.White.copy(alpha = .9f), CircleShape)
                    .clickable { onSelected(null) }
                    .semantics { contentDescription = "恢复默认黑色" },
            )
        }
        presets.forEach { argb ->
            val active = selected == argb
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                    .background(Color(argb), CircleShape)
                    .border(if (active) 3.dp else 1.dp, if (active) DavePalette.HeaderGreenDark else Color.White.copy(alpha = .9f), CircleShape)
                    .clickable { onSelected(argb) }
                    .semantics { contentDescription = "选择马卡龙颜色" },
            )
        }
        val customActive = selected != null && selected !in presets
        Box(
            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                .background(
                    Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                    CircleShape,
                )
                .border(if (customActive) 3.dp else 1.dp, if (customActive) DavePalette.HeaderGreenDark else Color.White.copy(alpha = .9f), CircleShape)
                .clickable { pickerOpen = true }
                .semantics { contentDescription = "打开自选颜色" },
        )
    }
    if (pickerOpen) DaveColorPicker(selected, onSelected) { pickerOpen = false }
}

@Composable
fun DaveColorPicker(selected: Long?, onSelected: (Long?) -> Unit, onDismiss: () -> Unit) {
    val initial = remember(selected) {
        FloatArray(3).also { android.graphics.Color.colorToHSV((selected ?: 0xFF355C52).toInt(), it) }
    }
    var hue by remember(selected) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(selected) { mutableFloatStateOf(initial[1]) }
    var brightness by remember(selected) { mutableFloatStateOf(initial[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(DavePalette.Card, RoundedCornerShape(16.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("直接取色", color = DavePalette.Ink)
            Box(Modifier.fillMaxWidth().height(46.dp).background(color, RoundedCornerShape(9.dp)))
            Text("常用马卡龙色", color = DavePalette.Meta)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DaveMacaronColors.forEach { argb ->
                    Box(
                        Modifier
                            .size(31.dp)
                            .clip(CircleShape)
                            .background(Color(argb), CircleShape)
                            .border(
                                if ((color.toArgb().toLong() and 0xFFFFFFFFL) == argb) 3.dp else 1.dp,
                                Color.White,
                                CircleShape,
                            )
                            .clip(CircleShape)
                            .clickable {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb.toInt(), hsv)
                                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                            }
                            .semantics { contentDescription = "选择马卡龙颜色" },
                    )
                }
            }

            Text("色盘 · 按住拖动取色", color = DavePalette.Meta)
            val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            Canvas(
                Modifier.fillMaxWidth().height(176.dp).pointerInput(hue) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(point: Offset) {
                            saturation = (point.x / size.width).coerceIn(0f, 1f)
                            brightness = (1f - point.y / size.height).coerceIn(0f, 1f)
                        }
                        update(down.position)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            update(change.position); change.consume()
                        } while (change.pressed)
                    }
                }.semantics { contentDescription = "颜色二维色盘" },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val center = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.Black.copy(alpha = .45f), 10.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(Color.White, 8.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            }

            Canvas(
                Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(point: Offset) { hue = (point.x / size.width).coerceIn(0f, 1f) * 360f }
                        update(down.position)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            update(change.position); change.consume()
                        } while (change.pressed)
                    }
                }.semantics { contentDescription = "色相彩虹带" },
            ) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
                drawCircle(Color.White, 8.dp.toPx(), Offset(hue / 360f * size.width, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            }

            Text("精细调整", color = DavePalette.Meta)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("色相", color = DavePalette.Meta, modifier = Modifier.size(width = 52.dp, height = 24.dp))
                Slider(hue, { hue = it }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("饱和度", color = DavePalette.Meta, modifier = Modifier.size(width = 52.dp, height = 24.dp))
                Slider(saturation, { saturation = it }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("明暗", color = DavePalette.Meta, modifier = Modifier.size(width = 52.dp, height = 24.dp))
                Slider(brightness, { brightness = it }, modifier = Modifier.weight(1f))
            }

            Text("#%06X".format(color.toArgb() and 0xFFFFFF), color = DavePalette.Ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSelected(null); onDismiss() }) { Text("默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = { onSelected(color.toArgb().toLong() and 0xFFFFFFFFL); onDismiss() }) { Text("应用") }
            }
        }
    }
}
