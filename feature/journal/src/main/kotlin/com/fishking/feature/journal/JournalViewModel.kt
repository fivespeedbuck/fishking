package com.fishking.feature.journal

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fishking.core.media.ImportedJournalMedia
import com.fishking.core.media.JournalAudioRecorder
import com.fishking.core.media.JournalMediaStore
import com.fishking.core.location.JournalLocationProvider
import com.fishking.core.model.DailyReview
import com.fishking.core.model.JournalBlockDraft
import com.fishking.core.model.JournalBlockType
import com.fishking.core.model.JournalDocument
import com.fishking.core.model.JournalMediaAsset
import com.fishking.core.model.JournalTextSize
import com.fishking.core.model.JournalTextAlignment
import com.fishking.core.model.JournalListStyle
import com.fishking.core.model.JournalTextStyleSpan
import com.fishking.core.model.JournalDocumentBuffer
import com.fishking.core.model.JournalDocumentNode
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.usecase.DailyReviewRepository
import com.fishking.core.usecase.JournalRepository
import com.fishking.core.usecase.LifeRepository
import java.time.LocalDate
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.Locale
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface JournalEditorItem {
    val id: String?
    val editorKey: String

    data class Text(
        override val id: String? = null,
        val value: TextFieldValue = TextFieldValue(""),
        val color: Long? = null,
        val textSize: JournalTextSize = JournalTextSize.BODY,
        val styleSpans: List<JournalTextStyleSpan> = emptyList(),
        val isTitle: Boolean = false,
        override val editorKey: String = UUID.randomUUID().toString(),
        val textAlignment: JournalTextAlignment = JournalTextAlignment.LEFT,
        val listStyle: JournalListStyle = JournalListStyle.NONE,
        val isChecked: Boolean = false,
    ) : JournalEditorItem {
        val text: String get() = value.text
    }

    data class Media(
        override val id: String? = null,
        val type: JournalBlockType,
        val assets: List<JournalMediaAsset>,
        override val editorKey: String = UUID.randomUUID().toString(),
    ) : JournalEditorItem

    data class Component(
        override val id: String? = null,
        val type: JournalBlockType,
        override val editorKey: String = UUID.randomUUID().toString(),
    ) : JournalEditorItem
}

data class JournalFocusRequest(val index: Int, val token: Long)

data class JournalUndoNotice(val token: Long, val message: String)

enum class JournalInlineStyle { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, HIGHLIGHT }

