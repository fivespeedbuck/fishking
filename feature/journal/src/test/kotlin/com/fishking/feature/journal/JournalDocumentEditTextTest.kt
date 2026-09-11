package com.fishking.feature.journal

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class JournalDocumentEditTextTest {
    private fun block(id: String, text: String, checklist: Boolean = false) = JournalDocumentNode.TextNode(
        JournalBlock(id, "journal", 0, JournalBlockType.TEXT_LINE, text = text,
            listStyle = if (checklist) JournalListStyle.CHECKLIST else JournalListStyle.NONE,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))

    private fun editor(buffer: JournalDocumentBuffer) = DocumentEditText(ApplicationProvider.getApplicationContext<Context>()).apply {
        // TextView's resize path expects layout params just as it has when
        // mounted inside JournalDocumentHost in production.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        applyDocument(buffer, TextRange.Zero, emptyMap())
        measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
        layout(0, 0, 600, 700)
    }

    @Test fun firstInputUsesSameEditableAndVisibleBody() {
        val view = editor(JournalDocumentBuffer.of(emptyList()))
        val originalEditable = view.text
        view.text.insert(0, "第一行")
        assertSame(originalEditable, view.text)
        assertEquals("第一行", view.text.toString())
        assertEquals("第一行", view.document.renderedText)
        assertEquals(JournalBlockType.TEXT_LINE, view.document.nodes.single().block.type)
    }

    @Test fun checklistMarkerTapUsesStableKeyWithoutMovingSelectionOrOpeningKeyboard() {
        val view = editor(JournalDocumentBuffer.of(listOf(block("first", "第一项\n", true), block("second", "第二项", true))))
        view.setSelection(1)
        var clicked: String? = null
        view.onChecklistToggle = { clicked = it }
        val line = view.layout.getLineForOffset(4)
        val x = view.totalPaddingLeft + 4f
        val y = view.totalPaddingTop + (view.layout.getLineTop(line) + view.layout.getLineBottom(line)) / 2f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 30, MotionEvent.ACTION_UP, x, y, 0)
        view.onTouchEvent(down); view.onTouchEvent(up)
        down.recycle(); up.recycle()
        assertEquals("second", clicked)
        assertEquals(1, view.selectionStart)
    }

    @Test fun smartReturnIsOneDocumentCallbackAndOneEditable() {
        val view = editor(JournalDocumentBuffer.of(listOf(block("body", "1. 内容"))))
        val originalEditable = view.text
        var changes = 0
        view.onDocumentChanged = { _, _, _ -> changes++ }
        view.text.insert(view.text.length, "\n")
        assertSame(originalEditable, view.text)
        assertEquals("1. 内容\n2. ", view.text.toString())
        assertEquals(view.text.length, view.selectionStart)
        assertEquals(1, changes)
        view.text.insert(view.text.length, "\n")
        assertEquals("1. 内容\n", view.text.toString())
        assertEquals(2, changes)
    }

    @Test fun deletingObjectKeepsExactTextAndSameEditable() {
        val attachment = JournalDocumentNode.AttachmentNode(JournalBlock("media", "journal", 0,
            JournalBlockType.IMAGE, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        val view = editor(JournalDocumentBuffer.of(listOf(block("left", "前"), attachment, block("right", "后"))))
        val editable = view.text
        view.text.delete(1, 2)
        assertSame(editable, view.text)
        assertEquals("前后", view.text.toString())
        assertEquals("前后", view.document.renderedText)
        assertNull(view.document.tokenFor("media"))
    }

    @Test fun disablingDocumentInputDoesNotHideKeyboardOwnedByTagPanel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val tagInput = EditText(context)
        inputMethodManager.showSoftInput(tagInput, InputMethodManager.SHOW_IMPLICIT)
        val shadow = Shadows.shadowOf(inputMethodManager)
        assertTrue(shadow.isSoftInputVisible)

        val documentHost = JournalDocumentHost(context)
        documentHost.setDocumentKeyboardAllowed(false)

        assertFalse(documentHost.editor.showSoftInputOnFocus)
        assertTrue("Document editor must not close the TAG field's keyboard", shadow.isSoftInputVisible)
    }
}
