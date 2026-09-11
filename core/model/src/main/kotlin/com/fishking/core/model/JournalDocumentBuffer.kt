package com.fishking.core.model

import java.time.Instant
import java.util.UUID

/** One UTF-16 document; each attachment consumes one atomic object character. */
data class JournalDocumentBuffer(val nodes: List<JournalDocumentNode>) {
    val renderedText: String = nodes.joinToString("") { node ->
        if (node is JournalDocumentNode.AttachmentNode) OBJECT_REPLACEMENT.toString() else node.block.text.orEmpty()
    }
    val length: Int get() = renderedText.length

    fun tokenFor(id: String): DocumentToken? = nodes.filterIsInstance<JournalDocumentNode.AttachmentNode>()
        .firstOrNull { it.block.id == id }?.let { DocumentToken(id) }

    fun ranges(): List<JournalNodeRange> {
        var offset = 0
        return nodes.map { node ->
            val start = offset
            offset += if (node is JournalDocumentNode.AttachmentNode) 1 else node.block.text.orEmpty().length
            JournalNodeRange(node, start, offset)
        }
    }

    fun attachmentIdAt(offset: Int): String? = ranges().firstOrNull {
        it.node is JournalDocumentNode.AttachmentNode && it.start == offset
    }?.node?.block?.id

    fun insertAttachment(offset: Int, block: JournalBlock): JournalDocumentBuffer =
        replace(offset, offset, listOf(JournalDocumentNode.AttachmentNode(block)))

    fun deleteAttachment(id: String): JournalDocumentBuffer {
        val range = ranges().firstOrNull { it.node is JournalDocumentNode.AttachmentNode && it.node.block.id == id } ?: return this
        return replace(range.start, range.endExclusive, emptyList())
    }

    fun replaceText(start: Int, endExclusive: Int, replacement: String): JournalDocumentBuffer {
        val safe = replacement.replace(OBJECT_REPLACEMENT.toString(), "")
        if (safe.isEmpty()) return replace(start, endExclusive, emptyList())
        val anchor = ranges().lastOrNull {
            it.node is JournalDocumentNode.TextNode && it.start <= start && it.endExclusive >= start
        }
        val base = anchor?.node?.block ?: emptyTextBlock()
        val local = (start - (anchor?.start ?: 0)).coerceIn(0, base.text.orEmpty().length)
        val inherited = base.textStyleSpans.lastOrNull { local > it.start && local <= it.endExclusive }
        val inserted = base.copy(
            id = if (start == anchor?.start) base.id else newTextId(), text = safe,
            textStyleSpans = inherited?.let { listOf(it.copy(start = 0, endExclusive = safe.length)) }.orEmpty(),
        )
        val result = replace(start, endExclusive, listOf(JournalDocumentNode.TextNode(inserted)))
        if ('\n' !in safe) return result
        // Return starts a fresh item, including the suffix when splitting a
        // checked row in its middle. Never inherit a completed check.
        val firstNewParagraph = start + safe.indexOf('\n') + 1
        return result.copy(nodes = result.ranges().map { range ->
            if (range.node is JournalDocumentNode.TextNode && range.start in firstNewParagraph..(start + safe.length)) {
                range.node.copy(block = range.node.block.copy(isChecked = false, type = JournalBlockType.TEXT_LINE))
            } else range.node
        })
    }

    /** Only an explicit Return invokes smart continuation; pasted text is literal. */
    fun editText(start: Int, endExclusive: Int, replacement: String): JournalDocumentEdit {
        val from = start.coerceIn(0, length)
        val to = endExclusive.coerceIn(from, length)
        val sanitized = replacement.replace(OBJECT_REPLACEMENT.toString(), "")
        if (sanitized == "\n" && from == to) {
            val paragraph = paragraphRange(from)
            val paragraphStart = paragraph.first
            val paragraphEnd = paragraph.last + 1
            val node = ranges().lastOrNull { it.node is JournalDocumentNode.TextNode && it.start <= from && it.endExclusive >= from }
            val isChecklist = node?.node?.block?.listStyle == JournalListStyle.CHECKLIST
            val beforeCaret = renderedText.substring(paragraphStart, from)
            val afterCaret = renderedText.substring(from, paragraphEnd)
            if (isChecklist && beforeCaret.isBlank() && afterCaret.isBlank()) {
                val cleared = if (paragraphStart == paragraphEnd) this else replaceText(paragraphStart, paragraphEnd, "")
                val plain = cleared.styleParagraphs(paragraphStart, paragraphStart) { it.copy(listStyle = JournalListStyle.NONE, isChecked = false) }
                return JournalDocumentEdit(plain, paragraphStart)
            }
            if (!isChecklist) {
                val marker = journalTypedListPrefix(beforeCaret)
                if (marker != null) {
                    if (beforeCaret.substring(marker.original.length).isBlank() && afterCaret.isBlank()) {
                        return JournalDocumentEdit(replaceText(paragraphStart, paragraphEnd, ""), paragraphStart)
                    }
                    val inserted = "\n" + marker.next
                    return JournalDocumentEdit(replaceText(from, to, inserted), from + inserted.length)
                }
            }
        }
        return JournalDocumentEdit(replaceText(from, to, sanitized), from + sanitized.length)
    }

