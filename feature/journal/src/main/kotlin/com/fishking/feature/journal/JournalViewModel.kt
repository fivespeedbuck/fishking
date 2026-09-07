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
import com.fishking.core.model.JournalTextStyleSpan
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.usecase.DailyReviewRepository
import com.fishking.core.usecase.JournalRepository
import com.fishking.core.usecase.LifeRepository
import java.time.LocalDate
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.Locale
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
    ) : JournalEditorItem {
        val text: String get() = value.text
    }

    data class Media(
        override val id: String? = null,
        val type: JournalBlockType,
        val assets: List<JournalMediaAsset>,
        override val editorKey: String = UUID.randomUUID().toString(),
    ) : JournalEditorItem
}

data class JournalFocusRequest(val index: Int, val token: Long)

data class JournalUndoNotice(val token: Long, val message: String)

private data class DeletedEditorContent(
    val date: LocalDate,
    val index: Int,
    val originalItem: JournalEditorItem,
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
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    val document: StateFlow<JournalDocument?> = selectedDate
        .flatMapLatest { date -> date?.let(journalRepository::observeDocument) ?: flowOf(null) }
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

    fun isReady(date: LocalDate): Boolean = loadedDate == date

    fun setDate(date: LocalDate) {
        if (selectedDate.value == date) return
        cancelRecording()
        cancelLocationLookup()
        persistImmediately()
        loadedDate = null
        loading.value = true
        items.value = emptyList()
        pendingUndo = null
        undoNotice.value = null
        tagSaveError.value = null
        selectedDate.value = date
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
                    )
                } else {
                    val resolvedType = if (
                        content.block.type == JournalBlockType.IMAGE && content.media.size == 1 &&
                        content.media.single().isAnimatedImage()
                    ) JournalBlockType.GIF else content.block.type
                    JournalEditorItem.Media(content.block.id, resolvedType, content.media)
                }
            }
            if (selectedDate.value != date) return@launch
            items.value = editorSnapshots[date] ?: loaded.ifEmpty { listOf(JournalEditorItem.Text()) }
            loadedDate = date
            loading.value = false
            locationDraft.value = value?.entry?.locationName.orEmpty()
            cleanupOrphans()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (selectedDate.value == date) {
                    loading.value = false
                    mediaImportError.value = "日记读取失败，请切换日期后重试，原记录未改动"
                }
            }
        }
    }

    fun selectItem(index: Int) {
        if (items.value.isEmpty()) return
        selectedIndex.value = index.coerceIn(items.value.indices)
    }

    fun clearFocusRequest() { focusRequest.value = JournalFocusRequest(0, 0L) }

    fun updateTitle(value: TextFieldValue) {
        if (loadedDate == null) return
        val index = items.value.indexOfFirst { it is JournalEditorItem.Text && it.isTitle }
        if (index >= 0) updateText(index, value)
        else if (value.text.isNotBlank()) {
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
        current[index] = item.copy(value = safeValue, styleSpans = remappedStyles)
        items.value = current
        selectedIndex.value = index
        scheduleSave()
    }

    fun addLine() {
        if (loadedDate == null) return
        val current = items.value.toMutableList()
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
        val (index, line) = selectedText() ?: return
        val current = items.value.toMutableList()
        val range = line.styleTargetRange()
        current[index] = if (line.text.isNotEmpty() && range.first == 0 && range.last + 1 == line.text.length) {
            line.copy(
                color = color,
                styleSpans = line.styleSpans.mapNotNull { span ->
                    span.copy(color = null).takeIf { it.textSize != null }
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

    fun setTextSize(size: JournalTextSize) {
        val (index, line) = selectedText() ?: return
        val current = items.value.toMutableList()
        val range = line.styleTargetRange()
        current[index] = if (line.text.isNotEmpty() && range.first == 0 && range.last + 1 == line.text.length) {
            line.copy(
                textSize = size,
                styleSpans = line.styleSpans.mapNotNull { span ->
                    span.copy(textSize = null).takeIf { it.color != null }
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

    fun requestSelectedEditorFocus() {
        selectedText()?.first?.let(::requestEditorFocus)
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
        beginUndoableDeletion(
            DeletedEditorContent(date, index, item),
            "已移除这张照片",
        )
        val remaining = item.assets.toMutableList().also { it.removeAt(assetIndex) }
        if (remaining.isEmpty()) current.removeAt(index) else current[index] = item.copy(assets = remaining)
        ensureEditorHasLine(current)
        items.value = current
        selectedIndex.value = index.coerceAtMost(current.lastIndex).coerceAtLeast(0)
        queueSnapshotSave(date, current, force = true)
    }

    fun undoDeletion() {
        val (token, deleted) = pendingUndo ?: return
        if (selectedDate.value != deleted.date) return
        deletionJobs.remove(token)?.cancel()
        val current = items.value.toMutableList()
        when (val original = deleted.originalItem) {
            is JournalEditorItem.Media -> {
                val existingIndex = original.id?.let { id -> current.indexOfFirst { it.id == id } }
                    ?: current.indexOfFirst { candidate ->
                        candidate is JournalEditorItem.Media && candidate.type == original.type &&
                            candidate.assets.any { asset -> original.assets.any { it.id == asset.id } }
                    }
                if (existingIndex >= 0) current[existingIndex] = original
                else current.add(deleted.index.coerceIn(0, current.size), original)
            }
            is JournalEditorItem.Text -> current.add(deleted.index.coerceIn(0, current.size), original)
        }
        removeTransientBlankIfRedundant(current)
        items.value = current
        selectedIndex.value = deleted.index.coerceIn(current.indices)
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
        current.removeAt(index)
        ensureEditorHasLine(current)
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
        pendingUndo = token to deleted
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
        val linked = document.value?.linkedGoalIds.orEmpty()
        viewModelScope.launch {
            if (goalId in linked) journalRepository.unlinkGoal(date, goalId) else journalRepository.linkGoal(date, goalId)
        }
    }

    fun toggleTodo(todoId: String) {
        val date = selectedDate.value ?: return
        val linked = document.value?.linkedTodoIds.orEmpty()
        viewModelScope.launch {
            if (todoId in linked) journalRepository.unlinkTodo(date, todoId) else journalRepository.linkTodo(date, todoId)
        }
    }

    fun setTags(raw: String) {
        val date = selectedDate.value ?: return
        val names = parseJournalTags(raw)
        tagSaveError.value = null
        viewModelScope.launch {
            val saved = runCatching { journalRepository.setTags(date, names) }.getOrDefault(false)
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
        viewModelScope.launch {
            journalRepository.setLocation(date, locationDraft.value)
            locationEditorExpanded.value = false
        }
    }

    fun useCurrentLocation() {
        if (locating.value) return
        val date = selectedDate.value ?: return
        mediaImportError.value = null
        locating.value = true
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            try {
                val location = locationProvider.currentLocation()
                if (selectedDate.value != date) return@launch
                journalRepository.setLocation(date, location.label, location.latitude, location.longitude)
                locationDraft.value = location.label
                locationEditorExpanded.value = false
            } catch (error: Throwable) {
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
        val current = items.value.toMutableList()
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
            current.addAll(insertionIndex, media)
            insertionIndex += media.size
            if (after.isNotEmpty()) {
                current.add(
                    insertionIndex,
                    JournalEditorItem.Text(
                        value = TextFieldValue(after),
                        color = text.color,
                        textSize = text.textSize,
                        styleSpans = sliceTextStyles(text.styleSpans, end, text.text.length),
                    ),
                )
            }
        } else {
            current.addAll(insertionIndex, media)
            insertionIndex += media.size
        }
        items.value = current
        selectedIndex.value = (insertionIndex - 1).coerceAtLeast(0)
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
        val meaningful = snapshot.filterNot { it is JournalEditorItem.Text && it.text.isBlank() && snapshot.size == 1 }
        if (meaningful.isEmpty() && snapshot.none { it.id != null } && !force) return
        val ids = journalRepository.saveBlocks(date, meaningful.map(JournalEditorItem::toDraft))
        val savedSnapshot = if (meaningful.size == snapshot.size) snapshot.mapIndexed { index, item -> item.withId(ids[index]) } else snapshot
        if (editorSnapshots[date] == snapshot) editorSnapshots[date] = savedSnapshot
        if (selectedDate.value == date && items.value == snapshot && meaningful.size == snapshot.size) {
            items.value = savedSnapshot
        }
    }

    private suspend fun cleanupOrphans() {
        journalRepository.pendingMediaCleanup().forEach { asset ->
            val draftUsesAsset = editorSnapshots.values.asSequence().flatten().any { item ->
                item is JournalEditorItem.Media && item.assets.any { it.id == asset.id }
            }
            val undoItem = pendingUndo?.second?.originalItem as? JournalEditorItem.Media
            if (draftUsesAsset || undoItem?.assets?.any { it.id == asset.id } == true) return@forEach
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
    )
    is JournalEditorItem.Media -> JournalBlockDraft(id = id ?: editorKey, type = type, mediaAssetIds = assets.map { it.id })
}

private fun JournalEditorItem.withId(value: String): JournalEditorItem = when (this) {
    is JournalEditorItem.Text -> copy(id = value)
    is JournalEditorItem.Media -> copy(id = value)
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
            )
        } else {
            JournalTextStyleSpan(pieceStart, pieceEnd, inherited?.color, inherited?.textSize)
        }
        styled.takeIf { it.color != null || it.textSize != null }
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
            previous.color == span.color && previous.textSize == span.textSize
        ) {
            result[result.lastIndex] = previous.copy(endExclusive = span.endExclusive)
        } else if (span.start < span.endExclusive && (span.color != null || span.textSize != null)) {
            result += span
        }
    }
    return result
}

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
    if (items.isEmpty()) items += JournalEditorItem.Text()
}

private fun removeTransientBlankIfRedundant(items: MutableList<JournalEditorItem>) {
    if (items.size <= 1) return
    val index = items.indexOfFirst { it is JournalEditorItem.Text && it.id == null && it.text.isEmpty() }
    if (index >= 0) items.removeAt(index)
}

private fun originalIsText(item: JournalEditorItem): Boolean = item is JournalEditorItem.Text
