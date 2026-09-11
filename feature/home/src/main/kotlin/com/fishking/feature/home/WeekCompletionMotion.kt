package com.fishking.feature.home

/** Week-only reading order. Columns are derived afresh, never permanent identity. */
internal data class WeekDayPresentation(
    val keys: List<String> = emptyList(),
) {
    fun reconcile(entries: List<WeekEntry>): WeekDayPresentation {
        val rank = keys.withIndex().associate { it.value to it.index }
        return WeekDayPresentation(entries.sortedBy { rank[it.stableKey] ?: Int.MAX_VALUE }.map(WeekEntry::stableKey))
    }

    fun applyTo(entries: List<WeekEntry>): List<WeekEntry> {
        val rank = keys.withIndex().associate { it.value to it.index }
        val ordered = entries.sortedBy { rank[it.stableKey] ?: Int.MAX_VALUE }
        return ordered.filterNot(WeekEntry::isComplete) + ordered.filter(WeekEntry::isComplete)
    }

    fun transferred(before: List<WeekEntry>, source: WeekEntry): WeekDayPresentation = copy(
        keys = (before.filter { !it.isComplete && it.stableKey != source.stableKey } + source +
            before.filter { it.isComplete && it.stableKey != source.stableKey }).map(WeekEntry::stableKey),
    )
}

internal data class WeekGridSlot(val column: Int, val top: Float)
internal data class WeekGridPlan(
    val slots: Map<String, WeekGridSlot>,
    val height: Float,
    val dividerTop: Float,
    val hasDivider: Boolean,
)

internal const val WeekCompletionMoveMillis = 420
internal const val WeekCompletionRevealMillis = 220

/**
 * Each section packs left-to-right, then top-to-bottom. At rest there can be
 * at most one vacant half-slot in each section, regardless of completion history.
 * Keeping an immutable column per key caused an entire open/done section to
 * accumulate in only one column after repeated toggles.
 */
internal fun weekGridPlan(
    entries: List<WeekEntry>,
    toggledKey: String? = null,
): WeekGridPlan {
    val (completed, open) = entries.partition { if (it.stableKey == toggledKey) !it.isComplete else it.isComplete }
    fun rows(items: List<WeekEntry>) = (items.size + 1) / 2
    val openHeight = rows(open) * 78f
    val dividerHeight = if (completed.isNotEmpty()) 17f else 0f
    val slots = buildMap {
        fun place(items: List<WeekEntry>, base: Float) {
            items.forEachIndexed { index, entry ->
                put(entry.stableKey, WeekGridSlot(index % 2, base + (index / 2) * 78f + 3f))
            }
        }
        place(open, 0f)
        place(completed, openHeight + dividerHeight)
    }
    return WeekGridPlan(slots, openHeight + dividerHeight + rows(completed) * 78f, openHeight + 8f, completed.isNotEmpty())
}

/**
 * Changed tiles fade at their old positions, repack only while fully invisible,
 * then reveal at their new positions. No diagonal travel, overlapping proxies,
 * per-card stagger, or count-dependent duration is needed for a dense grid.
 */
internal data class WeekCompletionFrame(
    val flight: Float,
    val layoutProgress: Float,
    val useDestinationSlots: Boolean,
    val changedTileAlpha: Float,
) {
    companion object {
        fun at(value: Float): WeekCompletionFrame {
            val time = value.coerceIn(0f, 1f)
            val layout = ((time - .48f) / .26f).coerceIn(0f, 1f)
            return WeekCompletionFrame(
                flight = (time / .5f).coerceIn(0f, 1f),
                layoutProgress = layout * layout * (3f - 2f * layout),
                useDestinationSlots = time >= .61f,
                changedTileAlpha = when {
                    time < .48f -> (1f - (time - .24f) / .24f).coerceIn(0f, 1f)
                    time < .74f -> 0f
                    else -> ((time - .74f) / .26f).coerceIn(0f, 1f)
                },
            )
        }
    }
}
