package com.fishking.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlin.math.abs

/** A visual-only overlay outside lazy items and their swipe clips. The source retains touch ownership. */
internal class DaveDragController {
    data class Slot(val group: String, val rect: Rect)
    private val slots = mutableMapOf<String, Slot>()
    var activeId by mutableStateOf<String?>(null)
        private set
    var pointer by mutableStateOf<Offset?>(null)
        private set
    var activePointerId: Long? = null
        private set
    private var dragStart = Offset.Zero
    private var onPosition: ((Offset) -> Unit)? = null
    private var onFinished: ((Offset) -> Unit)? = null
    fun register(id: String, group: String, rect: Rect) {
        // While a date changes, the source lazy item disappears but the overlay keeps
        // the drag alive. New destination slots must be accepted immediately.
        if (id != activeId || id !in slots) slots[id] = Slot(group, rect)
    }
    fun unregister(id: String) { if (id != activeId) slots.remove(id) }
    fun groupOf(id: String): String? = slots[id]?.group
    fun beginItem(
        id: String,
        point: Offset,
        pointerId: Long,
        positionChanged: ((Offset) -> Unit)?,
        finished: (Offset) -> Unit,
    ) {
        activeId = id
        pointer = point
        activePointerId = pointerId
        dragStart = point
        onPosition = positionChanged
        onFinished = finished
    }
    fun point(point: Offset) { pointer = point }
    fun reorderTarget(): Pair<String, Boolean>? {
        val id = activeId ?: return null; val source = slots[id] ?: return null; val point = pointer ?: return null
        val candidates = slots.entries.filter {
            it.key != id && groupsSharePreviewFamily(source.group, it.value.group)
        }
        val target = candidates.filter { entry ->
            val rect = entry.value.rect
            // A lifted card should enter a neighbour's hit area before their
            // centres overlap.  The old 14% margin made two-column week cards
            // feel unresponsive because the pointer had to travel almost a
            // full card height.  42% of the short edge is roughly 28dp on the
            // target device while still keeping adjacent slots distinct.
            val margin = minOf(rect.width, rect.height) * .42f
            Rect(rect.left - margin, rect.top - margin, rect.right + margin, rect.bottom + margin).contains(point)
        }.minByOrNull { (_, slot) -> (slot.rect.center - point).getDistance() }
        return target
            ?.let { it.key to (point.y > it.value.rect.center.y || kotlin.math.abs(point.y - it.value.rect.center.y) < it.value.rect.height * .25f && point.x > it.value.rect.center.x) }
    }
    fun previewOffset(id: String): Offset {
        val active = activeId ?: return Offset.Zero
        val source = slots[active] ?: return Offset.Zero
        val target = reorderTarget() ?: return Offset.Zero
        val slot = slots[id] ?: return Offset.Zero
        if (id == active) return Offset.Zero
        val targetGroup = slots[target.first]?.group ?: return Offset.Zero
        if (targetGroup == source.group) {
            if (slot.group != source.group) return Offset.Zero
            val ordered = orderedSlots(source.group)
            val original = ordered.map { it.key }
            val moved = original.toMutableList().apply {
                remove(active)
                add((indexOf(target.first) + if (target.second) 1 else 0).coerceIn(0, size), active)
            }
            val oldIndex = original.indexOf(id)
            val newIndex = moved.indexOf(id)
            if (oldIndex < 0 || newIndex < 0) return Offset.Zero
            return ordered[newIndex].value.rect.topLeft - ordered[oldIndex].value.rect.topLeft
        }
        if (slot.group != targetGroup) return Offset.Zero
        val ordered = orderedSlots(targetGroup)
        val original = ordered.map { it.key }
        val insertionIndex = (original.indexOf(target.first) + if (target.second) 1 else 0).coerceIn(0, original.size)
        val oldIndex = original.indexOf(id)
        if (oldIndex < insertionIndex || oldIndex < 0) return Offset.Zero
        val newTopLeft = if (oldIndex + 1 < ordered.size) {
            ordered[oldIndex + 1].value.rect.topLeft
        } else {
            nextSlotTopLeft(ordered)
        }
        return newTopLeft - ordered[oldIndex].value.rect.topLeft
    }

    private fun orderedSlots(group: String) = slots.entries
        .filter { it.value.group == group }
        .sortedWith(compareBy({ it.value.rect.top }, { it.value.rect.left }))

    private fun groupsSharePreviewFamily(first: String, second: String): Boolean {
        if (first == second) return true
        if ('|' !in first || '|' !in second) return false
        return first.substringBefore('|') == second.substringBefore('|')
    }

    private fun nextSlotTopLeft(ordered: List<Map.Entry<String, Slot>>): Offset {
        val last = ordered.last().value.rect
        if (ordered.size >= 2) {
            val previous = ordered[ordered.lastIndex - 1].value.rect
            if (abs(previous.top - last.top) < minOf(previous.height, last.height) * .35f) {
                val firstColumnLeft = ordered.minOf { it.value.rect.left }
                val rowGap = ordered.zipWithNext()
                    .map { (a, b) -> b.value.rect.top - a.value.rect.bottom }
                    .filter { it > 0f }
                    .minOrNull()
                    ?: 6f
                return Offset(firstColumnLeft, last.bottom + rowGap)
            }
        }
        return Offset(last.left, last.bottom + 6f)
    }
    var bounds by mutableStateOf<Rect?>(null)
        private set
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    private var origin = Rect.Zero

    fun start(rect: Rect, draw: @Composable () -> Unit) {
        origin = rect
        bounds = rect
        content = draw
    }

    fun move(delta: Offset) { bounds = origin.translate(delta) }
    fun dragTo(point: Offset) {
        pointer = point
        bounds = origin.translate(point - dragStart)
        onPosition?.invoke(point)
    }
    fun release(point: Offset) {
        dragTo(point)
        val commit = onFinished
        commit?.invoke(point)
        finish()
    }
    fun finish() {
        bounds = null
        content = null
        activeId = null
        pointer = null
        activePointerId = null
        onPosition = null
        onFinished = null
    }
}

internal val LocalDaveDragController = staticCompositionLocalOf<DaveDragController?> { null }
val LocalDaveReorderCommit = staticCompositionLocalOf<((String, String, Boolean) -> Unit)?> { null }

@Composable
fun DaveDragLayer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val controller = remember { DaveDragController() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (controller.activePointerId == down.id.value) {
                            val rootPoint = origin + change.position
                            if (change.pressed) controller.dragTo(rootPoint) else controller.release(rootPoint)
                        }
                        if (!change.pressed) break
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalDaveDragController provides controller) { content() }
        val bounds = controller.bounds
        val draw = controller.content
        if (bounds != null && draw != null) {
            val density = LocalDensity.current
            Box(
                Modifier
                    .zIndex(100f)
                    .offset { IntOffset((bounds.left - origin.x).roundToInt(), (bounds.top - origin.y).roundToInt()) }
                    .width(with(density) { bounds.width.toDp() })
                    .graphicsLayer { shadowElevation = 12f; alpha = .95f }
                    .clearAndSetSemantics {},
            ) { draw() }
        }
    }
}