    /** Android supplies the exact edit range, avoiding ambiguous repeated-token diffs. */
    fun replace(start: Int, endExclusive: Int, inserted: List<JournalDocumentNode>): JournalDocumentBuffer {
        val from = start.coerceIn(0, length)
        val to = endExclusive.coerceIn(from, length)
        return of(slice(0, from) + inserted + slice(to, length))
    }

    /** Plain-text replacement helper; native typing uses Android's exact edit range. */
    fun reconcile(edited: String): JournalDocumentBuffer {
        if (edited == renderedText) return this
        var prefix = 0
        while (prefix < length && prefix < edited.length && renderedText[prefix] == edited[prefix]) prefix++
        var suffix = 0
        while (suffix < length - prefix && suffix < edited.length - prefix &&
            renderedText[length - suffix - 1] == edited[edited.length - suffix - 1]) suffix++
        return replaceText(prefix, length - suffix, edited.substring(prefix, edited.length - suffix))
    }

    /** Character formatting changes only selected text; object atoms are never styled. */
    fun styleText(start: Int, endExclusive: Int, transform: (JournalTextStyleSpan) -> JournalTextStyleSpan): JournalDocumentBuffer {
        val from = start.coerceIn(0, length)
        val to = endExclusive.coerceIn(from, length)
        return copy(nodes = ranges().map { range ->
            val node = range.node
            if (node !is JournalDocumentNode.TextNode || range.endExclusive <= from || range.start >= to) node
            else {
                val block = node.block
                val text = block.text.orEmpty()
                val localStart = (from - range.start).coerceAtLeast(0)
                val localEnd = (to - range.start).coerceAtMost(text.length)
                val boundaries = (listOf(0, text.length, localStart, localEnd) + block.textStyleSpans.flatMap { listOf(it.start, it.endExclusive) })
                    .filter { it in 0..text.length }.distinct().sorted()
                val spans = boundaries.zipWithNext().map { (s, e) ->
                    val inherited = effectiveStyle(block, s).copy(start = s, endExclusive = e)
                    if (s < localEnd && e > localStart) transform(inherited).copy(start = s, endExclusive = e) else inherited
                }
                node.copy(block = block.copy(textStyleSpans = mergeStyles(spans)))
            }
        })
    }

    fun paragraphRange(start: Int, endExclusive: Int = start): IntRange {
        val from = start.coerceIn(0, length)
        val to = endExclusive.coerceIn(from, length)
        fun boundary(c: Char) = c == '\n' || c == OBJECT_REPLACEMENT
        var left = from
        while (left > 0 && !boundary(renderedText[left - 1])) left--
        var right = if (to > from) to - 1 else to
        while (right < length && !boundary(renderedText[right])) right++
        return left until right
    }

    fun styleParagraphs(start: Int, endExclusive: Int, transform: (JournalBlock) -> JournalBlock): JournalDocumentBuffer {
        val range = paragraphRange(start, endExclusive)
        val from = range.first.coerceIn(0, length)
        val paragraphEnd = (range.last + 1).coerceIn(from, length)
        val to = if (renderedText.getOrNull(paragraphEnd) == '\n') paragraphEnd + 1 else paragraphEnd
        val selected = slice(from, to).map { node ->
            if (node is JournalDocumentNode.TextNode) node.copy(block = transform(node.block)) else node
        }
        val styled = selected.ifEmpty { listOf(JournalDocumentNode.TextNode(transform(emptyTextBlock()))) }
        return of(slice(0, from) + styled + slice(to, length))
    }

    fun isStyleActive(start: Int, endExclusive: Int, predicate: (JournalTextStyleSpan) -> Boolean): Boolean {
        val styles = ranges().flatMap { range ->
            if (range.node !is JournalDocumentNode.TextNode) emptyList()
            else (maxOf(start, range.start) until minOf(endExclusive, range.endExclusive)).map {
                effectiveStyle(range.node.block, it - range.start)
            }
        }
        return styles.isNotEmpty() && styles.all(predicate)
    }

    fun toLegacyBlocks(): List<JournalBlock> = nodes.map { it.block }