private data class DeletedEditorContent(
    val date: LocalDate,
    val index: Int,
    val originalItem: JournalEditorItem,
    val previousKeys: List<String> = emptyList(),
    val followingKeys: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val journalRepository: JournalRepository,
    private val mediaStore: JournalMediaStore,
    private val audioRecorder: JournalAudioRecorder,
    private val locationProvider: JournalLocationProvider,
    dailyReviewRepository: DailyReviewRepository,
    lifeRepository: LifeRepository,
) : ViewModel() {
    val readError = MutableStateFlow<String?>(null)
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val documentRetryToken = MutableStateFlow(0L)
    val document: StateFlow<JournalDocument?> = combine(selectedDate, documentRetryToken) { date, _ -> date }
        .flatMapLatest { date ->
            date?.let {
                journalDocumentReadFlow(
                    readDocument = { journalRepository.observeDocument(date) },
                    onFailure = { if (selectedDate.value == date) readError.value = JOURNAL_READ_FAILURE_MESSAGE },
                )
            } ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val review: StateFlow<DailyReview?> = selectedDate
        .flatMapLatest { date -> date?.let(dailyReviewRepository::observe) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val goals: StateFlow<List<LifeGoalWithEvents>> = lifeRepository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items = MutableStateFlow<List<JournalEditorItem>>(listOf(JournalEditorItem.Text()))
    val loading = MutableStateFlow(false)
    private var loadedDate: LocalDate? = null
    private val editorSnapshots = mutableMapOf<LocalDate, List<JournalEditorItem>>()
    val selectedIndex = MutableStateFlow(0)
    val documentSelection = MutableStateFlow(TextRange.Zero)
    private var documentEditing = false
    val focusRequest = MutableStateFlow(JournalFocusRequest(index = 0, token = 0L))
    val undoNotice = MutableStateFlow<JournalUndoNotice?>(null)
    val locationEditorExpanded = MutableStateFlow(false)
    val locationDraft = MutableStateFlow("")
    val locating = MutableStateFlow(false)
    val importingMedia = MutableStateFlow(false)
    val mediaImportError = MutableStateFlow<String?>(null)
    val tagSaveError = MutableStateFlow<String?>(null)
    val isRecording = MutableStateFlow(false)
    val recordingElapsedMillis = MutableStateFlow(0L)
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var locationJob: Job? = null
    private val deletionJobs = mutableMapOf<Long, Job>()
    private var pendingUndo: Pair<Long, DeletedEditorContent>? = null
    private var recordingStartedAtMillis = 0L
    private val saveMutex = Mutex()
    private val latestSaveRevision = mutableMapOf<LocalDate, Long>()
    private var nextSaveRevision = 0L
    private var nextFocusToken = 0L
    private var nextUndoToken = 0L
    private val editUndoStack = ArrayDeque<List<JournalEditorItem>>()
    private val editRedoStack = ArrayDeque<List<JournalEditorItem>>()
    private val undoSelections = ArrayDeque<TextRange>()
    private val redoSelections = ArrayDeque<TextRange>()
    private var applyingEditHistory = false
    private val pendingMetadataRemovals = mutableMapOf<LocalDate, MutableMap<JournalBlockType, Long>>()
    private var nextMetadataRevision = 0L
    private val metadataGenerations = mutableMapOf<Pair<LocalDate, JournalBlockType>, Long>()

    fun isReady(date: LocalDate): Boolean = loadedDate == date

    fun setDate(date: LocalDate) = loadDate(date)

    fun retryRead() {
        val date = selectedDate.value ?: return
        readError.value = null
        if (loadedDate == date) {
            // Reconnect observation without replacing a valid in-memory draft.
            documentRetryToken.value++
        } else {
            loadDate(date, forceReload = true)
        }
    }

    private fun loadDate(date: LocalDate, forceReload: Boolean = false) {
        if (selectedDate.value == date && !forceReload) return
        cancelRecording()
        cancelLocationLookup()
        persistImmediately()
        loadedDate = null
        loading.value = true
        items.value = emptyList()
        pendingUndo = null
        undoNotice.value = null
        editUndoStack.clear()
        editRedoStack.clear()
        undoSelections.clear()
        redoSelections.clear()
        documentSelection.value = TextRange.Zero
        documentEditing = false
        tagSaveError.value = null
        readError.value = null
        selectedDate.value = date
        if (forceReload) documentRetryToken.value++
        selectedIndex.value = 0
        focusRequest.value = JournalFocusRequest(0, 0L)
        locationEditorExpanded.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
            val value = journalRepository.observeDocument(date).first()
            val loaded = value?.blocks.orEmpty().map { content ->
                if (content.block.type == JournalBlockType.TEXT_LINE || content.block.type == JournalBlockType.TITLE) {
                    JournalEditorItem.Text(
                        id = content.block.id,
                        isTitle = content.block.type == JournalBlockType.TITLE,
                        value = TextFieldValue(content.block.text.orEmpty()),
                        color = content.block.textColor,
                        textSize = content.block.textSize,
                        styleSpans = content.block.textStyleSpans,
                        textAlignment = content.block.textAlignment,
                        listStyle = content.block.listStyle,
                        isChecked = content.block.isChecked,
                    )
                } else if (content.block.type in JOURNAL_COMPONENT_TYPES) {
                    JournalEditorItem.Component(content.block.id, content.block.type)
                } else {
                    val resolvedType = if (
                        content.block.type == JournalBlockType.IMAGE && content.media.size == 1 &&
                        content.media.single().isAnimatedImage()
                    ) JournalBlockType.GIF else content.block.type
                    JournalEditorItem.Media(content.block.id, resolvedType, content.media)
                }
            }
            if (selectedDate.value != date) return@launch
            val restored = (editorSnapshots[date] ?: loaded).toMutableList()
            // Older entries already own real metadata. Give those facts a
            // body position on first edit without duplicating their values.
            fun restoreComponent(type: JournalBlockType, present: Boolean) {
                if (present && pendingMetadataRemovals[date]?.containsKey(type) != true &&
                    restored.none { it is JournalEditorItem.Component && it.type == type }) restored += JournalEditorItem.Component(type = type)
            }
            restoreComponent(JournalBlockType.LOCATION, !value?.entry?.locationName.isNullOrBlank())
            restoreComponent(JournalBlockType.LINKS, value?.linkedGoalIds.orEmpty().isNotEmpty() || value?.linkedTodoIds.orEmpty().isNotEmpty())
            restoreComponent(JournalBlockType.TAGS, value?.tags.orEmpty().isNotEmpty())
            // The native canvas owns canonical paragraph nodes immediately.
            // No hidden TITLE or synthetic attachment TextField anchors survive.
            items.value = journalItemsFromDocumentBuffer(journalItemsToDocumentBuffer(restored), restored)
            loadedDate = date
            loading.value = false
            locationDraft.value = value?.entry?.locationName.orEmpty()
            cleanupOrphans()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (selectedDate.value == date) {
                    loading.value = false
                    readError.value = JOURNAL_READ_FAILURE_MESSAGE
                }
            }
        }
    }

    fun selectItem(index: Int) {
        if (items.value.isEmpty()) return
        documentEditing = false
        selectedIndex.value = index.coerceIn(items.value.indices)
    }

    fun clearFocusRequest() { focusRequest.value = JournalFocusRequest(0, 0L) }

    fun updateTitle(value: TextFieldValue) {
        if (loadedDate == null) return
        val index = items.value.indexOfFirst { it is JournalEditorItem.Text && it.isTitle }
        if (index >= 0) updateText(index, value)
        else if (value.text.isNotBlank()) {
            recordEdit(items.value)
            items.value = listOf(JournalEditorItem.Text(value = value, textSize = JournalTextSize.TITLE, isTitle = true)) + items.value
            selectedIndex.value = 0
            clearFocusRequest()
            scheduleSave()
        }
    }

    fun updateText(index: Int, value: TextFieldValue) {
        val current = items.value.toMutableList()
        val item = current.getOrNull(index) as? JournalEditorItem.Text ?: return
        val safeSelection = TextRange(
            value.selection.start.coerceIn(0, value.text.length),
            value.selection.end.coerceIn(0, value.text.length),
        )
        val safeValue = value.copy(selection = safeSelection)
        val remappedStyles = remapStyleSpans(item.text, item.styleSpans, safeValue.text)
        recordEdit(current)
        if (item.listStyle != JournalListStyle.NONE && '\n' in safeValue.text) {
            val lines = if (item.text.isEmpty() && safeValue.text == "\n") {
                listOf(item.copy(value = TextFieldValue(""), listStyle = JournalListStyle.NONE, isChecked = false))
            } else splitJournalListParagraph(item.copy(value = safeValue, styleSpans = remappedStyles))
            current.removeAt(index)
            current.addAll(index, lines)
            items.value = current
            val lineOffset = safeValue.text.take(safeValue.selection.end).count { it == '\n' }.coerceAtMost(lines.lastIndex)
            selectedIndex.value = index + lineOffset
            scheduleSave()
            requestEditorFocus(selectedIndex.value)
            return
        }
        current[index] = item.copy(value = safeValue, styleSpans = remappedStyles)
        items.value = current
        selectedIndex.value = index
        scheduleSave()
    }

    /** The native field reports selection separately: moving a handle is not an edit or autosave. */
    fun selectDocumentRange(start: Int, end: Int) {
        documentEditing = true
        val buffer = journalItemsToDocumentBuffer(items.value)
        documentSelection.value = TextRange(start.coerceIn(0, buffer.length), end.coerceIn(0, buffer.length))
        val range = buffer.ranges().lastOrNull { it.start <= minOf(start, end) }
        selectedIndex.value = items.value.indexOfFirst { it.editorKey == range?.node?.block?.id }.coerceAtLeast(0)
    }

    fun updateDocumentBuffer(buffer: JournalDocumentBuffer, selectionStart: Int, selectionEnd: Int) {
        if (loadedDate == null) return
        documentEditing = true
        val current = items.value
        val rebuilt = journalItemsFromDocumentBuffer(buffer, current)
        val prior = journalItemsToDocumentBuffer(current)
        if (buffer != prior) recordEdit(current)
        val removedMetadata = registerRemovedMetadata(current, rebuilt)
        items.value = rebuilt
        selectDocumentRange(selectionStart, selectionEnd)
        if (removedMetadata) loadedDate?.let { queueSnapshotSave(it, rebuilt, force = true) }
        else if (buffer != prior) scheduleSave()
    }

    private fun documentStyleRange(): IntRange {
        val buffer = journalItemsToDocumentBuffer(items.value)
        val selection = documentSelection.value
        return if (!selection.collapsed) selection.min until selection.max else buffer.paragraphRange(selection.min)
    }

    private fun styleDocument(transform: (JournalTextStyleSpan) -> JournalTextStyleSpan) {
        val buffer = journalItemsToDocumentBuffer(items.value)
        val range = documentStyleRange()
        val selection = documentSelection.value
        updateDocumentBuffer(buffer.styleText(range.first, range.last + 1, transform), selection.start, selection.end)
    }

    private fun styleDocumentParagraphs(transform: (com.fishking.core.model.JournalBlock) -> com.fishking.core.model.JournalBlock) {
        val selection = documentSelection.value
        val buffer = journalItemsToDocumentBuffer(items.value)
        updateDocumentBuffer(buffer.styleParagraphs(selection.min, selection.max, transform), selection.start, selection.end)
    }

    fun addLine() {
        if (loadedDate == null) return
        val current = items.value.toMutableList()
        recordEdit(current)
        val index = (selectedIndex.value + 1).coerceIn(0, current.size)
        current.add(index, JournalEditorItem.Text())
        items.value = current
        selectedIndex.value = index
        scheduleSave()
        requestEditorFocus(index)
    }

    fun appendEmoji(emoji: String) {
        val pair = selectedText() ?: return
        val (index, line) = pair
        val selection = line.value.selection
        val start = selection.min.coerceIn(0, line.text.length)
        val end = selection.max.coerceIn(start, line.text.length)
        val updated = line.text.replaceRange(start, end, emoji)
        updateText(
            index,
            line.value.copy(
                text = updated,
                selection = TextRange(start + emoji.length),
                composition = null,
            ),
        )
        requestEditorFocus(index)
    }

    fun setTextColor(color: Long?) {
        if (documentEditing) {
            styleDocument { it.copy(color = color ?: 0xFF282622L) }
            return
        }
        val (index, line) = selectedText() ?: return
        val current = items.value.toMutableList()
        recordEdit(current)
        val range = line.styleTargetRange()
        current[index] = if (line.text.isNotEmpty() && range.first == 0 && range.last + 1 == line.text.length) {
            line.copy(
                color = color,
                styleSpans = line.styleSpans.mapNotNull { span ->
                    span.copy(color = null).takeIf(JournalTextStyleSpan::hasVisualStyle)
                },
            )
        } else if (line.text.isEmpty()) {
            line.copy(color = color)
        } else {
            line.copy(
                styleSpans = applyTextStyle(
                    text = line.text,
                    spans = line.styleSpans,
                    target = range,
                    applyColor = true,
                    color = color,
                ),
            )
        }
        items.value = current
        scheduleSave()
        requestEditorFocus(index)
    }

    fun setTextAlignment(alignment: JournalTextAlignment) {
        if (documentEditing) {
            styleDocumentParagraphs { it.copy(textAlignment = alignment) }
            return
        }
        val (index, text) = selectedText() ?: return
        val current = items.value.toMutableList()
        recordEdit(current)
        current[index] = text.copy(textAlignment = alignment)
        items.value = current
        scheduleSave()
    }

    fun setListStyle(style: JournalListStyle) {
        if (documentEditing) {
            styleDocumentParagraphs { it.copy(listStyle = style, isChecked = it.isChecked && style == JournalListStyle.CHECKLIST) }
            return
        }
        val (index, text) = selectedText() ?: return
        if (text.isTitle) return
        val current = items.value.toMutableList()
        recordEdit(current)
        val updated = text.copy(listStyle = style, isChecked = text.isChecked && style == JournalListStyle.CHECKLIST)
        val replacement = if (style == JournalListStyle.NONE) listOf(updated) else splitJournalListParagraph(updated)
        current.removeAt(index)
        current.addAll(index, replacement)
        items.value = current
        selectedIndex.value = index
        scheduleSave()
    }

    fun toggleChecklist(index: Int) {
        val current = items.value.toMutableList()
        val text = current.getOrNull(index) as? JournalEditorItem.Text ?: return
        if (text.listStyle != JournalListStyle.CHECKLIST) return
        recordEdit(current)
        current[index] = text.copy(isChecked = !text.isChecked)
        items.value = current
        selectedIndex.value = index
        loadedDate?.let { queueSnapshotSave(it, current, force = true) }
    }

    /** Native hit testing reports a stable paragraph key, never a stale list index. */
    fun toggleChecklist(editorKey: String) {
        val index = items.value.indexOfFirst { it.editorKey == editorKey }
        if (index >= 0) toggleChecklist(index)
    }

    fun setTextSize(size: JournalTextSize) {
        if (documentEditing) {
            styleDocument { it.copy(textSize = size) }
            return
        }
        val (index, line) = selectedText() ?: return
        val current = items.value.toMutableList()
        recordEdit(current)
        val range = line.styleTargetRange()
        current[index] = if (line.text.isNotEmpty() && range.first == 0 && range.last + 1 == line.text.length) {
            line.copy(
                textSize = size,
                styleSpans = line.styleSpans.mapNotNull { span ->
                    span.copy(textSize = null).takeIf(JournalTextStyleSpan::hasVisualStyle)
                },
            )
        } else if (line.text.isEmpty()) {
            line.copy(textSize = size)
        } else {
            line.copy(
                styleSpans = applyTextStyle(
                    text = line.text,
                    spans = line.styleSpans,
                    target = range,
                    applyTextSize = true,
                    textSize = size,
                ),
            )
        }
        items.value = current
        scheduleSave()
        requestEditorFocus(index)
    }

    fun toggleInlineStyle(style: JournalInlineStyle) {
        if (documentEditing) {
            val enable = !isInlineStyleActive(style)
            styleDocument { span -> when (style) {
                JournalInlineStyle.BOLD -> span.copy(bold = enable)
                JournalInlineStyle.ITALIC -> span.copy(italic = enable)
                JournalInlineStyle.UNDERLINE -> span.copy(underline = enable)
                JournalInlineStyle.STRIKETHROUGH -> span.copy(strikethrough = enable)
                JournalInlineStyle.HIGHLIGHT -> span.copy(highlightColor = if (enable) JOURNAL_HIGHLIGHT_COLOR else null)
            } }
            return
        }
        val (index, line) = selectedText() ?: return
        val target = line.styleTargetRange()
        if (target.isEmpty()) return
        val enable = !isInlineStyleActive(line, target, style)
        val current = items.value.toMutableList()
        recordEdit(current)
        current[index] = line.copy(
            styleSpans = applyTextStyle(
                text = line.text,
                spans = line.styleSpans,
                target = target,
                applyBold = style == JournalInlineStyle.BOLD,
                bold = enable,
                applyItalic = style == JournalInlineStyle.ITALIC,
                italic = enable,
                applyUnderline = style == JournalInlineStyle.UNDERLINE,
                underline = enable,
                applyStrikethrough = style == JournalInlineStyle.STRIKETHROUGH,
                strikethrough = enable,
                applyHighlight = style == JournalInlineStyle.HIGHLIGHT,
                highlightColor = if (enable) JOURNAL_HIGHLIGHT_COLOR else null,
            ),
        )
        items.value = current
        scheduleSave()
        requestEditorFocus(index)
    }

    fun isInlineStyleActive(style: JournalInlineStyle): Boolean {
        if (documentEditing) {
            val range = documentStyleRange()
            return journalItemsToDocumentBuffer(items.value).isStyleActive(range.first, range.last + 1) { span ->
                when (style) {
                    JournalInlineStyle.BOLD -> span.bold
                    JournalInlineStyle.ITALIC -> span.italic
                    JournalInlineStyle.UNDERLINE -> span.underline
                    JournalInlineStyle.STRIKETHROUGH -> span.strikethrough
                    JournalInlineStyle.HIGHLIGHT -> span.highlightColor != null
                }
            }
        }
        val (_, line) = selectedText() ?: return false
        return isInlineStyleActive(line, line.styleTargetRange(), style)
    }

    fun requestSelectedEditorFocus() {
        selectedText()?.first?.let(::requestEditorFocus)
    }

    fun canUndoEdit(): Boolean = editUndoStack.isNotEmpty()

    fun canRedoEdit(): Boolean = editRedoStack.isNotEmpty()

    fun undoEdit() {
        if (editUndoStack.isEmpty() || loadedDate == null) return
        val previous = editUndoStack.removeLast()
        editRedoStack.addLast(items.value)
        redoSelections.addLast(documentSelection.value)
        documentSelection.value = if (undoSelections.isEmpty()) TextRange.Zero else undoSelections.removeLast()
        applyingEditHistory = true
        registerRemovedMetadata(items.value, previous)
        items.value = previous
        applyingEditHistory = false
        selectedIndex.value = selectedIndex.value.coerceIn(previous.indices)
        queueSnapshotSave(loadedDate!!, previous, force = true)
        if (!documentEditing) requestSelectedEditorFocus()
    }

    fun redoEdit() {
        if (editRedoStack.isEmpty() || loadedDate == null) return
        val next = editRedoStack.removeLast()
        editUndoStack.addLast(items.value)
        undoSelections.addLast(documentSelection.value)
        documentSelection.value = if (redoSelections.isEmpty()) TextRange.Zero else redoSelections.removeLast()
        applyingEditHistory = true
        registerRemovedMetadata(items.value, next)
        items.value = next
        applyingEditHistory = false
        selectedIndex.value = selectedIndex.value.coerceIn(next.indices)
        queueSnapshotSave(loadedDate!!, next, force = true)
        if (!documentEditing) requestSelectedEditorFocus()
    }

    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty() || importingMedia.value) return
        val date = loadedDate ?: return
        viewModelScope.launch {
            importingMedia.value = true
            mediaImportError.value = null
            val registered = mutableListOf<JournalMediaAsset>()
            var inserted = false
            try {
                uris.forEach { uri ->
                    val imported = mediaStore.import(uri)
                    try {
                        registered += registerMedia(imported)
                    } catch (error: Throwable) {
                        mediaStore.delete(imported.privatePath)
                        throw error
                    }
                }
                check(selectedDate.value == date) { "Journal date changed during media import" }
                insertMediaAtCursor(registered, date)
                inserted = true
                saveLatestSnapshot(date, items.value, propagateFailure = true)
            } catch (error: Throwable) {
                // A failed import has not touched the editor. A failed DB save keeps valid imported
                // assets in the draft for retry; neither case may roll back concurrent text edits.
                if (!inserted) cleanupRegisteredMedia(registered)
                if (error is CancellationException) throw error
                android.util.Log.w("FishKingMedia", "Import failed at ${if (inserted) "save" else "read"}: ${error.javaClass.simpleName}")
                if (selectedDate.value == date) mediaImportError.value = if (inserted) {
                    "媒体已导入，但暂未保存成功，请勿关闭应用"
                } else when (error) {
                    is SecurityException -> "无法读取所选媒体，请重新从系统相册选择（读取权限失效）"
                    is java.io.FileNotFoundException -> "所选媒体尚未下载或已移动，请先在相册打开后重选"
                    else -> "媒体导入失败（${error.javaClass.simpleName}），文字和原文件未改动"
                }
            } finally {
                importingMedia.value = false
            }
        }
    }

    fun startRecording() {
        if (loadedDate == null) return
        if (isRecording.value || importingMedia.value) return
        mediaImportError.value = null
        try {
            audioRecorder.start()
            recordingStartedAtMillis = monotonicMillis()
            recordingElapsedMillis.value = 0L
            isRecording.value = true
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (isActive && isRecording.value) {
                    recordingElapsedMillis.value = monotonicMillis() - recordingStartedAtMillis
                    delay(100L)
                }
            }
        } catch (error: Throwable) {
            audioRecorder.cancel()
            mediaImportError.value = "录音启动失败，请检查麦克风是否被占用"
        }
    }

    fun stopRecording() {
        if (!isRecording.value) return
        stopRecordingClock()
        importingMedia.value = true
        val date = selectedDate.value ?: run {
            audioRecorder.cancel()
            importingMedia.value = false
            return
        }
        viewModelScope.launch {
            var imported: ImportedJournalMedia? = null
            var registered: JournalMediaAsset? = null
            var inserted = false
            try {
                imported = audioRecorder.stop()
                registered = registerMedia(imported)
                check(selectedDate.value == date) { "Journal date changed while saving audio" }
                insertMediaAtCursor(listOf(registered), date)
                inserted = true
                saveLatestSnapshot(date, items.value, propagateFailure = true)
            } catch (error: Throwable) {
                if (!inserted && registered != null) {
                    cleanupRegisteredMedia(listOf(registered))
                } else if (!inserted) {
                    imported?.let { mediaStore.delete(it.privatePath) }
                }
                if (error is CancellationException) throw error
                if (selectedDate.value == date) mediaImportError.value = if (inserted) {
                    "录音已保留，但暂未保存成功，请勿关闭应用"
                } else "录音保存失败，没有留下不完整文件"
            } finally {
                importingMedia.value = false
            }
        }
    }

    fun cancelRecording() {
        if (!isRecording.value) return
        stopRecordingClock()
        audioRecorder.cancel()
    }

    fun reportAudioPermissionDenied() {
        mediaImportError.value = "需要麦克风权限才能录音"
    }

    fun removeTextBlock(index: Int) {
        if (items.value.getOrNull(index) !is JournalEditorItem.Text) return
        removeEditorItem(index, "已移除文字")
    }

    fun removeMediaBlock(index: Int) {
        val item = items.value.getOrNull(index) as? JournalEditorItem.Media ?: return
        if (documentEditing) {
            val buffer = journalItemsToDocumentBuffer(items.value)
            val removed = buffer.ranges().firstOrNull { it.node.block.id == item.editorKey } ?: return
            val selection = documentSelection.value
            fun mapped(offset: Int) = if (offset > removed.start) offset - 1 else offset
            updateDocumentBuffer(buffer.deleteAttachment(item.editorKey), mapped(selection.start), mapped(selection.end))
            return
        }
        val message = when (item.type) {
            JournalBlockType.GIF -> "已移除动图"
            JournalBlockType.VIDEO -> "已移除视频"
            JournalBlockType.AUDIO -> "已移除录音"
            else -> "已移除图片"
        }
        removeEditorItem(index, message)
    }

    fun removeImage(index: Int, assetIndex: Int) {
        val date = selectedDate.value ?: return
        val current = items.value.toMutableList()
        val item = current.getOrNull(index) as? JournalEditorItem.Media ?: return
        if (item.type != JournalBlockType.IMAGE || assetIndex !in item.assets.indices) return
        if (documentEditing && item.assets.size == 1) {
            removeMediaBlock(index)
            return
        }
        beginUndoableDeletion(
            DeletedEditorContent(date, index, item),
            "已移除这张照片",
        )
        recordEdit(current)
        val remaining = item.assets.toMutableList().also { it.removeAt(assetIndex) }
        if (remaining.isEmpty()) current.removeAt(index) else current[index] = item.copy(assets = remaining)
        ensureEditorHasLine(current)
        clearFocusRequest()
        items.value = current
        selectedIndex.value = index.coerceAtMost(current.lastIndex).coerceAtLeast(0)
        queueSnapshotSave(date, current, force = true)
    }

    fun undoDeletion() {
        val (token, deleted) = pendingUndo ?: return
        if (selectedDate.value != deleted.date) return
        deletionJobs.remove(token)?.cancel()
        val current = items.value.toMutableList()
        val insertionIndex = deleted.followingKeys.firstNotNullOfOrNull { key ->
            current.indexOfFirst { it.editorKey == key }.takeIf { it >= 0 }
        } ?: deleted.previousKeys.firstNotNullOfOrNull { key ->
            current.indexOfFirst { it.editorKey == key }.takeIf { it >= 0 }?.plus(1)
        } ?: deleted.index.coerceIn(0, current.size)
        when (val original = deleted.originalItem) {
            is JournalEditorItem.Media -> {
                val existingIndex = original.id?.let { id -> current.indexOfFirst { it.id == id } }
                    ?: current.indexOfFirst { candidate ->
                        candidate is JournalEditorItem.Media && candidate.type == original.type &&
                            candidate.assets.any { asset -> original.assets.any { it.id == asset.id } }
                    }
                if (existingIndex >= 0) current[existingIndex] = original
                else current.add(insertionIndex, original)
            }
            is JournalEditorItem.Text -> current.add(insertionIndex, original)
            is JournalEditorItem.Component -> current.add(insertionIndex, original)
        }
        removeTransientBlankIfRedundant(current)
        items.value = current
        selectedIndex.value = current.indexOfFirst { it.editorKey == deleted.originalItem.editorKey }.coerceIn(current.indices)
        pendingUndo = null
        undoNotice.value = null
        queueSnapshotSave(deleted.date, current, force = true)
        if (originalIsText(deleted.originalItem)) requestEditorFocus(selectedIndex.value)
    }

    fun dismissUndo() {
        val token = undoNotice.value?.token ?: return
        undoNotice.value = null
        if (pendingUndo?.first == token) pendingUndo = null
    }

    private fun removeEditorItem(index: Int, message: String) {
        val date = selectedDate.value ?: return
        val current = items.value.toMutableList()
        val removed = current.getOrNull(index) ?: return
        beginUndoableDeletion(
            DeletedEditorContent(
                date = date,
                index = index,
                originalItem = removed,
            ),
            message,
        )
        recordEdit(current)
        current.removeAt(index)
        ensureEditorHasLine(current)
        clearFocusRequest()
        items.value = current
        selectedIndex.value = index.coerceAtMost(current.lastIndex).coerceAtLeast(0)
        queueSnapshotSave(date, current, force = true)
    }

    private fun beginUndoableDeletion(deleted: DeletedEditorContent, message: String) {
        pendingUndo?.first?.let { previousToken ->
            deletionJobs.remove(previousToken)?.cancel()
            viewModelScope.launch { cleanupOrphans() }
        }
        val token = ++nextUndoToken
        pendingUndo = token to deleted.copy(
            previousKeys = items.value.take(deleted.index).asReversed().map(JournalEditorItem::editorKey),
            followingKeys = items.value.drop(deleted.index + 1).map(JournalEditorItem::editorKey),
        )
        undoNotice.value = JournalUndoNotice(token, message)
        deletionJobs[token] = viewModelScope.launch {
            delay(MEDIA_UNDO_WINDOW_MILLIS)
            if (pendingUndo?.first == token) {
                pendingUndo = null
                undoNotice.value = null
            }
            cleanupOrphans()
            deletionJobs.remove(token)
        }
    }

    fun toggleGoal(goalId: String) {
        val date = selectedDate.value ?: return
        val token = beginMetadataChange(date, JournalBlockType.LINKS)
        viewModelScope.launch {
            changeMetadata(date, JournalBlockType.LINKS, token) {
                val linked = journalRepository.observeDocument(date).first()?.linkedGoalIds.orEmpty()
                if (goalId in linked) journalRepository.unlinkGoal(date, goalId) else journalRepository.linkGoal(date, goalId)
            }
        }
    }

    fun toggleTodo(todoId: String) {
        val date = selectedDate.value ?: return
        val token = beginMetadataChange(date, JournalBlockType.LINKS)
        viewModelScope.launch {
            changeMetadata(date, JournalBlockType.LINKS, token) {
                val linked = journalRepository.observeDocument(date).first()?.linkedTodoIds.orEmpty()
                if (todoId in linked) journalRepository.unlinkTodo(date, todoId) else journalRepository.linkTodo(date, todoId)
            }
        }
    }

    fun setTags(raw: String) {
        val date = selectedDate.value ?: return
        val names = parseJournalTags(raw)
        tagSaveError.value = null
        val token = beginMetadataChange(date, JournalBlockType.TAGS)
        viewModelScope.launch {
            val saved = changeMetadata(date, JournalBlockType.TAGS, token) {
                check(journalRepository.setTags(date, names)) { "TAG save failed" }
            }
            if (!saved && selectedDate.value == date) {
                tagSaveError.value = "TAG 保存失败，日记正文未受影响"
            }
        }
    }

    fun toggleLocationEditor() {
        locationEditorExpanded.value = !locationEditorExpanded.value
        if (locationEditorExpanded.value) locationDraft.value = document.value?.entry?.locationName.orEmpty()
    }

    fun updateLocation(value: String) { locationDraft.value = value.replace('\n', ' ') }

    fun saveLocation() {
        val date = selectedDate.value ?: return
        val label = locationDraft.value
        val token = beginMetadataChange(date, JournalBlockType.LOCATION)
        viewModelScope.launch {
            if (changeMetadata(date, JournalBlockType.LOCATION, token) { journalRepository.setLocation(date, label) } &&
                loadedDate == date) locationEditorExpanded.value = false
        }
    }

    fun useCurrentLocation() {
        if (locating.value) return
        val date = selectedDate.value ?: return
        val token = beginMetadataChange(date, JournalBlockType.LOCATION)
        mediaImportError.value = null
        locating.value = true
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            try {
                val location = locationProvider.currentLocation()
                if (selectedDate.value != date) return@launch
                if (changeMetadata(date, JournalBlockType.LOCATION, token) {
                    journalRepository.setLocation(date, location.label, location.latitude, location.longitude)
                }) {
                    locationDraft.value = location.label
                    locationEditorExpanded.value = false
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (selectedDate.value == date) mediaImportError.value = "暂时无法取得位置，你仍可手动输入"
            } finally {
                locating.value = false
            }
        }
    }

    fun reportLocationPermissionDenied() {
        mediaImportError.value = "未获得位置权限，你仍可手动输入"
    }

    fun cancelLocationLookup() {
        locationJob?.cancel()
        locationJob = null
        locating.value = false
    }

    private suspend fun insertMediaAtCursor(assets: List<JournalMediaAsset>, date: LocalDate) {
        val media = assets.toMediaEditorItems()
        check(loadedDate == date && selectedDate.value == date) { "Journal date changed while preparing media" }
        insertItemsAtCursor(media)
    }

    private fun insertItemsAtCursor(inserted: List<JournalEditorItem>) {
        if (documentEditing) {
            val before = items.value
            val buffer = journalItemsToDocumentBuffer(before)
            val selection = documentSelection.value
            val nodes = journalItemsToDocumentBuffer(inserted).nodes
            recordEdit(before)
            // Register the new node payloads before projecting their stable IDs.
            items.value = before + inserted
            applyingEditHistory = true
            updateDocumentBuffer(buffer.replace(selection.min, selection.max, nodes),
                selection.min + nodes.size, selection.min + nodes.size)
            applyingEditHistory = false
            clearFocusRequest()
            return
        }
        val current = items.value.toMutableList()
        ensureEditorHasLine(current)
        val selected = selectedIndex.value.coerceIn(current.indices)
        val text = current.getOrNull(selected) as? JournalEditorItem.Text
        var insertionIndex = selected + 1
        if (text != null && !text.isTitle) {
            val start = text.value.selection.min.coerceIn(0, text.text.length)
            val end = text.value.selection.max.coerceIn(start, text.text.length)
            val before = text.text.substring(0, start)
            val after = text.text.substring(end)
            current[selected] = text.copy(
                value = TextFieldValue(before, TextRange(before.length)),
                styleSpans = sliceTextStyles(text.styleSpans, 0, start),
            )
            insertionIndex = selected + 1
            current.addAll(insertionIndex, inserted)
            insertionIndex += inserted.size
            current.add(
                insertionIndex,
                JournalEditorItem.Text(
                    value = TextFieldValue(after),
                    color = text.color,
                    textSize = text.textSize,
                    styleSpans = sliceTextStyles(text.styleSpans, end, text.text.length),
                    textAlignment = text.textAlignment,
                    listStyle = text.listStyle,
                ),
            )
        } else {
            current.addAll(insertionIndex, inserted)
            insertionIndex += inserted.size
            current.add(insertionIndex, JournalEditorItem.Text())
        }
        recordEdit(items.value)
        items.value = journalItemsWithTextAnchors(current)
        selectedIndex.value = items.value.indexOfFirst { it.editorKey == inserted.last().editorKey }
            .coerceAtLeast(0)
        // Importing media or inserting a metadata component is a document
        // operation, not an instruction to reopen the keyboard. Keep the
        // insertion caret for the next explicit tap without stealing focus.
        clearFocusRequest()
    }

    private suspend fun syncComponent(date: LocalDate, type: JournalBlockType) {
        if (pendingMetadataRemovals[date]?.containsKey(type) == true) return
        val saved = journalRepository.observeDocument(date).first()
        if (loadedDate != date || selectedDate.value != date) return
        val present = when (type) {
            JournalBlockType.LOCATION -> !saved?.entry?.locationName.isNullOrBlank()
            JournalBlockType.LINKS -> saved?.linkedGoalIds.orEmpty().isNotEmpty() || saved?.linkedTodoIds.orEmpty().isNotEmpty()
            JournalBlockType.TAGS -> saved?.tags.orEmpty().isNotEmpty()
            else -> false
        }
        val existing = items.value.indexOfFirst { it is JournalEditorItem.Component && it.type == type }
        if (present && existing < 0) insertItemsAtCursor(listOf(JournalEditorItem.Component(type = type)))
        else if (!present && existing >= 0) {
            clearFocusRequest()
            if (documentEditing) {
                val key = items.value[existing].editorKey
                val buffer = journalItemsToDocumentBuffer(items.value).deleteAttachment(key)
                updateDocumentBuffer(buffer, documentSelection.value.start.coerceAtMost(buffer.length), documentSelection.value.end.coerceAtMost(buffer.length))
            } else items.value = journalItemsWithTextAnchors(items.value.filterIndexed { i, _ -> i != existing })
            selectedIndex.value = selectedIndex.value.coerceIn(items.value.indices)
        }
        // An existing component was edited in place. Do not jump to the
        // neighbouring text field or make the IME bounce back up.
        queueSnapshotSave(date, items.value, force = true)
    }

    /** Called by each component boundary; a neighbouring field is always a real text block. */
    fun focusTextBeside(index: Int, after: Boolean) {
        if (loadedDate == null) return
        val current = items.value.toMutableList()
        val adjacent = if (after) index + 1 else index - 1
        val candidate = current.getOrNull(adjacent) as? JournalEditorItem.Text
        val target = if (candidate != null && !candidate.isTitle) adjacent else {
            val insertion = (if (after) index + 1 else index).coerceIn(0, current.size)
            current.add(insertion, JournalEditorItem.Text())
            insertion
        }
        val text = current[target] as JournalEditorItem.Text
        current[target] = text.copy(value = text.value.copy(selection = TextRange(if (after) 0 else text.text.length)))
        items.value = current
        selectedIndex.value = target
        scheduleSave()
        requestEditorFocus(target)
    }

    fun removeComponent(index: Int) {
        if (loadedDate == null) return
        val component = items.value.getOrNull(index) as? JournalEditorItem.Component ?: return
        val buffer = journalItemsToDocumentBuffer(items.value)
        val offset = buffer.ranges().firstOrNull { it.node.block.id == component.editorKey }?.start ?: return
        val selection = documentSelection.value
        fun mapped(value: Int) = if (value > offset) value - 1 else value
        updateDocumentBuffer(buffer.deleteAttachment(component.editorKey), mapped(selection.start), mapped(selection.end))
    }

    private fun beginMetadataChange(date: LocalDate, type: JournalBlockType): Long =
        metadataGenerations.getOrPut(date to type) { 0L }

    private suspend fun changeMetadata(date: LocalDate, type: JournalBlockType, token: Long, mutation: suspend () -> Unit): Boolean {
        try {
            val changed = saveMutex.withLock {
                if (metadataGenerations[date to type] != token) return@withLock false
                // A deliberate re-add first commits a pending removal. No old
                // autosave can subsequently erase the newly entered metadata.
                if (pendingMetadataRemovals[date].orEmpty().isNotEmpty()) {
                    val snapshot = if (loadedDate == date) items.value else editorSnapshots[date].orEmpty()
                    saveSnapshot(date, snapshot, force = true)
                }
                mutation()
                true
            }
            if (!changed || metadataGenerations[date to type] != token) return false
            syncComponent(date, type)
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (loadedDate == date) mediaImportError.value = "组件暂未保存成功，请重试"
            return false
        }
    }

    private suspend fun registerMedia(imported: ImportedJournalMedia): JournalMediaAsset =
        journalRepository.registerMedia(
            privatePath = imported.privatePath,
            previewPath = imported.previewPath,
            mimeType = imported.mimeType,
            sizeBytes = imported.sizeBytes,
            checksum = imported.checksum,
            durationMillis = imported.durationMillis,
        )

    private suspend fun cleanupRegisteredMedia(assets: List<JournalMediaAsset>) {
        assets.forEach { asset ->
            val abandoned = journalRepository.abandonUnlinkedMedia(asset.id)
            if (abandoned && mediaStore.delete(asset.privatePath)) {
                journalRepository.confirmMediaFileDeleted(asset.id)
            }
        }
    }

    private fun stopRecordingClock() {
        isRecording.value = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    private fun recordEdit(previous: List<JournalEditorItem>) {
        if (applyingEditHistory) return
        editUndoStack.addLast(previous.toList())
        undoSelections.addLast(documentSelection.value)
        while (editUndoStack.size > 60) editUndoStack.removeFirst()
        while (undoSelections.size > 60) undoSelections.removeFirst()
        editRedoStack.clear()
        redoSelections.clear()
    }

    /**
     * Metadata deletion is deliberately not undoable. Strip only those nodes
     * from both histories, preserving unrelated text/media undo and file pins.
     */
    private fun registerRemovedMetadata(before: List<JournalEditorItem>, after: List<JournalEditorItem>): Boolean {
        val date = loadedDate ?: return false
        val keptTypes = after.filterIsInstance<JournalEditorItem.Component>().map { it.type }.toSet()
        val removed = before.filterIsInstance<JournalEditorItem.Component>().map { it.type }.toSet() - keptTypes
        if (removed.isEmpty()) return false
        val pending = pendingMetadataRemovals.getOrPut(date) { mutableMapOf() }
        removed.forEach { type ->
            val revision = ++nextMetadataRevision
            pending[type] = revision
            metadataGenerations[date to type] = revision
        }
        if (JournalBlockType.LOCATION in removed) cancelLocationLookup()
        fun stripHistory(stack: ArrayDeque<List<JournalEditorItem>>, selections: ArrayDeque<TextRange>) {
            val offsets = selections.toList()
            val rewritten = stack.toList().mapIndexed { index, snapshot ->
                val buffer = journalItemsToDocumentBuffer(snapshot)
                val ranges = buffer.ranges().filter { it.node is JournalDocumentNode.AttachmentNode && it.node.block.type in removed }
                val cleaned = JournalDocumentBuffer.of(buffer.nodes.filterNot {
                    it is JournalDocumentNode.AttachmentNode && it.block.type in removed
                })
                val selection = offsets.getOrNull(index) ?: TextRange.Zero
                fun mapped(value: Int) = (value - ranges.count { it.start < value }).coerceIn(0, cleaned.length)
                journalItemsFromDocumentBuffer(cleaned, snapshot) to TextRange(mapped(selection.start), mapped(selection.end))
            }
            stack.clear()
            selections.clear()
            rewritten.forEach { (snapshot, selection) -> stack.addLast(snapshot); selections.addLast(selection) }
        }
        stripHistory(editUndoStack, undoSelections)
        stripHistory(editRedoStack, redoSelections)
        val finalBuffer = journalItemsToDocumentBuffer(after)
        while (editUndoStack.lastOrNull()?.let { journalItemsToDocumentBuffer(it) == finalBuffer } == true) {
            editUndoStack.removeLast()
            if (undoSelections.isNotEmpty()) undoSelections.removeLast()
        }
        if (JournalBlockType.LOCATION in removed) locationDraft.value = ""
        return true
    }

    private fun selectedText(): Pair<Int, JournalEditorItem.Text>? {
        val current = items.value
        if (current.isEmpty() || loadedDate == null) return null
        val selected = selectedIndex.value.coerceIn(current.indices)
        (current[selected] as? JournalEditorItem.Text)?.let { return selected to it }
        val textIndex = current.indices.filter { current[it] is JournalEditorItem.Text }
            .minByOrNull { kotlin.math.abs(it - selected) } ?: return null
        return textIndex to (current[textIndex] as JournalEditorItem.Text)
    }

    private fun requestEditorFocus(index: Int) {
        focusRequest.value = JournalFocusRequest(index, ++nextFocusToken)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val date = loadedDate ?: return
        val snapshot = items.value
        editorSnapshots[date] = snapshot
        val revision = requestSave(date)
        saveJob = viewModelScope.launch {
            delay(280)
            saveSnapshotSafely(date, snapshot, revision)
        }
    }

    fun persistImmediately() {
        saveJob?.cancel()
        val date = loadedDate ?: return
        queueSnapshotSave(date, items.value)
    }

    // Allocate the revision synchronously with the UI edit, not when its coroutine eventually runs.
    private fun queueSnapshotSave(date: LocalDate, snapshot: List<JournalEditorItem>, force: Boolean = false) {
        editorSnapshots[date] = snapshot
        val revision = requestSave(date)
        viewModelScope.launch { saveSnapshotSafely(date, snapshot, revision, force) }
    }

    private suspend fun saveLatestSnapshot(
        date: LocalDate,
        snapshot: List<JournalEditorItem>,
        force: Boolean = false,
        propagateFailure: Boolean = false,
    ) {
        editorSnapshots[date] = snapshot
        val revision = requestSave(date)
        if (propagateFailure) saveSnapshotSerially(date, snapshot, revision, force)
        else saveSnapshotSafely(date, snapshot, revision, force)
    }

    private suspend fun saveSnapshotSafely(date: LocalDate, snapshot: List<JournalEditorItem>, revision: Long, force: Boolean = false) {
        try {
            saveSnapshotSerially(date, snapshot, revision, force)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (selectedDate.value == date) mediaImportError.value = "暂未保存成功，文字仍保留在编辑器，请勿关闭应用"
        }
    }

    private fun requestSave(date: LocalDate): Long = (++nextSaveRevision).also { revision ->
        latestSaveRevision[date] = revision
    }

    private suspend fun saveSnapshotSerially(
        date: LocalDate,
        snapshot: List<JournalEditorItem>,
        revision: Long,
        force: Boolean = false,
    ) {
        saveMutex.withLock {
            if (latestSaveRevision[date] != revision) return
            saveSnapshot(date, snapshot, force)
        }
    }

    private suspend fun saveSnapshot(date: LocalDate, snapshot: List<JournalEditorItem>, force: Boolean = false) {
        // Cursor gutters are editor state, not persistent blank paragraphs.
        // Whitespace/newlines entered by the user still count as real text.
        val meaningful = snapshot.filterNot { it is JournalEditorItem.Text && it.isEmptyCursorAnchor() }
        if (meaningful.isEmpty() && snapshot.none { it.id != null } && !force) return
        val removals = pendingMetadataRemovals[date]?.toMap().orEmpty()
        val drafts = meaningful.map(JournalEditorItem::toDraft)
        val ids = if (removals.isEmpty()) journalRepository.saveBlocks(date, drafts)
            else journalRepository.saveBlocksRemovingComponents(date, drafts, removals.keys)
        removals.forEach { (type, revision) ->
            if (pendingMetadataRemovals[date]?.get(type) == revision) pendingMetadataRemovals[date]?.remove(type)
        }
        val idsByKey = meaningful.mapIndexed { index, item -> item.editorKey to ids[index] }.toMap()
        val savedSnapshot = snapshot.map { item -> item.withId(idsByKey[item.editorKey]) }
        if (editorSnapshots[date] == snapshot) editorSnapshots[date] = savedSnapshot
        if (selectedDate.value == date && items.value == snapshot) {
            items.value = savedSnapshot
        }
    }

    private suspend fun cleanupOrphans() {
        journalRepository.pendingMediaCleanup().forEach { asset ->
            val draftUsesAsset = editorSnapshots.values.asSequence().flatten().any { item ->
                item is JournalEditorItem.Media && item.assets.any { it.id == asset.id }
            }
            val undoItem = pendingUndo?.second?.originalItem as? JournalEditorItem.Media
            val historyUsesAsset = (editUndoStack.asSequence() + editRedoStack.asSequence()).flatten().any { item ->
                item is JournalEditorItem.Media && item.assets.any { it.id == asset.id }
            }
            if (draftUsesAsset || historyUsesAsset || undoItem?.assets?.any { it.id == asset.id } == true) return@forEach
            val fileAlreadyMissing = withContext(Dispatchers.IO) { !File(asset.privatePath).exists() }
            if (fileAlreadyMissing || mediaStore.delete(asset.privatePath)) {
                journalRepository.confirmMediaFileDeleted(asset.id)
            }
        }
    }

    override fun onCleared() {
        recordingTimerJob?.cancel()
        locationJob?.cancel()
        deletionJobs.values.forEach(Job::cancel)
        audioRecorder.cancel()
        super.onCleared()
    }

    companion object {
        private const val JOURNAL_HIGHLIGHT_COLOR = 0x66FFE066L
        fun factory(
            journalRepository: JournalRepository,
            mediaStore: JournalMediaStore,
            audioRecorder: JournalAudioRecorder,
            locationProvider: JournalLocationProvider,
            dailyReviewRepository: DailyReviewRepository,
            lifeRepository: LifeRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JournalViewModel(
                    journalRepository,
                    mediaStore,
                    audioRecorder,
                    locationProvider,
                    dailyReviewRepository,
                    lifeRepository,
                )
            }
        }


        private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
        private const val MEDIA_UNDO_WINDOW_MILLIS = 6_000L
    }
}

