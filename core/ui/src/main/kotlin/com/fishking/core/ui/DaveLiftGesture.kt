package com.fishking.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The whole card can be lifted; short taps and horizontal swipes retain their actions. */
@Composable
internal fun rememberDaveLiftModifier(
    key: String, enabled: Boolean,
    draw: @Composable () -> Unit,
    onPosition: ((Offset) -> Unit)?, onDrop: ((Offset) -> Unit)?,
    group: String = key,
    previewReorder: Boolean = true,
): Modifier {
    val layer = LocalDaveDragController.current
    val haptics = LocalHapticFeedback.current
    val dateDrop by rememberUpdatedState(LocalDaveDateDrop.current)
    val reorder by rememberUpdatedState(LocalDaveReorderCommit.current)
    var bounds by remember(key) { mutableStateOf(Rect.Zero) }
    val position by rememberUpdatedState(onPosition)
    val drop by rememberUpdatedState(onDrop)
    val currentDraw by rememberUpdatedState(draw)
    DisposableEffect(key, enabled) { onDispose { if (enabled && onDrop != null) layer?.unregister(key) } }
    val targetOffset = if (previewReorder) layer?.previewOffset(key) ?: Offset.Zero else Offset.Zero
    val offsetX by androidx.compose.animation.core.animateFloatAsState(targetOffset.x, androidx.compose.animation.core.tween(160), label = "reorder x")
    val offsetY by androidx.compose.animation.core.animateFloatAsState(targetOffset.y, androidx.compose.animation.core.tween(160), label = "reorder y")
    return Modifier.onGloballyPositioned { bounds = it.boundsInRoot(); if (enabled && onDrop != null) layer?.register(key, group, bounds) }
        .semantics { if (enabled && onDrop != null) contentDescription = "长按拖动待办" }
        .graphicsLayer { alpha = if (layer?.activeId == key) 0f else 1f; translationX = offsetX; translationY = offsetY }
        .then(if (!enabled || onDrop == null) Modifier else Modifier.pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Child click handlers consume DOWN. That is not a cancelled hold.
                // Decide from physical motion/up, so a small card's labels/time control cannot steal it.
                val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!change.pressed || event.changes.count { it.pressed } > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (cancelled == true) return@awaitEachGesture
                val held = down
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val start = bounds.topLeft + held.position
                layer?.start(bounds, currentDraw)
                layer?.beginItem(
                    id = key,
                    point = start,
                    pointerId = held.id.value,
                    positionChanged = { point -> position?.invoke(point) },
                    finished = { target ->
                        val crossDateHandled = dateDrop?.invoke(key, target) == true
                        if (!crossDateHandled && previewReorder) {
                            val reorderTarget = layer?.reorderTarget()
                            if (reorderTarget != null && reorder != null && layer?.groupOf(reorderTarget.first) == group) {
                                reorder?.invoke(key, reorderTarget.first, reorderTarget.second)
                            }
                        }
                        // A date-drop host has already received this release even when it
                        // returns false (same-date reorder). Do not deliver it twice.
                        if (dateDrop == null) drop?.invoke(target)
                    },
                )
                position?.invoke(start)
                while (layer?.activeId == key) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == held.id } ?: break
                    if (event.changes.count { it.pressed } > 1) { layer?.finish(); break }
                    change.consume()
                    if (!change.pressed) break
                }
            }
        })
}