    fun listOrdinal(nodeId: String): Int {
        var previous = JournalListStyle.NONE
        var ordinal = 0
        nodes.forEach { node ->
            val style = if (node is JournalDocumentNode.TextNode) node.block.listStyle else JournalListStyle.NONE
            ordinal = if (style == JournalListStyle.NONE) 0 else if (style == previous) ordinal + 1 else 1
            if (node.block.id == nodeId) return ordinal.coerceAtLeast(1)
            previous = style
        }
        return 1
    }

    private fun slice(start: Int, end: Int): List<JournalDocumentNode> = ranges().mapNotNull { range ->
        if (range.endExclusive <= start || range.start >= end) return@mapNotNull null
        val node = range.node
        if (node is JournalDocumentNode.AttachmentNode) node else {
            val block = node.block
            val from = (start - range.start).coerceAtLeast(0)
            val to = (end - range.start).coerceAtMost(block.text.orEmpty().length)
            JournalDocumentNode.TextNode(block.copy(
                id = if (from == 0) block.id else newTextId(),
                text = block.text.orEmpty().substring(from, to),
                textStyleSpans = block.textStyleSpans.mapNotNull { span ->
                    val s = maxOf(from, span.start); val e = minOf(to, span.endExclusive)
                    if (s >= e) null else span.copy(start = s - from, endExclusive = e - from)
                },
            ))
        }
    }

    companion object {
        const val OBJECT_REPLACEMENT = '\uFFFC'

        fun fromLegacy(blocks: List<JournalBlock>): JournalDocumentBuffer = fromParagraphNodes(blocks.sortedBy { it.position }.map { block ->
            if (block.type == JournalBlockType.TEXT_LINE || block.type == JournalBlockType.TITLE) JournalDocumentNode.TextNode(block)
            else JournalDocumentNode.AttachmentNode(block)
        })

        /**
         * Storage/legacy text blocks denote paragraphs, unlike fragments made
         * by a range replacement. Add a real newline at a missing boundary
         * once; canonical persisted paragraphs already contain that newline.
         */
        fun fromParagraphNodes(nodes: List<JournalDocumentNode>): JournalDocumentBuffer {
            val meaningful = nodes.filterNot {
                it is JournalDocumentNode.TextNode && it.block.type == JournalBlockType.TITLE && it.block.text.isNullOrEmpty()
            }
            return of(meaningful.mapIndexed { index, node ->
                if (node !is JournalDocumentNode.TextNode) node else {
                    val text = node.block.text.orEmpty()
                    val next = meaningful.getOrNull(index + 1)
                    val needsBoundary = next is JournalDocumentNode.TextNode && !text.endsWith('\n')
                    node.copy(block = node.block.copy(
                        type = node.block.type,
                        text = if (needsBoundary) text + "\n" else text,
                        // Only the checkbox tool owns structural list state.
                        // Number/letter/bullet continuation now consists of
                        // literal user-visible characters, not hidden modes.
                        listStyle = if (node.block.listStyle == JournalListStyle.CHECKLIST) JournalListStyle.CHECKLIST else JournalListStyle.NONE,
                        isChecked = node.block.listStyle == JournalListStyle.CHECKLIST && node.block.isChecked,
                    ))
                }
            })
        }

        /** Normalize edit fragments to one text node per real paragraph. */
        fun of(nodes: List<JournalDocumentNode>): JournalDocumentBuffer {
            val result = mutableListOf<JournalDocumentNode>()
            val usedIds = mutableSetOf<String>()
            fun append(node: JournalDocumentNode) {
                val previous = result.lastOrNull() as? JournalDocumentNode.TextNode
                if (node is JournalDocumentNode.TextNode && previous != null &&
                    !previous.block.text.orEmpty().endsWith('\n')) {
                    val left = previous.block; val right = node.block
                    val offset = left.text.orEmpty().length
                    result[result.lastIndex] = JournalDocumentNode.TextNode(left.copy(
                        text = left.text.orEmpty() + right.text.orEmpty(),
                        textStyleSpans = mergeStyles(fullStyles(left) + fullStyles(right).map { it.copy(start = it.start + offset, endExclusive = it.endExclusive + offset) }),
                    ))
                } else {
                    // Range replacement can split a node more than once. IDs
                    // remain stable for the first paragraph, unique thereafter.
                    val unique = if (node is JournalDocumentNode.TextNode && !usedIds.add(node.block.id)) {
                        node.copy(block = node.block.copy(id = newTextId().also(usedIds::add)))
                    } else node
                    result += unique
                }
            }
            nodes.forEachIndexed { nodeIndex, node ->
                if (node is JournalDocumentNode.AttachmentNode) {
                    append(node)
                } else {
                    val text = node.block.text.orEmpty()
                    if (text.isEmpty()) {
                        if (nodeIndex == nodes.lastIndex && (result.isEmpty() || result.last().let {
                            it is JournalDocumentNode.TextNode && it.block.text.orEmpty().endsWith('\n')
                        })) append(node)
                    } else {
                        var start = 0
                        var paragraphIndex = 0
                        while (start < text.length) {
                            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it + 1 }
                            val block = node.block.copy(
                                id = if (paragraphIndex == 0) node.block.id else newTextId(),
                                text = text.substring(start, end),
                                isChecked = paragraphIndex == 0 && node.block.isChecked,
                                textStyleSpans = node.block.textStyleSpans.mapNotNull { span ->
                                    val s = maxOf(start, span.start); val e = minOf(end, span.endExclusive)
                                    if (s >= e) null else span.copy(start = s - start, endExclusive = e - start)
                                },
                            )
                            append(JournalDocumentNode.TextNode(block))
                            start = end
                            paragraphIndex++
                        }
                    }
                }
            }
            val tail = result.lastOrNull() as? JournalDocumentNode.TextNode
            if (tail != null && tail.block.text.orEmpty().endsWith('\n')) {
                result += JournalDocumentNode.TextNode(tail.block.copy(
                    id = newTextId(), text = "", textStyleSpans = emptyList(), isChecked = false, type = JournalBlockType.TEXT_LINE,
                ))
            }
            return JournalDocumentBuffer(result)
        }

