package com.fishking.feature.home

import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCompletionMotionTest {
    @Test
    fun everySectionIsPackedWithAtMostOneVacantHalfSlot() {
        for (openCount in 0..16) for (doneCount in 0..16) {
            val entries = (0 until openCount).map { entry("o$it", false) } + (0 until doneCount).map { entry("d$it", true) }
            assertPacked(entries, weekGridPlan(entries))
        }
    }

    @Test
    fun everyCompleteAndUndoRelocatesOnlyWhileInvisibleAndNeverOverlaps() {
        for (openCount in 1..12) for (doneCount in 0..9) {
            val entries = (0 until openCount).map { entry("o$it", false) } + (0 until doneCount).map { entry("d$it", true) }
            val presentation = WeekDayPresentation().reconcile(entries)
            entries.forEach { source ->
                val before = weekGridPlan(entries)
                val afterEntries = presentation.transferred(entries, source).keys.map { key -> entries.first { it.stableKey == key } }
                val after = weekGridPlan(afterEntries, source.stableKey)
                val survivors = entries.filterNot { it.stableKey == source.stableKey }
                for (sample in 0..100) {
                    val frame = WeekCompletionFrame.at(sample / 100f)
                    if (frame.layoutProgress > 0f && frame.layoutProgress < 1f) assertEquals(0f, frame.changedTileAlpha, .0001f)
                    val visible = survivors.mapNotNull {
                        val from = before.slots.getValue(it.stableKey)
                        val to = after.slots.getValue(it.stableKey)
                        if (from != to && frame.changedTileAlpha <= 0f) null
                        else (if (frame.useDestinationSlots) to else from) to it.isComplete
                    }
                    val height = before.height + (after.height - before.height) * frame.layoutProgress
                    visible.forEach { (slot, _) -> assertTrue("visible tile outside grid", slot.top + 72f <= height + .01f) }
                    visible.groupBy { it.first.column }.values.forEach { column ->
                        column.sortedBy { it.first.top }.zipWithNext().forEach { (a, b) ->
                            assertTrue("visible overlap at $openCount/$doneCount ${source.stableKey} frame $sample", b.first.top - a.first.top >= 77.99f)
                        }
                    }
                    val divider = before.dividerTop + (after.dividerTop - before.dividerTop) * frame.layoutProgress
                    visible.forEach { (slot, completed) ->
                        assertTrue("divider crosses a visible tile", if (completed) slot.top > divider else slot.top + 72f < divider)
                    }
                }
            }
        }
    }

    @Test
    fun repeatedRightColumnCompletionsCannotStrandAllOpenCardsOnTheLeft() {
        var entries: List<WeekEntry> = (0..11).map { entry("$it", false) }
        var presentation = WeekDayPresentation().reconcile(entries)
        repeat(30) { turn ->
            val before = presentation.applyTo(entries)
            val section = before.filter { it.isComplete == (turn % 7 == 6) }.ifEmpty { before }
            val source = section.getOrElse(1) { section.first() }
            presentation = presentation.transferred(before, source)
            entries = entries.map { if (it.stableKey == source.stableKey) toggle(it) else it }
            presentation = presentation.reconcile(entries)
            val ordered = presentation.applyTo(entries)
            assertPacked(ordered, weekGridPlan(ordered))
        }
    }

    @Test
    fun acknowledgementKeepsSettledGeometryWithoutChangingBusinessOrder() {
        val before = (0..6).map { entry("$it", false) } + entry("done", true)
        val source = before[2]
        val presentation = WeekDayPresentation().reconcile(before).transferred(before, source)
        val frozen = presentation.keys.map { key -> before.first { it.stableKey == key } }
        val planned = weekGridPlan(frozen, source.stableKey)
        val saved = before.map { if (it.stableKey == source.stableKey) toggle(it) else it }
        assertEquals(planned, weekGridPlan(presentation.reconcile(saved).applyTo(saved)))
        assertEquals(before.map { it.value.position }, saved.map { it.value.position })
        assertEquals(640, WeekCompletionMoveMillis + WeekCompletionRevealMillis)
    }

    private fun assertPacked(entries: List<WeekEntry>, plan: WeekGridPlan) {
        val open = entries.filterNot(WeekEntry::isComplete)
        val done = entries.filter(WeekEntry::isComplete)
        val openHeight = ((open.size + 1) / 2) * 78f
        val dividerHeight = if (done.isEmpty()) 0f else 17f
        listOf(open to 0f, done to (openHeight + dividerHeight)).forEach { (items, base) ->
            items.forEachIndexed { index, item ->
                assertEquals(WeekGridSlot(index % 2, base + (index / 2) * 78f + 3f), plan.slots[item.stableKey])
            }
        }
        assertEquals(openHeight + dividerHeight + ((done.size + 1) / 2) * 78f, plan.height, .001f)
        assertEquals(done.isNotEmpty(), plan.hasDivider)
    }

    private fun toggle(item: WeekEntry): WeekEntry.Todo {
        item as WeekEntry.Todo
        return item.copy(value = item.value.copy(status = if (item.isComplete) TodoStatus.OPEN else TodoStatus.COMPLETED))
    }

    private fun entry(id: String, completed: Boolean) = WeekEntry.Todo(TodoOccurrence(
        id = id, nominalDate = LocalDate.of(2026, 9, 10), displayDate = LocalDate.of(2026, 9, 10), title = id,
        status = if (completed) TodoStatus.COMPLETED else TodoStatus.OPEN, position = id.hashCode().toLong(),
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    ))
}
