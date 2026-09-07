package com.fishking.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Claim a quick horizontal swipe before BasicTextField consumes its Main-pass events.
 * Taps, vertical scrolling and long-press selection remain owned by the text editor.
 */
internal fun Modifier.daveTextSwipe(
    onStart: () -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var previous = down.position
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (claimed) change.consume()
                break
            }
            if (event.changes.count { it.pressed } > 1) break
            val total = change.position - down.position
            if (!claimed) {
                if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                if (abs(total.y) > viewConfiguration.touchSlop && abs(total.y) >= abs(total.x)) break
                if (abs(total.x) > viewConfiguration.touchSlop && abs(total.x) > abs(total.y)) {
                    claimed = true
                    onStart()
                }
            }
            if (claimed) {
                change.consume()
                onDelta(change.position.x - previous.x)
            }
            previous = change.position
        }
        if (claimed) onEnd()
    }
}