        private fun newTextId() = UUID.randomUUID().toString()
        private fun emptyTextBlock() = JournalBlock(newTextId(), "", 0, JournalBlockType.TEXT_LINE, text = "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

        private fun effectiveStyle(block: JournalBlock, offset: Int): JournalTextStyleSpan {
            val span = block.textStyleSpans.lastOrNull { offset >= it.start && offset < it.endExclusive }
                ?: JournalTextStyleSpan(0, block.text.orEmpty().length)
            return span.copy(color = span.color ?: block.textColor, textSize = span.textSize ?: block.textSize)
        }

        private fun fullStyles(block: JournalBlock): List<JournalTextStyleSpan> {
            val length = block.text.orEmpty().length
            val boundaries = (listOf(0, length) + block.textStyleSpans.flatMap { listOf(it.start, it.endExclusive) })
                .filter { it in 0..length }.distinct().sorted()
            return boundaries.zipWithNext().map { (s, e) -> effectiveStyle(block, s).copy(start = s, endExclusive = e) }
        }

        private fun mergeStyles(spans: List<JournalTextStyleSpan>): List<JournalTextStyleSpan> {
            val result = mutableListOf<JournalTextStyleSpan>()
            spans.filter { it.start < it.endExclusive }.forEach { span ->
                val previous = result.lastOrNull()
                if (previous != null && previous.endExclusive == span.start && previous.copy(start = span.start, endExclusive = span.endExclusive) == span) {
                    result[result.lastIndex] = previous.copy(endExclusive = span.endExclusive)
                } else result += span
            }
            return result
        }
    }
}

data class DocumentToken(val attachmentId: String)
data class JournalNodeRange(val node: JournalDocumentNode, val start: Int, val endExclusive: Int)
data class JournalDocumentEdit(val document: JournalDocumentBuffer, val caret: Int)

private data class TypedListPrefix(val original: String, val next: String)

/** Strict start-of-paragraph patterns: dates, decimals, prose and embedded markers remain literal. */
private fun journalTypedListPrefix(text: String): TypedListPrefix? {
    val match = Regex("^([ \t]{0,8})([0-9]{1,8}|[A-Za-z]|[•◦‣*+\\-])([.)]?)([ \t]+)").find(text) ?: return null
    val indent = match.groupValues[1]
    val marker = match.groupValues[2]
    val punctuation = match.groupValues[3]
    val next = when {
        marker.all(Char::isDigit) && punctuation in listOf(".", ")") -> (marker.toLong() + 1).toString() + punctuation
        marker.length == 1 && marker[0].isLetter() && punctuation in listOf(".", ")") -> {
            val letter = marker[0]
            if (letter == 'Z') "AA$punctuation" else if (letter == 'z') "aa$punctuation" else "${letter + 1}$punctuation"
        }
        marker in listOf("•", "◦", "‣", "*", "+", "-") && punctuation.isEmpty() -> marker
        else -> return null
    }
    return TypedListPrefix(match.value, indent + next + " ")
}

sealed interface JournalDocumentNode {
    val block: JournalBlock
    data class TextNode(override val block: JournalBlock) : JournalDocumentNode
    data class AttachmentNode(override val block: JournalBlock) : JournalDocumentNode
}