private fun isInlineStyleActive(
    line: JournalEditorItem.Text,
    target: IntRange,
    style: JournalInlineStyle,
): Boolean {
    if (target.isEmpty()) return false
    return target.all { offset ->
        val span = line.styleSpans.lastOrNull { offset >= it.start && offset < it.endExclusive }
        when (style) {
            JournalInlineStyle.BOLD -> span?.bold == true
            JournalInlineStyle.ITALIC -> span?.italic == true
            JournalInlineStyle.UNDERLINE -> span?.underline == true
            JournalInlineStyle.STRIKETHROUGH -> span?.strikethrough == true
            JournalInlineStyle.HIGHLIGHT -> span?.highlightColor != null
        }
    }
}

internal fun parseJournalTags(raw: String): List<String> =
    raw.split(Regex("[\\s#,，]+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }

private fun JournalEditorItem.toDraft(): JournalBlockDraft = when (this) {
    is JournalEditorItem.Text -> JournalBlockDraft(
        id = id ?: editorKey,
        type = if (isTitle) JournalBlockType.TITLE else JournalBlockType.TEXT_LINE,
        text = text,
        textColor = color,
        textSize = textSize,
        textStyleSpans = styleSpans,
        textAlignment = textAlignment,
        listStyle = listStyle,
        isChecked = isChecked,
    )
    is JournalEditorItem.Media -> JournalBlockDraft(id = id ?: editorKey, type = type, mediaAssetIds = assets.map { it.id })
    is JournalEditorItem.Component -> JournalBlockDraft(id = id ?: editorKey, type = type)
}

private fun JournalEditorItem.withId(value: String?): JournalEditorItem = when (this) {
    is JournalEditorItem.Text -> copy(id = value)
    is JournalEditorItem.Media -> copy(id = value)
    is JournalEditorItem.Component -> copy(id = value)
}

private suspend fun List<JournalMediaAsset>.toMediaEditorItems(): List<JournalEditorItem.Media> {
    val result = mutableListOf<JournalEditorItem.Media>()
    val staticImages = mutableListOf<JournalMediaAsset>()
    fun flushStaticImages() {
        staticImages.chunked(3).forEach { result += JournalEditorItem.Media(type = JournalBlockType.IMAGE, assets = it) }
        staticImages.clear()
    }
    forEach { asset ->
        when {
            asset.isAnimatedImage() -> {
                flushStaticImages()
                result += JournalEditorItem.Media(type = JournalBlockType.GIF, assets = listOf(asset))
            }
            asset.mimeType.startsWith("video/", ignoreCase = true) -> {
                flushStaticImages()
                result += JournalEditorItem.Media(type = JournalBlockType.VIDEO, assets = listOf(asset))
            }
            asset.mimeType.startsWith("audio/", ignoreCase = true) -> {
                flushStaticImages()
                result += JournalEditorItem.Media(type = JournalBlockType.AUDIO, assets = listOf(asset))
            }
            else -> staticImages += asset
        }
    }
    flushStaticImages()
    return result
}

private fun JournalEditorItem.Text.styleTargetRange(): IntRange {
    if (text.isEmpty()) return IntRange.EMPTY
    val selection = value.selection
    if (!selection.collapsed) return selection.min until selection.max
    val caret = selection.end.coerceIn(0, text.length)
    val start = if (caret == 0) 0 else text.lastIndexOf('\n', caret - 1) + 1
    val end = text.indexOf('\n', caret).takeIf { it >= 0 } ?: text.length
    return start until end
}

internal fun applyTextStyle(
    text: String,
    spans: List<JournalTextStyleSpan>,
    target: IntRange,
    applyColor: Boolean = false,
    color: Long? = null,
    applyTextSize: Boolean = false,
    textSize: JournalTextSize? = null,
    applyBold: Boolean = false,
    bold: Boolean = false,
    applyItalic: Boolean = false,
    italic: Boolean = false,
    applyUnderline: Boolean = false,
    underline: Boolean = false,
    applyStrikethrough: Boolean = false,
    strikethrough: Boolean = false,
    applyHighlight: Boolean = false,
    highlightColor: Long? = null,
): List<JournalTextStyleSpan> {
    if (text.isEmpty() || target.isEmpty()) return spans
    val start = target.first.coerceIn(0, text.length)
    val end = (target.last + 1).coerceIn(start, text.length)
    if (start == end) return spans
    val boundaries = buildSet {
        add(0)
        add(text.length)
        add(start)
        add(end)
        spans.forEach {
            add(it.start.coerceIn(0, text.length))
            add(it.endExclusive.coerceIn(0, text.length))
        }
    }.sorted()
    val pieces = boundaries.zipWithNext().mapNotNull { (pieceStart, pieceEnd) ->
        if (pieceStart == pieceEnd) return@mapNotNull null
        val inherited = spans.lastOrNull { pieceStart >= it.start && pieceStart < it.endExclusive }
        val styled = if (pieceStart < end && pieceEnd > start) {
            JournalTextStyleSpan(
                pieceStart,
                pieceEnd,
                color = if (applyColor) color else inherited?.color,
                textSize = if (applyTextSize) textSize else inherited?.textSize,
                bold = if (applyBold) bold else inherited?.bold == true,
                italic = if (applyItalic) italic else inherited?.italic == true,
                underline = if (applyUnderline) underline else inherited?.underline == true,
                strikethrough = if (applyStrikethrough) strikethrough else inherited?.strikethrough == true,
                highlightColor = if (applyHighlight) highlightColor else inherited?.highlightColor,
            )
        } else {
            inherited?.copy(start = pieceStart, endExclusive = pieceEnd)
                ?: JournalTextStyleSpan(pieceStart, pieceEnd)
        }
        styled.takeIf(JournalTextStyleSpan::hasVisualStyle)
    }
    return mergeAdjacentStyles(pieces)
}

internal fun remapStyleSpans(
    oldText: String,
    oldSpans: List<JournalTextStyleSpan>,
    newText: String,
): List<JournalTextStyleSpan> {
    if (oldText == newText) return oldSpans
    var prefix = 0
    while (prefix < oldText.length && prefix < newText.length && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (
        suffix < oldText.length - prefix && suffix < newText.length - prefix &&
        oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
    ) suffix++
    val oldEnd = oldText.length - suffix
    val newEnd = newText.length - suffix
    val shift = newEnd - oldEnd
    val mapped = mutableListOf<JournalTextStyleSpan>()
    oldSpans.forEach { span ->
        if (span.start < prefix) {
            val end = minOf(span.endExclusive, prefix)
            if (span.start < end) mapped += span.copy(endExclusive = end)
        }
        if (span.endExclusive > oldEnd) {
            val start = maxOf(span.start, oldEnd) + shift
            val end = span.endExclusive + shift
            if (start < end) mapped += span.copy(start = start, endExclusive = end)
        }
    }
    if (newEnd > prefix) {
        val inherited = oldSpans.lastOrNull { prefix > it.start && prefix < it.endExclusive }
        if (inherited != null) {
            mapped += inherited.copy(start = prefix, endExclusive = newEnd)
        }
    }
    return mergeAdjacentStyles(mapped.sortedBy(JournalTextStyleSpan::start))
}

private fun sliceTextStyles(
    spans: List<JournalTextStyleSpan>,
    start: Int,
    end: Int,
): List<JournalTextStyleSpan> = spans.mapNotNull { span ->
    val clippedStart = maxOf(span.start, start)
    val clippedEnd = minOf(span.endExclusive, end)
    if (clippedStart >= clippedEnd) null else span.copy(
        start = clippedStart - start,
        endExclusive = clippedEnd - start,
    )
}

private data class SplitTextResult(
    val items: List<JournalEditorItem.Text>,
    val selectedLine: Int,
)

private fun splitTextItem(
    original: JournalEditorItem.Text,
    value: TextFieldValue,
    styles: List<JournalTextStyleSpan>,
): SplitTextResult {
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = 0
    value.text.forEachIndexed { index, char ->
        if (char == '\n') {
            ranges += start to index
            start = index + 1
        }
    }
    ranges += start to value.text.length
    val caret = value.selection.end.coerceIn(0, value.text.length)
    val selectedLine = value.text.take(caret).count { it == '\n' }.coerceAtMost(ranges.lastIndex)
    return SplitTextResult(
        items = ranges.mapIndexed { index, (lineStart, lineEnd) ->
            val text = value.text.substring(lineStart, lineEnd)
            val selection = if (index == selectedLine) {
                TextRange((caret - lineStart).coerceIn(0, text.length))
            } else {
                TextRange(text.length)
            }
            JournalEditorItem.Text(
                id = original.id.takeIf { index == 0 },
                value = TextFieldValue(text, selection),
                color = original.color,
                textSize = original.textSize,
                styleSpans = sliceTextStyles(styles, lineStart, lineEnd),
            )
        },
        selectedLine = selectedLine,
    )
}

private fun mergeAdjacentStyles(spans: List<JournalTextStyleSpan>): List<JournalTextStyleSpan> {
    if (spans.isEmpty()) return emptyList()
    val result = mutableListOf<JournalTextStyleSpan>()
    spans.sortedBy(JournalTextStyleSpan::start).forEach { span ->
        val previous = result.lastOrNull()
        if (
            previous != null && previous.endExclusive == span.start &&
            previous.sameVisualStyleAs(span)
        ) {
            result[result.lastIndex] = previous.copy(endExclusive = span.endExclusive)
        } else if (span.start < span.endExclusive && span.hasVisualStyle()) {
            result += span
        }
    }
    return result
}

private fun JournalTextStyleSpan.hasVisualStyle(): Boolean =
    color != null || textSize != null || bold || italic || underline || strikethrough || highlightColor != null

private fun JournalTextStyleSpan.sameVisualStyleAs(other: JournalTextStyleSpan): Boolean =
    color == other.color && textSize == other.textSize && bold == other.bold && italic == other.italic &&
        underline == other.underline && strikethrough == other.strikethrough && highlightColor == other.highlightColor

private suspend fun JournalMediaAsset.isAnimatedImage(): Boolean = withContext(Dispatchers.IO) {
    val file = File(privatePath)
    if (!file.isFile) return@withContext mimeType.equals("image/gif", ignoreCase = true)
    val header = ByteArray(minOf(file.length(), 128 * 1024L).toInt())
    val count = runCatching { FileInputStream(file).use { it.read(header) } }.getOrDefault(0)
    if (count < 6) return@withContext false
    val ascii = header.copyOf(count).toString(Charsets.ISO_8859_1)
    ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a") ||
        (ascii.startsWith("RIFF") && ascii.length >= 12 && ascii.substring(8, 12) == "WEBP" &&
            (ascii.contains("ANIM") || ascii.contains("ANMF")))
}

private fun ensureEditorHasLine(items: MutableList<JournalEditorItem>) {
    val anchored = journalItemsWithTextAnchors(items)
    items.clear()
    items.addAll(anchored)
}

private fun removeTransientBlankIfRedundant(items: MutableList<JournalEditorItem>) {
    if (items.size <= 1) return
    val index = items.indexOfFirst { it is JournalEditorItem.Text && it.id == null && it.isEmptyCursorAnchor() }
    if (index >= 0 && items.getOrNull(index - 1) is JournalEditorItem.Text && items.getOrNull(index + 1) is JournalEditorItem.Text) items.removeAt(index)
    ensureEditorHasLine(items)
}

internal val JOURNAL_COMPONENT_TYPES = setOf(JournalBlockType.LOCATION, JournalBlockType.LINKS, JournalBlockType.TAGS)

internal fun JournalEditorItem.Text.isEmptyCursorAnchor(): Boolean = text.isEmpty() &&
    listStyle == JournalListStyle.NONE && textAlignment == JournalTextAlignment.LEFT && !isChecked &&
    color == null && styleSpans.isEmpty() && !isTitle && textSize == JournalTextSize.BODY

/** A list row is a real text block; Return makes another independently checkable row. */
internal fun splitJournalListParagraph(item: JournalEditorItem.Text): List<JournalEditorItem.Text> {
    var offset = 0
    return item.text.split('\n').mapIndexed { index, text ->
        val start = offset
        offset += text.length + 1
        item.copy(
            id = if (index == 0) item.id else null,
            editorKey = if (index == 0) item.editorKey else UUID.randomUUID().toString(),
            value = TextFieldValue(text, TextRange(
                (item.value.selection.start - start).coerceIn(0, text.length),
                (item.value.selection.end - start).coerceIn(0, text.length),
            )),
            styleSpans = sliceTextStyles(item.styleSpans, start, start + text.length),
            isChecked = index == 0 && item.isChecked,
        )
    }
}

internal fun journalListOrdinal(items: List<JournalEditorItem>, index: Int): Int {
    val text = items.getOrNull(index) as? JournalEditorItem.Text ?: return 1
    if (text.listStyle == JournalListStyle.NONE) return 1
    var ordinal = 1
    for (previous in index - 1 downTo 0) {
        val row = items[previous] as? JournalEditorItem.Text ?: break
        if (row.isTitle || row.listStyle != text.listStyle) break
        ordinal++
    }
    return ordinal
}

/**
 * Attachment boundaries need a writable anchor, not a persistent empty card.
 * Merge adjacent body-text runs by dropping only truly empty fields; real text,
 * styles, intentional newlines and editor keys are left untouched.
 */
internal fun journalItemsWithTextAnchors(items: List<JournalEditorItem>): List<JournalEditorItem> = buildList {
    var index = 0
    while (index < items.size) {
        val item = items[index]
        if (item is JournalEditorItem.Text && !item.isTitle) {
            val run = mutableListOf<JournalEditorItem.Text>()
            while (index < items.size) {
                val next = items[index] as? JournalEditorItem.Text ?: break
                if (next.isTitle) break
                run += next
                index++
            }
            val meaningful = run.filterNot { it.isEmptyCursorAnchor() }
            addAll(meaningful.ifEmpty { listOf(run.first()) })
            continue
        }
        if (item !is JournalEditorItem.Text) {
            val previous = lastOrNull()
            if (previous !is JournalEditorItem.Text || previous.isTitle) add(JournalEditorItem.Text())
        }
        add(item)
        index++
    }
    if (lastOrNull().let { it !is JournalEditorItem.Text || it.isTitle }) add(JournalEditorItem.Text())
}

private fun originalIsText(item: JournalEditorItem): Boolean = item is JournalEditorItem.Text
