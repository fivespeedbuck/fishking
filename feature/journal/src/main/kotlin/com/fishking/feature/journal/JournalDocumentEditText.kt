package com.fishking.feature.journal

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.viewinterop.AndroidView
import com.fishking.core.model.*
import java.time.Instant

/**
 * One native Editable and IME connection. Full-width object spans reserve real
 * layout rows; interactive Compose attachments are positioned in those rows.
 * No independent TextField, cursor gutter, encoded path, or synthetic newline
 * is needed around an attachment.
 */
internal class JournalDocumentHost(context: Context) : FrameLayout(context) {
    val editor = DocumentEditText(context)
    private val attachmentViews = linkedMapOf<String, ComposeView>()
    private val attachmentMetrics = mutableMapOf<String, Pair<Int, Int>>()
    private var compositionContext: CompositionContext? = null
    private var attachmentContent: (@Composable (String) -> Unit)? = null
    private var lastFocusToken = 0L
    private var boundaryDown: Pair<String, Boolean>? = null
    private var boundaryX = 0f
    private var boundaryY = 0f

    init {
        clipChildren = true
        clipToPadding = true
        addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        editor.onGeometryChanged = { positionAttachments() }
    }

    fun bind(
        buffer: JournalDocumentBuffer,
        selection: TextRange,
        keyboardAllowed: Boolean,
        focusToken: Long,
        composition: CompositionContext,
        content: @Composable (String) -> Unit,
        onChange: (JournalDocumentBuffer, Int, Int) -> Unit,
        onSelection: (Int, Int) -> Unit,
        onChecklistToggle: (String) -> Unit,
        onTextInteraction: () -> Unit,
    ) {
        compositionContext = composition
        attachmentContent = content
        editor.onDocumentChanged = onChange
        editor.onSelection = onSelection
        editor.onChecklistToggle = onChecklistToggle
        editor.onTextInteraction = onTextInteraction
        setDocumentKeyboardAllowed(keyboardAllowed)
        // This host owns only the document editor, not the window-wide IME.
        // Panels such as TAG may legitimately own the keyboard while the
        // document remains mounted and recomposes in the background. Hiding
        // the IME from every disabled bind races that panel and closes its
        // keyboard immediately after it opens. Panel transitions perform the
        // one intentional hide in JournalFullscreenEditor instead.
        val wanted = buffer.nodes.filterIsInstance<JournalDocumentNode.AttachmentNode>().map { it.block.id }.toSet()
        attachmentViews.keys.filterNot { it in wanted }.toList().forEach { id ->
            attachmentViews.remove(id)?.let { it.disposeComposition(); removeView(it) }
            attachmentMetrics.remove(id)
        }
        wanted.forEach { id ->
            if (id !in attachmentViews) {
                val child = ComposeView(context).apply {
                    setParentCompositionContext(composition)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setContent { attachmentContent?.invoke(id) }
                }
                attachmentViews[id] = child
                addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            } else {
                // Stable ComposeView identity keeps GIF/video/audio playback
                // intact when text or selection elsewhere in the journal changes.
                attachmentViews[id]?.setContent { attachmentContent?.invoke(id) }
            }
        }
        editor.applyDocument(buffer, selection, attachmentMetrics)
        if (keyboardAllowed && focusToken > 0 && focusToken != lastFocusToken) {
            lastFocusToken = focusToken
            editor.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        requestLayout()
    }

    internal fun setDocumentKeyboardAllowed(allowed: Boolean) {
        editor.showSoftInputOnFocus = allowed
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val bodyWidth = (width - editor.paddingLeft - editor.paddingRight).coerceAtLeast(1)
        attachmentViews.forEach { (id, child) ->
            child.measure(MeasureSpec.makeMeasureSpec(bodyWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            attachmentMetrics[id] = bodyWidth to (child.measuredHeight.coerceAtLeast(dp(40)) + dp(16))
        }
        editor.updateMetrics(attachmentMetrics, bodyWidth)
        editor.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        editor.layout(0, 0, right - left, bottom - top)
        positionAttachments()
    }

    private fun positionAttachments() {
        val textLayout = editor.layout ?: return
        editor.document.ranges().filter { it.node is JournalDocumentNode.AttachmentNode }.forEach { range ->
            val child = attachmentViews[range.node.block.id] ?: return@forEach
            if (range.start > editor.text.length) return@forEach
            val line = textLayout.getLineForOffset(range.start)
            val y = editor.totalPaddingTop + textLayout.getLineBaseline(line) - editor.scrollY - child.measuredHeight - dp(8)
            val x = editor.totalPaddingLeft
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            boundaryDown = boundaryAt(event.x, event.y)
            boundaryX = event.x; boundaryY = event.y
            if (boundaryDown != null) return true
        }
        boundaryDown?.let { boundary ->
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    if (kotlin.math.abs(event.x - boundaryX) > slop || kotlin.math.abs(event.y - boundaryY) > slop) {
                        boundaryDown = null
                        val down = MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_DOWN; setLocation(boundaryX, boundaryY)
                        }
                        editor.dispatchTouchEvent(down); down.recycle()
                        return editor.dispatchTouchEvent(event)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    boundaryDown = null
                    if (boundaryAt(event.x, event.y) == boundary) {
                        val range = editor.document.ranges().firstOrNull { it.node.block.id == boundary.first }
                        range?.let {
                            editor.beginExplicitInput()
                            editor.setSelection(if (boundary.second) it.endExclusive else it.start)
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> boundaryDown = null
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun boundaryAt(x: Float, y: Float): Pair<String, Boolean>? = attachmentViews.entries.firstNotNullOfOrNull { (id, view) ->
        if (x < view.left || x > view.right) null else when {
            y >= view.top - dp(8) && y < view.top -> id to false
            y >= view.bottom && y <= view.bottom + dp(8) -> id to true
            else -> null
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

internal class DocumentEditText(context: Context) : EditText(context) {
    var document = JournalDocumentBuffer.of(emptyList())
        private set
    var onDocumentChanged: ((JournalDocumentBuffer, Int, Int) -> Unit)? = null
    var onSelection: ((Int, Int) -> Unit)? = null
    var onGeometryChanged: (() -> Unit)? = null
    var onChecklistToggle: ((String) -> Unit)? = null
    var onTextInteraction: (() -> Unit)? = null
    private var suppress = false
    private var inTextChange = false
    private var pendingCorrectedCaret: Int? = null
    private var ownSpans = emptyList<Any>()
    private var metrics = emptyMap<String, Pair<Int, Int>>()
    private var contentWidth = 1
    private var checklistDownKey: String? = null
    private var checklistDownX = 0f
    private var checklistDownY = 0f
    private var normalizingSelection = false
    private var documentCallbacksReady = false

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        setTextColor(0xFF282622.toInt())
        setHintTextColor(0xFF8A8478.toInt())
        highlightColor = 0x668CBD73
        backgroundTintList = ColorStateList.valueOf(0xFF5C8254.toInt())
        textSize = 18f
        setPadding(dp(20), dp(12), dp(20), dp(30))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = true
        includeFontPadding = true
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { if (!suppress) inTextChange = true }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppress) return
                val edit = document.editText(start, start + before, s?.subSequence(start, start + count)?.toString().orEmpty())
                document = edit.document
                pendingCorrectedCaret = edit.caret.takeIf { s?.toString() != document.renderedText }
            }
            override fun afterTextChanged(s: Editable?) {
                if (suppress) return
                inTextChange = false
                pendingCorrectedCaret?.let { caret ->
                    suppress = true
                    // Smart Return changes one narrow range in the same
                    // Editable, so undo observes one logical input event.
                    replaceEditableText(document.renderedText)
                    setSelection(caret.coerceIn(0, text.length))
                    suppress = false
                }
                pendingCorrectedCaret = null
                // Reapply only our visual spans; leave Android's composing
                // region and IME spans alone, especially for Chinese input.
                decorate()
                val start = selectionStart.coerceIn(0, document.length)
                val end = selectionEnd.coerceIn(0, document.length)
                onDocumentChanged?.invoke(document, start, end)
                onGeometryChanged?.invoke()
            }
        })
        documentCallbacksReady = true
    }

    fun applyDocument(next: JournalDocumentBuffer, selection: TextRange, metrics: Map<String, Pair<Int, Int>>) {
        this.metrics = metrics.toMap()
        if (next != document || text.toString() != next.renderedText) {
            suppress = true
            document = next
            if (text.toString() != next.renderedText) {
                // External insert/delete/undo is one Editable transaction, not
                // setText(): the same native field and scroll position survive.
                replaceEditableText(next.renderedText)
            }
            decorate()
            suppress = false
        }
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        if (selectionStart != start || selectionEnd != end) {
            suppress = true
            setSelection(start, end)
            suppress = false
        }
        isCursorVisible = !selectionTouchesAttachmentLine(start, end)
    }

    private fun replaceEditableText(next: String) {
        val current = text.toString()
        var prefix = 0
        while (prefix < current.length && prefix < next.length && current[prefix] == next[prefix]) prefix++
        var suffix = 0
        while (suffix < current.length - prefix && suffix < next.length - prefix &&
            current[current.lastIndex - suffix] == next[next.lastIndex - suffix]) suffix++
        text.replace(prefix, current.length - suffix, next.substring(prefix, next.length - suffix))
    }

    fun updateMetrics(next: Map<String, Pair<Int, Int>>, width: Int) {
        if (metrics != next || contentWidth != width) {
            metrics = next.toMap()
            contentWidth = width
            decorate()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // TextView/EditText can dispatch this virtual callback from their
        // constructor, before Kotlin has initialized our document field.
        if (!documentCallbacksReady) return
        if (!suppress && !inTextChange && !normalizingSelection && selStart >= 0 && selEnd >= 0) {
            val normalized = normalizeSelectionAwayFromAttachments(selStart, selEnd)
            if (normalized.first != selStart || normalized.second != selEnd) {
                normalizingSelection = true
                setSelection(normalized.first, normalized.second)
                normalizingSelection = false
            } else {
                // ReplacementSpan reserves the full component height. Android
                // otherwise draws a component-height caret at its boundary.
                isCursorVisible = !selectionTouchesAttachmentLine(selStart, selEnd)
                onSelection?.invoke(selStart, selEnd)
            }
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onGeometryChanged?.invoke()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            checklistDownKey = checklistKeyAt(event.x, event.y)
            checklistDownX = event.x
            checklistDownY = event.y
            if (checklistDownKey != null) return true
            onTextInteraction?.invoke()
            showSoftInputOnFocus = true
        }
        checklistDownKey?.let { key ->
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    if (kotlin.math.abs(event.x - checklistDownX) > slop || kotlin.math.abs(event.y - checklistDownY) > slop) {
                        checklistDownKey = null
                        val down = MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_DOWN
                            setLocation(checklistDownX, checklistDownY)
                        }
                        super.onTouchEvent(down)
                        down.recycle()
                        return super.onTouchEvent(event)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    checklistDownKey = null
                    if (checklistKeyAt(event.x, event.y) == key) {
                        performClick()
                        onChecklistToggle?.invoke(key)
                    }
                }
                MotionEvent.ACTION_CANCEL -> checklistDownKey = null
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    fun beginExplicitInput() {
        onTextInteraction?.invoke()
        showSoftInputOnFocus = true
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun checklistKeyAt(x: Float, y: Float): String? {
        val textLayout = layout ?: return null
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        if (localX < -dp(12) || localX > dp(28) || localY < 0 || localY >= textLayout.height) return null
        val line = textLayout.getLineForVertical(localY.toInt())
        return document.ranges().firstOrNull { range ->
            range.node is JournalDocumentNode.TextNode && range.node.block.listStyle == JournalListStyle.CHECKLIST &&
                textLayout.getLineForOffset(range.start) == line
        }?.node?.block?.id
    }

    private fun decorate() {
        val editable = text ?: return
        ownSpans.forEach(editable::removeSpan)
        val spans = mutableListOf<Any>()
        fun span(value: Any, start: Int, end: Int) {
            if (start >= end || start < 0 || end > editable.length) return
            editable.setSpan(value, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spans += value
        }
        document.ranges().forEach { range ->
            val block = range.node.block
            if (range.node is JournalDocumentNode.AttachmentNode) {
                val size = metrics[block.id]
                span(DocumentAttachmentSpan(size?.first ?: contentWidth, size?.second ?: dp(80)), range.start, range.endExclusive)
            } else {
                val start = range.start; val end = range.endExclusive
                block.textColor?.let { span(ForegroundColorSpan(it.toInt()), start, end) }
                span(AbsoluteSizeSpan(sizeSp(block.textSize), true), start, end)
                block.textStyleSpans.forEach { style ->
                    val s = (start + style.start).coerceIn(start, end)
                    val e = (start + style.endExclusive).coerceIn(s, end)
                    style.color?.let { span(ForegroundColorSpan(it.toInt()), s, e) }
                    style.textSize?.let { span(AbsoluteSizeSpan(sizeSp(it), true), s, e) }
                    if (style.bold || style.italic) span(StyleSpan((if (style.bold) Typeface.BOLD else 0) or (if (style.italic) Typeface.ITALIC else 0)), s, e)
                    if (style.underline) span(UnderlineSpan(), s, e)
                    if (style.strikethrough) span(StrikethroughSpan(), s, e)
                    style.highlightColor?.let { span(BackgroundColorSpan(it.toInt()), s, e) }
                }
                // A checked checklist row keeps its stored formatting, but its
                // completed state is always legible as a muted, struck-through
                // line. The overlay is visual only, so unchecking restores the
                // original colour and inline styles without rewriting content.
                if (block.listStyle == JournalListStyle.CHECKLIST && block.isChecked) {
                    span(ForegroundColorSpan(0xFF69655F.toInt()), start, end)
                    span(StrikethroughSpan(), start, end)
                }
                val align = when (block.textAlignment) {
                    JournalTextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    JournalTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                    JournalTextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                }
                span(AlignmentSpan.Standard(align), start, end)
                if (block.listStyle != JournalListStyle.NONE) {
                    val marker = if (block.listStyle == JournalListStyle.CHECKLIST) (if (block.isChecked) "☑" else "☐")
                        else journalListMarker(block.listStyle, document.listOrdinal(block.id))
                    span(DocumentListSpan(marker, dp(28)), start, end)
                }
            }
        }
        ownSpans = spans
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val textLayout = layout ?: return
        // The trailing empty paragraph has no characters to carry a span.
        // Draw only its marker; the next real input remains in the same Editable.
        document.ranges().lastOrNull()?.takeIf {
            it.start == it.endExclusive && it.node is JournalDocumentNode.TextNode && it.node.block.listStyle != JournalListStyle.NONE
        }?.let { range ->
            val block = range.node.block
            val marker = if (block.listStyle == JournalListStyle.CHECKLIST) (if (block.isChecked) "☑" else "☐")
                else journalListMarker(block.listStyle, document.listOrdinal(block.id))
            val line = textLayout.getLineForOffset(range.start)
            canvas.drawText(marker, (totalPaddingLeft - scrollX).toFloat(),
                (totalPaddingTop + textLayout.getLineBaseline(line) - scrollY).toFloat(), paint)
        }
    }

    /** U+FFFC attachment rows are vertical waypoints, never selectable text. */
    private fun normalizeSelectionAwayFromAttachments(start: Int, end: Int): Pair<Int, Int> {
        val attachments = document.ranges()
            .filter { it.node is JournalDocumentNode.AttachmentNode }
            .map { it.start to it.endExclusive }
        if (attachments.isEmpty()) return start to end
        if (start == end) {
            val row = attachments.firstOrNull { start > it.first && start < it.second }
            return row?.first?.let { it to it } ?: (start to end)
        }
        val row = attachments.firstOrNull { start.coerceAtMost(end) < it.second && end.coerceAtLeast(start) > it.first }
            ?: return start to end
        return row.first to row.first
    }

    private fun selectionTouchesAttachmentLine(start: Int, end: Int): Boolean {
        if (start != end) return false
        val textLayout = layout ?: return false
        val caretLine = textLayout.getLineForOffset(start.coerceIn(0, text.length))
        return document.ranges().any {
            it.node is JournalDocumentNode.AttachmentNode &&
                textLayout.getLineForOffset(it.start.coerceIn(0, text.length)) == caretLine
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun sizeSp(size: JournalTextSize) = when (size) {
        JournalTextSize.SMALL -> 14
        JournalTextSize.BODY -> 18
        JournalTextSize.LARGE -> 23
        JournalTextSize.TITLE -> 29
    }
}

private class DocumentAttachmentSpan(private val width: Int, private val height: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.apply { ascent = -height; top = -height; descent = 0; bottom = 0 }
        return width.coerceAtLeast(1)
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) = Unit
}

private class DocumentListSpan(private val marker: String, private val width: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = width
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        if (first) c.drawText(marker, x.toFloat(), baseline.toFloat(), p)
    }
}

@Composable
internal fun JournalSingleDocumentEditor(
    items: List<JournalEditorItem>,
    selection: TextRange,
    modifier: Modifier = Modifier,
    requestFocusToken: Long = 0L,
    keyboardAllowed: Boolean = true,
    onDocumentChanged: (JournalDocumentBuffer, Int, Int) -> Unit,
    onSelectionChanged: (Int, Int) -> Unit,
    onChecklistToggle: (String) -> Unit,
    onTextInteraction: () -> Unit,
    attachment: @Composable (String) -> Unit,
) {
    val composition = rememberCompositionContext()
    val source = journalItemsToDocumentBuffer(items)
    AndroidView(
        modifier = modifier,
        factory = { JournalDocumentHost(it) },
        update = { host -> host.bind(source, selection, keyboardAllowed, requestFocusToken, composition, attachment, onDocumentChanged, onSelectionChanged, onChecklistToggle, onTextInteraction) },
    )
}

internal fun journalItemsToDocumentBuffer(items: List<JournalEditorItem>): JournalDocumentBuffer = JournalDocumentBuffer.fromParagraphNodes(items.mapIndexed { index, item ->
    val block = when (item) {
        is JournalEditorItem.Text -> JournalBlock(
            id = item.editorKey, journalId = "", position = index.toLong(),
            type = if (item.isTitle) JournalBlockType.TITLE else JournalBlockType.TEXT_LINE,
            text = item.text, textColor = item.color, textSize = item.textSize, textStyleSpans = item.styleSpans,
            textAlignment = item.textAlignment, listStyle = item.listStyle, isChecked = item.isChecked,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        is JournalEditorItem.Media -> JournalBlock(item.editorKey, "", index.toLong(), item.type, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        is JournalEditorItem.Component -> JournalBlock(item.editorKey, "", index.toLong(), item.type, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    }
    if (item is JournalEditorItem.Text) JournalDocumentNode.TextNode(block) else JournalDocumentNode.AttachmentNode(block)
})

internal fun journalItemsFromDocumentBuffer(buffer: JournalDocumentBuffer, knownItems: List<JournalEditorItem>): List<JournalEditorItem> {
    val byKey = knownItems.associateBy(JournalEditorItem::editorKey)
    return buffer.nodes.mapNotNull { node ->
        val block = node.block
        when (node) {
            is JournalDocumentNode.TextNode -> JournalEditorItem.Text(
                id = byKey[block.id]?.id, editorKey = block.id,
                value = androidx.compose.ui.text.input.TextFieldValue(block.text.orEmpty()),
                color = block.textColor, textSize = block.textSize, styleSpans = block.textStyleSpans,
                isTitle = block.type == JournalBlockType.TITLE || byKey[block.id]?.let { it is JournalEditorItem.Text && it.isTitle } == true,
                textAlignment = block.textAlignment, listStyle = block.listStyle, isChecked = block.isChecked,
            )
            is JournalDocumentNode.AttachmentNode -> byKey[block.id]
        }
    }.ifEmpty { listOf(JournalEditorItem.Text()) }
}
