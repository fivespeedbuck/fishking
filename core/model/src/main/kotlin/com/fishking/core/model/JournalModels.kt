package com.fishking.core.model

import java.time.Instant
import java.time.LocalDate

data class JournalEntry(
    val id: String,
    val entryDate: LocalDate,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val entryTime: java.time.LocalTime? = null,
)

data class JournalTimelineItem(
    val id: String,
    val date: LocalDate,
    val time: java.time.LocalTime?,
    val title: String,
    val excerpt: String,
    val mediaCount: Int,
    val location: String? = null,
    val thumbnailPaths: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

enum class JournalBlockType {
    TITLE,
    TEXT_LINE,
    IMAGE,
    GIF,
    VIDEO,
    AUDIO,
    /** Ordered references to the entry's canonical metadata, not copies of it. */
    LOCATION,
    LINKS,
    TAGS,
}

/** Four deliberately small typography steps keep the journal expressive without becoming a document editor. */
enum class JournalTextSize {
    SMALL,
    BODY,
    LARGE,
    TITLE,
}

/** Paragraph layout, independent of text characters and inline styled ranges. */
enum class JournalTextAlignment { LEFT, CENTER, RIGHT }

enum class JournalListStyle { NONE, BULLET, NUMBERED, LETTERED, CHECKLIST }

/** Numbering is a projection of contiguous list items, never a literal text prefix. */
fun journalListMarker(style: JournalListStyle, ordinal: Int): String = when (style) {
    JournalListStyle.BULLET -> "•"
    JournalListStyle.NUMBERED -> "${ordinal.coerceAtLeast(1)}."
    JournalListStyle.LETTERED -> {
        var value = ordinal.coerceAtLeast(1)
        var label = ""
        while (value > 0) { value--; label = ('a' + value % 26) + label; value /= 26 }
        "$label."
    }
    else -> ""
}

/**
 * A non-overlapping, half-open styled range inside one text block.
 *
 * Null properties inherit the block's base style. Keeping offsets in the domain model makes text selection
 * styling durable across process death without leaking Compose types into Room or the repository layer.
 */
data class JournalTextStyleSpan(
    val start: Int,
    val endExclusive: Int,
    val color: Long? = null,
    val textSize: JournalTextSize? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val highlightColor: Long? = null,
)

data class JournalBlock(
    val id: String,
    val journalId: String,
    val position: Long,
    val type: JournalBlockType,
    val text: String? = null,
    val textColor: Long? = null,
    val textSize: JournalTextSize = JournalTextSize.BODY,
    val textStyleSpans: List<JournalTextStyleSpan> = emptyList(),
    val mediaUri: String? = null,
    val previewUri: String? = null,
    val mimeType: String? = null,
    val durationMillis: Long? = null,
    val mediaGroupId: String? = null,
    val mediaGroupPosition: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val textAlignment: JournalTextAlignment = JournalTextAlignment.LEFT,
    val listStyle: JournalListStyle = JournalListStyle.NONE,
    val isChecked: Boolean = false,
)

data class JournalMediaAsset(
    val id: String,
    val privatePath: String,
    val previewPath: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String? = null,
    val durationMillis: Long? = null,
    val pendingDeleteAt: Instant? = null,
    val createdAt: Instant,
)

data class JournalContentBlock(
    val block: JournalBlock,
    val media: List<JournalMediaAsset> = emptyList(),
)

data class JournalDocument(
    val entry: JournalEntry,
    val blocks: List<JournalContentBlock>,
    val linkedGoalIds: List<String>,
    val linkedTodoIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

data class JournalBlockDraft(
    val id: String? = null,
    val type: JournalBlockType,
    val text: String? = null,
    val textColor: Long? = null,
    val textSize: JournalTextSize = JournalTextSize.BODY,
    val textStyleSpans: List<JournalTextStyleSpan> = emptyList(),
    val mediaAssetIds: List<String> = emptyList(),
    val textAlignment: JournalTextAlignment = JournalTextAlignment.LEFT,
    val listStyle: JournalListStyle = JournalListStyle.NONE,
    val isChecked: Boolean = false,
)
