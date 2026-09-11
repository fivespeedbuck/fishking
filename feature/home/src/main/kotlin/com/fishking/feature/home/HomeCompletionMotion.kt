package com.fishking.feature.home

import com.fishking.core.model.TodoOccurrence
import kotlin.math.roundToInt

/** Explicit single-day order; week presentation keeps its existing rules. */
internal data class HomeDayItems(
    val open: List<HomeDisplayItem>,
    val completed: List<HomeDisplayItem>,
) {
    fun moveAcrossDivider(todo: TodoOccurrence): HomeDayItems = moveAcrossDivider(HomeDisplayItem.Todo(todo))

    fun moveAcrossDivider(target: HomeDisplayItem): HomeDayItems {
        val sourceKey = target.stableKey
        val remainingOpen = open.filterNot { it.stableKey == sourceKey }
        val remainingCompleted = completed.filterNot { it.stableKey == sourceKey }
        return if (target.isCompleteOnHome) {
            HomeDayItems(remainingOpen, listOf(target) + remainingCompleted)
        } else {
            // Undo is the geometric mirror: append beside the divider, not at
            // the top of the open list. Only the divider and completed cards
            // between it and the source move down; everything else stays put.
            HomeDayItems(remainingOpen + target, remainingCompleted)
        }
    }
}

internal val HomeDisplayItem.isCompleteOnHome: Boolean
    get() = when (this) {
        is HomeDisplayItem.Todo -> value.isCompleted
        is HomeDisplayItem.Habit -> isComplete
    }

internal fun HomeDisplaySections.singleDayItems(): HomeDayItems = HomeDayItems(
    open = open,
    // Preserve the pre-experiment day-page order for existing completed
    // records. A transferred card is explicitly inserted by the divider.
    completed = completedUrgent + completedNormal + completedHabits,
)

/**
 * Preserve the destination order after fading in, rather than briefly putting
 * a card by the divider and then snapping it back to its database sort index.
 * An explicit drag reorder (changed position on an existing item) takes
 * precedence. This is a day-local presentation order, not a new stored fact.
 */
internal data class HomeDayOrder(
    val openKeys: List<String>,
    val completedKeys: List<String>,
    val positions: Map<String, Long>,
) {
    fun applyTo(sections: HomeDisplaySections): HomeDayItems {
        val baseline = sections.singleDayItems()
        val current = baseline.open + baseline.completed
        if (current.any { item -> positions[item.stableKey]?.let { it != item.position } == true }) {
            return baseline
        }
        return HomeDayItems(
            mergeOrder(baseline.open, openKeys),
            mergeOrder(baseline.completed, completedKeys),
        )
    }

    companion object {
        fun capture(items: HomeDayItems, sections: HomeDisplaySections) = HomeDayOrder(
            openKeys = items.open.map(HomeDisplayItem::stableKey),
            completedKeys = items.completed.map(HomeDisplayItem::stableKey),
            positions = (sections.open + sections.completed).associate { it.stableKey to it.position },
        )

        private fun mergeOrder(current: List<HomeDisplayItem>, keys: List<String>): List<HomeDisplayItem> {
            val byKey = current.associateBy(HomeDisplayItem::stableKey)
            val result = keys.mapNotNull(byKey::get).toMutableList()
            val known = result.mapTo(mutableSetOf(), HomeDisplayItem::stableKey)
            // New drafts/records retain their canonical position relative to
            // their next surviving neighbour, rather than always going last.
            current.forEachIndexed { index, item ->
                if (known.add(item.stableKey)) {
                    val next = current.drop(index + 1).firstOrNull { next ->
                        result.any { it.stableKey == next.stableKey }
                    }
                    val insertAt = next?.let { next -> result.indexOfFirst { it.stableKey == next.stableKey } }
                        ?: result.size
                    result.add(insertAt, item)
                }
            }
            return result
        }
    }
}

internal data class HomeCompletionFrame(val flight: Float, val reflow: Float) {
    companion object {
        /** One clock drives the flight and both complementary slot heights. */
        fun at(time: Float): HomeCompletionFrame {
            val t = time.coerceIn(0f, 1f)
            return HomeCompletionFrame(
                flight = ease((t / .78f).coerceIn(0f, 1f)),
                // Give the outgoing card a visible head start. Reflow then
                // overlaps the latter flight and settles before target fade.
                reflow = ease(((t - .30f) / .70f).coerceIn(0f, 1f)),
            )
        }

        private fun ease(value: Float): Float = value * value * (3f - 2f * value)
    }
}

internal data class HomeCompletionSlotHeights(val source: Int, val destination: Int)

internal fun homeCompletionSlotHeights(heightPx: Int, progress: Float): HomeCompletionSlotHeights {
    val height = heightPx.coerceAtLeast(0)
    val destination = (height * progress.coerceIn(0f, 1f)).roundToInt()
    // Subtract after rounding: the two slots always add to the exact original
    // number of pixels. Thus opposite-side cards cannot drift even one pixel.
    return HomeCompletionSlotHeights(source = height - destination, destination = destination)
}
