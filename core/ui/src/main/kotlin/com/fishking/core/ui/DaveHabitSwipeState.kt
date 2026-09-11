package com.fishking.core.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class DaveHabitSwipePhase { REST, DRAGGING, SETTLING, ACTIONS, HANDOFF }
internal enum class DaveHabitSwipeDirection { NONE, LEFT, RIGHT }

internal data class DaveHabitSwipeSettle(val from: Float, val target: Float, val revision: Long)

/** One owner for pointer motion and settling. A return cannot cross into the other action. */
@Stable
internal class DaveHabitSwipeState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var direction by mutableStateOf(DaveHabitSwipeDirection.NONE)
        private set
    var phase by mutableStateOf(DaveHabitSwipePhase.REST)
        private set
    private var revision = 0L

    fun beginDrag() {
        revision++
        direction = when {
            offsetPx < 0f -> DaveHabitSwipeDirection.LEFT
            offsetPx > 0f -> DaveHabitSwipeDirection.RIGHT
            else -> DaveHabitSwipeDirection.NONE
        }
        phase = DaveHabitSwipePhase.DRAGGING
    }

    fun dragBy(delta: Float, actionWidth: Float) {
        if (phase != DaveHabitSwipePhase.DRAGGING || delta == 0f) return
        if (direction == DaveHabitSwipeDirection.NONE) {
            direction = if (delta > 0f) DaveHabitSwipeDirection.RIGHT else DaveHabitSwipeDirection.LEFT
        }
        offsetPx = if (direction == DaveHabitSwipeDirection.RIGHT) {
            (offsetPx + delta).coerceAtLeast(0f)
        } else {
            (offsetPx + delta).coerceIn(-actionWidth.coerceAtLeast(0f), 0f)
        }
    }

    fun beginSettle(target: Float): DaveHabitSwipeSettle {
        revision++
        phase = DaveHabitSwipePhase.SETTLING
        return DaveHabitSwipeSettle(offsetPx, target, revision)
    }

    fun settleFrame(settle: DaveHabitSwipeSettle, fraction: Float) {
        if (phase != DaveHabitSwipePhase.SETTLING || revision != settle.revision) return
        // Even a future overshooting animation spec cannot switch gesture sides.
        val bounded = fraction.coerceIn(0f, 1f)
        offsetPx = (settle.from + (settle.target - settle.from) * bounded)
            .coerceIn(minOf(settle.from, settle.target), maxOf(settle.from, settle.target))
    }

    fun finishSettle(settle: DaveHabitSwipeSettle): Boolean {
        if (phase != DaveHabitSwipePhase.SETTLING || revision != settle.revision) return false
        offsetPx = settle.target
        direction = if (offsetPx < 0f) DaveHabitSwipeDirection.LEFT else DaveHabitSwipeDirection.NONE
        phase = if (offsetPx < 0f) DaveHabitSwipePhase.ACTIONS else DaveHabitSwipePhase.REST
        return true
    }

    fun handOff() {
        revision++
        phase = DaveHabitSwipePhase.HANDOFF
    }
}

/** The 22dp edge fade grows with actual travel; touching zero has identical pixels. */
internal fun daveHabitSwipeFeather(offsetPx: Float, featherWidthPx: Float): Float =
    (offsetPx / featherWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** Extends the backing card under the foreground's trailing rounded corners. */
internal fun daveHabitActionRevealLeft(
    widthPx: Float,
    offsetPx: Float,
    cornerRadiusPx: Float,
): Float = (widthPx + offsetPx - cornerRadiusPx).coerceIn(0f, widthPx)
