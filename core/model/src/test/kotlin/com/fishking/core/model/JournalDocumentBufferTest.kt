package com.fishking.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JournalDocumentBufferTest {
    private fun block(id: String, type: JournalBlockType, text: String? = null, position: Long = 0) =
        JournalBlock(
            id = id,
            journalId = "journal",
            position = position,
            type = type,
            text = text,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    @Test fun attachmentInsertionKeepsOneContinuousRenderedDocument() {
        val original = JournalDocumentBuffer.fromLegacy(listOf(block("t", JournalBlockType.TEXT_LINE, "beforeafter", 0)))
        val withAttachment = original.insertAttachment(6, block("photo", JournalBlockType.IMAGE, position = 1))

        assertEquals("before\uFFFCafter", withAttachment.renderedText)
        assertEquals("photo", withAttachment.attachmentIdAt(6))
        assertEquals("photo", withAttachment.tokenFor("photo")?.attachmentId)
        assertEquals("before", withAttachment.toLegacyBlocks()[0].text)
        assertEquals("after", withAttachment.toLegacyBlocks()[2].text)
    }

    @Test fun deletingAttachmentReconnectsTextWithoutBlankAnchor() {
        val buffer = JournalDocumentBuffer.of(
            listOf(
                JournalDocumentNode.TextNode(block("a", JournalBlockType.TEXT_LINE, "left", 0)),
                JournalDocumentNode.AttachmentNode(block("m", JournalBlockType.VIDEO, position = 1)),
                JournalDocumentNode.TextNode(block("b", JournalBlockType.TEXT_LINE, "right", 2)),
            ),
        )
        val deleted = buffer.deleteAttachment("m")

        assertEquals("leftright", deleted.renderedText)
        assertEquals(1, deleted.toLegacyBlocks().size)
        assertEquals("leftright", deleted.toLegacyBlocks().single().text)
    }

    @Test fun editTextBackspaceOverObjectRemovesTokenAndJoinsBothSides() {
        val buffer = JournalDocumentBuffer.of(
            listOf(
                JournalDocumentNode.TextNode(block("a", JournalBlockType.TEXT_LINE, "left", 0)),
                JournalDocumentNode.AttachmentNode(block("m", JournalBlockType.IMAGE, position = 1)),
                JournalDocumentNode.TextNode(block("b", JournalBlockType.TEXT_LINE, "right", 2)),
            ),
        )
        val edited = buffer.reconcile("leftright")

        assertEquals("leftright", edited.renderedText)
        assertEquals(1, edited.nodes.size)
        assertEquals("leftright", edited.toLegacyBlocks().single().text)
    }

    @Test fun replacementDoesNotExposeAttachmentIdOrPrivatePath() {
        val media = block("m", JournalBlockType.IMAGE, position = 1).copy(mediaUri = "file:///private/secret.jpg")
        val buffer = JournalDocumentBuffer.of(
            listOf(JournalDocumentNode.TextNode(block("t", JournalBlockType.TEXT_LINE, "hello", 0)), JournalDocumentNode.AttachmentNode(media)),
        )
        val replaced = buffer.replaceText(0, 5, "你好")

        assertEquals("你好\uFFFC", replaced.renderedText)
        assertEquals(null, replaced.renderedText.find { it == '/' })
        assertNotNull(replaced.tokenFor("m"))
        assertEquals("file:///private/secret.jpg", replaced.toLegacyBlocks().last().mediaUri)
    }

    @Test fun legacyRoundTripPreservesUntouchedBlocksAndStyles() {
        val styled = block("t", JournalBlockType.TEXT_LINE, "styled", 4).copy(
            textColor = 0xFF00FF00,
            textStyleSpans = listOf(JournalTextStyleSpan(0, 3, bold = true)),
        )
        val media = block("m", JournalBlockType.AUDIO, position = 7).copy(durationMillis = 12_000)
        val roundTrip = JournalDocumentBuffer.fromLegacy(listOf(styled, media)).toLegacyBlocks()

        assertEquals(listOf(styled, media), roundTrip)
    }

    @Test fun styleSelectionAcrossAttachmentMapsOnlySelectedText() {
        val buffer = JournalDocumentBuffer.of(listOf(
            JournalDocumentNode.TextNode(block("a", JournalBlockType.TEXT_LINE, "abcd")),
            JournalDocumentNode.AttachmentNode(block("m", JournalBlockType.AUDIO)),
            JournalDocumentNode.TextNode(block("b", JournalBlockType.TEXT_LINE, "efgh")),
        ))
        val styled = buffer.styleText(2, 7) { it.copy(bold = true, color = 0xFF334455) }
        assertEquals("abcd\uFFFCefgh", styled.renderedText)
        assertEquals("m", styled.tokenFor("m")?.attachmentId)
        assertEquals(false, styled.isStyleActive(0, 2) { it.bold })
        assertEquals(true, styled.isStyleActive(2, 7) { it.bold })
        assertEquals(false, styled.isStyleActive(7, 9) { it.bold })
    }

    @Test fun paragraphAlignmentDoesNotModifyOtherParagraphs() {
        val buffer = JournalDocumentBuffer.fromLegacy(listOf(block("t", JournalBlockType.TEXT_LINE, "one\ntwo\nthree")))
        val changed = buffer.styleParagraphs(5, 5) { it.copy(textAlignment = JournalTextAlignment.RIGHT) }
        assertEquals("one\ntwo\nthree", changed.renderedText)
        assertEquals(listOf(JournalTextAlignment.LEFT, JournalTextAlignment.RIGHT, JournalTextAlignment.LEFT),
            changed.nodes.map { it.block.textAlignment })
    }

    @Test fun paragraphAlignmentAppliesToEveryParagraphTouchedBySelection() {
        val buffer = JournalDocumentBuffer.fromLegacy(listOf(
            block("t", JournalBlockType.TEXT_LINE, "one\ntwo\nthree\nfour"),
        ))

        val changed = buffer.styleParagraphs(2, 12) { it.copy(textAlignment = JournalTextAlignment.CENTER) }

        assertEquals("one\ntwo\nthree\nfour", changed.renderedText)
        assertEquals(
            listOf(
                JournalTextAlignment.CENTER,
                JournalTextAlignment.CENTER,
                JournalTextAlignment.CENTER,
                JournalTextAlignment.LEFT,
            ),
            changed.nodes.map { it.block.textAlignment },
        )
    }

    @Test fun returnSplitsCheckedItemAndTypingKeepsIndependentUncheckedParagraph() {
        val original = JournalDocumentBuffer.of(listOf(JournalDocumentNode.TextNode(
            block("check", JournalBlockType.TEXT_LINE, "前后").copy(listStyle = JournalListStyle.CHECKLIST, isChecked = true),
        )))
        val split = original.replaceText(1, 1, "\n")
        assertEquals("前\n后", split.renderedText)
        assertEquals(listOf("前\n", "后"), split.nodes.map { it.block.text })
        assertEquals(listOf(true, false), split.nodes.map { it.block.isChecked })
        assertEquals("check", split.nodes.first().block.id)
        val secondId = split.nodes.last().block.id
        val typed = split.replaceText(2, 3, "新后")
        assertEquals("前\n新后", typed.renderedText)
        assertEquals(secondId, typed.nodes.last().block.id)
        assertEquals(listOf(true, false), typed.nodes.map { it.block.isChecked })
    }

    @Test fun returnAtEndKeepsEmptyNextListItemAndContinuousOrdinals() {
        listOf(JournalListStyle.NUMBERED, JournalListStyle.LETTERED, JournalListStyle.CHECKLIST).forEach { style ->
            val original = JournalDocumentBuffer.of(listOf(JournalDocumentNode.TextNode(
                block("first", JournalBlockType.TEXT_LINE, "第一项").copy(listStyle = style),
            )))
            val returned = original.replaceText(3, 3, "\n")
            assertEquals(listOf("第一项\n", ""), returned.nodes.map { it.block.text })
            val typed = returned.replaceText(4, 4, "第二项\n第三项")
            assertEquals("第一项\n第二项\n第三项", typed.renderedText)
            assertEquals(listOf(1, 2, 3), typed.nodes.map { typed.listOrdinal(it.block.id) })
            assertEquals(3, typed.nodes.map { it.block.id }.distinct().size)
        }
    }

    @Test fun removingRealParagraphBreakJoinsTextWithoutFabricatingAnotherOne() {
        val buffer = JournalDocumentBuffer.fromLegacy(listOf(block("t", JournalBlockType.TEXT_LINE, "one\ntwo")))
        val joined = buffer.replaceText(3, 4, "")
        assertEquals("onetwo", joined.renderedText)
        assertEquals(1, joined.nodes.size)
    }

    @Test fun emptyTitleIsNotAHiddenInputTargetAndExistingParagraphsDoNotRunTogether() {
        val empty = JournalDocumentBuffer.fromLegacy(listOf(
            block("title", JournalBlockType.TITLE, "", 0),
            block("body", JournalBlockType.TEXT_LINE, "", 1),
        ))
        val typed = empty.replaceText(0, 0, "新的日记")
        assertEquals(JournalBlockType.TEXT_LINE, typed.nodes.single().block.type)
        assertEquals("body", typed.nodes.single().block.id)
        assertEquals("新的日记", typed.renderedText)
        val legacy = JournalDocumentBuffer.fromLegacy(listOf(
            block("title", JournalBlockType.TITLE, "标题", 0),
            block("body", JournalBlockType.TEXT_LINE, "正文", 1),
            block("second", JournalBlockType.TEXT_LINE, "下一段", 2),
        ))
        assertEquals("标题\n正文\n下一段", legacy.renderedText)
    }

    @Test fun smartReturnContinuesExplicitTypedPrefixesWithoutHiddenListModes() {
        listOf("1. 内容" to "2. ", "A. 内容" to "B. ", "a) 内容" to "b) ",
            "• 内容" to "• ", "- 内容" to "- ", "* 内容" to "* ", "  9. 内容" to "  10. ").forEach { (line, prefix) ->
            val original = JournalDocumentBuffer.fromLegacy(listOf(block("body", JournalBlockType.TEXT_LINE, line)))
            val edit = original.editText(line.length, line.length, "\n")
            assertEquals(line + "\n" + prefix, edit.document.renderedText)
            assertEquals(edit.document.length, edit.caret)
            assertEquals(true, edit.document.nodes.all { it.block.listStyle == JournalListStyle.NONE })
            val exit = edit.document.editText(edit.caret, edit.caret, "\n")
            assertEquals(line + "\n", exit.document.renderedText)
        }
    }

    @Test fun smartReturnDoesNotRewriteOrdinaryTextOrMultilinePaste() {
        listOf("2026.09.11", "1.25 米", "A.词语", "版本 1. 内容", "内容 - 说明", "普通文字", "-20 度", "A 内容").forEach { line ->
            val original = JournalDocumentBuffer.fromLegacy(listOf(block("body", JournalBlockType.TEXT_LINE, line)))
            assertEquals(line + "\n", original.editText(line.length, line.length, "\n").document.renderedText)
        }
        val blank = JournalDocumentBuffer.of(emptyList())
        assertEquals("1. 内容\n", blank.editText(0, 0, "1. 内容\n").document.renderedText)
    }

    @Test fun structuredChecklistReturnContinuesAndEmptyReturnExitsInPlace() {
        val original = JournalDocumentBuffer.of(listOf(JournalDocumentNode.TextNode(
            block("checked", JournalBlockType.TEXT_LINE, "已完成").copy(listStyle = JournalListStyle.CHECKLIST, isChecked = true))))
        val edit = original.editText(3, 3, "\n")
        assertEquals("已完成\n", edit.document.renderedText)
        assertEquals(listOf(true, false), edit.document.nodes.map { it.block.isChecked })
        assertEquals(JournalListStyle.CHECKLIST, edit.document.nodes.last().block.listStyle)
        val exit = edit.document.editText(edit.caret, edit.caret, "\n")
        assertEquals("已完成\n", exit.document.renderedText)
        assertEquals(JournalListStyle.NONE, exit.document.nodes.last().block.listStyle)
    }
}
