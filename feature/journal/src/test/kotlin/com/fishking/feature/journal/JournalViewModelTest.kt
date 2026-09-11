package com.fishking.feature.journal

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.fishking.core.media.ImportedJournalMedia
import com.fishking.core.media.JournalAudioRecorder
import com.fishking.core.media.JournalMediaStore
import com.fishking.core.location.JournalLocation
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
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.usecase.DailyReviewRepository
import com.fishking.core.usecase.JournalRepository
import com.fishking.core.usecase.LifeRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun failedInitialDocumentReadRetriesBothCollectorsWithoutWritingAnEmptyDocument() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false).apply { failDocumentRead = true }
        val viewModel = newViewModel(repository)
        val date = LocalDate.of(2026, 9, 11)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.document.collect {} }
        viewModel.setDate(date)
        runCurrent()
        assertEquals(JOURNAL_READ_FAILURE_MESSAGE, viewModel.readError.value)
        assertEquals(false, viewModel.isReady(date))
        assertTrue(repository.savedDates.isEmpty())
        repository.failDocumentRead = false
        viewModel.retryRead()
        runCurrent()
        assertEquals(null, viewModel.readError.value)
        assertTrue(viewModel.isReady(date))
        assertTrue(repository.savedDates.isEmpty())
        assertTrue(repository.documentReadCount >= 4)
    }

    @Test
    fun retryingFailedObservationKeepsTheCurrentInMemoryDraftAndSelection() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        val date = LocalDate.of(2026, 9, 11)
        viewModel.setDate(date)
        runCurrent()
        viewModel.updateText(0, TextFieldValue("未丢失的草稿", TextRange(3)))
        val draft = viewModel.items.value
        repository.failDocumentRead = true
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.document.collect {} }
        runCurrent()
        assertEquals(JOURNAL_READ_FAILURE_MESSAGE, viewModel.readError.value)
        repository.failDocumentRead = false
        viewModel.retryRead()
        runCurrent()
        assertEquals(null, viewModel.readError.value)
        assertEquals(draft, viewModel.items.value)
        assertTrue(viewModel.isReady(date))
    }

    @Test
    fun titleDoesNotReplaceBodyAndDateChangeClearsFocus() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        val date = LocalDate.of(2026, 9, 4)
        viewModel.setDate(date)
        runCurrent()
        viewModel.updateText(0, TextFieldValue("保留正文"))
        val bodyKey = viewModel.items.value.single().editorKey
        viewModel.updateTitle(TextFieldValue("新标题"))
        advanceUntilIdle()
        assertEquals(listOf(JournalBlockType.TITLE, JournalBlockType.TEXT_LINE), repository.lastBlockTypes)
        assertEquals(bodyKey, viewModel.items.value[1].editorKey)
        viewModel.addLine()
        assertEquals(true, viewModel.focusRequest.value.token > 0)
        viewModel.setDate(date.plusDays(1))
        assertEquals(0L, viewModel.focusRequest.value.token)
        advanceUntilIdle()
    }

    @Test
    fun rapidDateChangesNeverSaveOldTextToLoadingDate() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        val a = LocalDate.of(2026, 9, 1)
        val b = a.plusDays(1)
        val c = b.plusDays(1)
        viewModel.setDate(a)
        runCurrent()
        viewModel.updateText(0, TextFieldValue("只属于 A"))
        repository.delayedLoadDate = b
        viewModel.setDate(b)
        runCurrent()
        assertEquals(true, viewModel.loading.value)
        assertEquals(emptyList<JournalEditorItem>(), viewModel.items.value)
        viewModel.addLine()
        viewModel.updateText(0, TextFieldValue("加载中不能污染其他日期"))
        viewModel.setDate(c)
        runCurrent()
        advanceUntilIdle()
        assertEquals(false, repository.savedDates.contains(b))
        assertEquals(listOf("只属于 A"), repository.completedTextSnapshots)
        viewModel.setDate(a)
        runCurrent()
        assertEquals("只属于 A", (viewModel.items.value.single() as JournalEditorItem.Text).text)
    }

    @Test
    fun firstAutosaveKeepsEditorIdentityAndMultilineSelection() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 4))
        runCurrent()
        val key = viewModel.items.value.single().editorKey
        viewModel.updateText(0, TextFieldValue("第一行\n第二行", TextRange(1, 6)))
        viewModel.setTextColor(123L)
        advanceTimeBy(281)
        advanceUntilIdle()
        val line = viewModel.items.value.single() as JournalEditorItem.Text
        assertEquals(key, line.editorKey)
        assertEquals(TextRange(1, 6), line.value.selection)
        assertEquals("第一行\n第二行", repository.lastBlocks.single().text)
        assertEquals(listOf(JournalTextStyleSpan(1, 6, color = 123L)), line.styleSpans)
    }

    @Test
    fun delayedTextSaveCannotOverwriteNewerImmediateSnapshot() = runTest(dispatcher) {
        val repository = RecordingJournalRepository()
        val viewModel = JournalViewModel(
            journalRepository = repository,
            mediaStore = UnusedMediaStore,
            audioRecorder = UnusedAudioRecorder,
            locationProvider = UnusedLocationProvider,
            dailyReviewRepository = EmptyDailyReviewRepository,
            lifeRepository = EmptyLifeRepository,
        )
        val date = LocalDate.of(2026, 9, 4)

        viewModel.setDate(date)
        runCurrent()
        viewModel.updateText(index = 0, value = TextFieldValue("旧文字", TextRange(3)))
        advanceTimeBy(280)
        runCurrent()
        repository.firstSaveStarted.await()

        viewModel.items.value = listOf(
            JournalEditorItem.Text(value = TextFieldValue("新文字")),
            JournalEditorItem.Media(
                type = JournalBlockType.IMAGE,
                assets = listOf(testAsset),
            ),
        )
        viewModel.removeMediaBlock(index = 1)
        runCurrent()

        repository.allowFirstSaveToFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("旧文字", "新文字"), repository.completedTextSnapshots)
    }

    @Test
    fun obsoleteDelayedSnapshotIsSkippedAfterImmediateSave() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = JournalViewModel(
            journalRepository = repository,
            mediaStore = UnusedMediaStore,
            audioRecorder = UnusedAudioRecorder,
            locationProvider = UnusedLocationProvider,
            dailyReviewRepository = EmptyDailyReviewRepository,
            lifeRepository = EmptyLifeRepository,
        )
        val date = LocalDate.of(2026, 9, 4)

        viewModel.setDate(date)
        runCurrent()
        viewModel.updateText(index = 0, value = TextFieldValue("会过期的文字", TextRange(6)))
        viewModel.items.value = listOf(
            JournalEditorItem.Text(value = TextFieldValue("最终文字")),
            JournalEditorItem.Media(
                type = JournalBlockType.IMAGE,
                assets = listOf(testAsset),
            ),
        )
        viewModel.removeMediaBlock(index = 1)
        advanceUntilIdle()

        assertEquals(listOf("最终文字"), repository.completedTextSnapshots)
    }

    @Test
    fun stoppingRecordingRegistersAudioAndInsertsItAtTheEditorCursor() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val recorder = CompletingAudioRecorder
        val viewModel = JournalViewModel(
            journalRepository = repository,
            mediaStore = UnusedMediaStore,
            audioRecorder = recorder,
            locationProvider = UnusedLocationProvider,
            dailyReviewRepository = EmptyDailyReviewRepository,
            lifeRepository = EmptyLifeRepository,
        )
        val date = LocalDate.of(2026, 9, 4)

        viewModel.setDate(date)
        runCurrent()
        viewModel.updateText(index = 0, value = TextFieldValue("前后", TextRange(1)))
        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals(true, recorder.started)
        assertEquals(listOf(JournalBlockType.TEXT_LINE, JournalBlockType.AUDIO, JournalBlockType.TEXT_LINE), repository.lastBlockTypes)
        assertEquals("前", repository.lastBlocks[0].text)
        assertEquals("后", repository.lastBlocks[2].text)
    }

    @Test
    fun currentLocationIsSavedWithCoordinatesWithoutReplacingJournalBlocks() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = JournalViewModel(
            journalRepository = repository,
            mediaStore = UnusedMediaStore,
            audioRecorder = UnusedAudioRecorder,
            locationProvider = FixedLocationProvider,
            dailyReviewRepository = EmptyDailyReviewRepository,
            lifeRepository = EmptyLifeRepository,
        )
        val date = LocalDate.of(2026, 9, 4)

        viewModel.setDate(date)
        runCurrent()
        viewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals(RecordedLocation(date, "广州市 · 天河区", 23.13, 113.36), repository.lastLocation)
    }

    @Test
    fun selectionAndRichStyleSurviveEditsAndAutosave() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        val date = LocalDate.of(2026, 9, 4)

        viewModel.setDate(date)
        runCurrent()
        viewModel.updateText(0, TextFieldValue("今天很开心", TextRange(2, 5)))
        viewModel.setTextColor(0xFFED8FAE)
        viewModel.setTextSize(JournalTextSize.LARGE)
        advanceTimeBy(281)
        advanceUntilIdle()

        val line = viewModel.items.value.single() as JournalEditorItem.Text
        assertEquals(TextRange(2, 5), line.value.selection)
        assertEquals(1, line.styleSpans.size)
        assertEquals(2, line.styleSpans.single().start)
        assertEquals(5, line.styleSpans.single().endExclusive)
        assertEquals(0xFFED8FAE, line.styleSpans.single().color)
        assertEquals(JournalTextSize.LARGE, line.styleSpans.single().textSize)
        assertEquals(line.styleSpans, repository.lastBlocks.single().textStyleSpans)
    }

    @Test
    fun emojiReplacesSelectionAndKeepsCollapsedCursorAfterEmoji() = runTest(dispatcher) {
        val viewModel = newViewModel(RecordingJournalRepository(blockFirstSave = false))
        viewModel.setDate(LocalDate.of(2026, 9, 4))
        runCurrent()
        viewModel.updateText(0, TextFieldValue("今天一般", TextRange(2, 3)))

        viewModel.appendEmoji("🥳")

        val line = viewModel.items.value.single() as JournalEditorItem.Text
        assertEquals("今天🥳般", line.text)
        assertEquals(TextRange(4), line.value.selection)
        assertEquals(0, viewModel.focusRequest.value.index)
        assertEquals(true, viewModel.focusRequest.value.token > 0)
    }

    @Test
    fun deletingOnlyTextPersistsEmptyDocumentButKeepsTransientEditorLine() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 4))
        runCurrent()
        viewModel.updateText(0, TextFieldValue("会删除"))
        advanceTimeBy(281)
        advanceUntilIdle()

        viewModel.removeTextBlock(0)
        runCurrent()

        assertEquals("", (viewModel.items.value.single() as JournalEditorItem.Text).text)
        assertEquals(emptyList<JournalBlockDraft>(), repository.lastBlocks)
        assertEquals("已移除文字", viewModel.undoNotice.value?.message)
    }

    @Test
    fun deletingMediaCanBeUndoneBeforeCleanupWindow() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        val date = LocalDate.of(2026, 9, 4)
        viewModel.setDate(date)
        runCurrent()
        viewModel.items.value = listOf(
            JournalEditorItem.Text(value = TextFieldValue("之前")),
            JournalEditorItem.Media(type = JournalBlockType.IMAGE, assets = listOf(testAsset)),
        )

        viewModel.removeMediaBlock(1)
        runCurrent()
        viewModel.undoDeletion()
        runCurrent()

        assertEquals(3, viewModel.items.value.size)
        assertEquals(testAsset.id, (viewModel.items.value[1] as JournalEditorItem.Media).assets.single().id)
        assertEquals("", (viewModel.items.value[2] as JournalEditorItem.Text).text)
        assertEquals(null, viewModel.undoNotice.value)
    }

    @Test
    fun editingTextRemapsStyledRangesWithoutLeavingInvalidOffsets() {
        val original = listOf(JournalTextStyleSpan(2, 5, color = 0xFFED8FAE))

        val afterInsert = remapStyleSpans("今天真开心", original, "今天天气真开心")
        val afterDelete = remapStyleSpans("今天真开心", original, "今天心")

        assertEquals(listOf(JournalTextStyleSpan(4, 7, color = 0xFFED8FAE)), afterInsert)
        assertEquals(listOf(JournalTextStyleSpan(2, 3, color = 0xFFED8FAE)), afterDelete)
    }

    @Test
    fun clearingColorFromSelectionPreservesItsTextSize() {
        val result = applyTextStyle(
            text = "ABCDE",
            spans = listOf(JournalTextStyleSpan(1, 4, color = 123L, textSize = JournalTextSize.LARGE)),
            target = 2..2,
            applyColor = true,
            color = null,
        )

        assertEquals(
            listOf(
                JournalTextStyleSpan(1, 2, color = 123L, textSize = JournalTextSize.LARGE),
                JournalTextStyleSpan(2, 3, color = null, textSize = JournalTextSize.LARGE),
                JournalTextStyleSpan(3, 4, color = 123L, textSize = JournalTextSize.LARGE),
            ),
            result,
        )
    }

    @Test
    fun togglingInlineDecorationPreservesOtherSelectedStyles() {
        val result = applyTextStyle(
            text = "ABCDE",
            spans = listOf(JournalTextStyleSpan(1, 4, color = 123L, italic = true)),
            target = 2..2,
            applyUnderline = true,
            underline = true,
            applyHighlight = true,
            highlightColor = 0x66FFE066,
        )

        assertEquals(
            listOf(
                JournalTextStyleSpan(1, 2, color = 123L, italic = true),
                JournalTextStyleSpan(2, 3, color = 123L, italic = true, underline = true, highlightColor = 0x66FFE066),
                JournalTextStyleSpan(3, 4, color = 123L, italic = true),
            ),
            result,
        )
    }

    @Test
    fun everyComponentHasWritableTextBeforeAndAfterWithStableKeys() {
        val title = JournalEditorItem.Text(value = TextFieldValue("标题"), isTitle = true)
        val media = JournalEditorItem.Media(type = JournalBlockType.IMAGE, assets = listOf(testAsset))
        val components = JOURNAL_COMPONENT_TYPES.map { JournalEditorItem.Component(type = it) }
        val anchored = journalItemsWithTextAnchors(listOf(title, media) + components)
        anchored.forEachIndexed { index, item ->
            if (item !is JournalEditorItem.Text) {
                assertEquals(false, (anchored[index - 1] as JournalEditorItem.Text).isTitle)
                assertEquals(false, (anchored[index + 1] as JournalEditorItem.Text).isTitle)
            }
        }
        assertEquals(anchored.map { it.editorKey }, journalItemsWithTextAnchors(anchored).map { it.editorKey })
        assertEquals(media.editorKey, anchored[2].editorKey)
    }

    @Test
    fun focusingBothSidesOfFirstComponentCreatesRealEditableBlocks() = runTest(dispatcher) {
        val viewModel = newViewModel(RecordingJournalRepository(blockFirstSave = false))
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        val component = JournalEditorItem.Component(type = JournalBlockType.LOCATION)
        viewModel.items.value = listOf(component)
        viewModel.focusTextBeside(0, after = false)
        assertEquals(0, viewModel.focusRequest.value.index)
        viewModel.updateText(0, TextFieldValue("前文"))
        viewModel.focusTextBeside(1, after = true)
        assertEquals(2, viewModel.focusRequest.value.index)
        viewModel.updateText(2, TextFieldValue("后文"))
        assertEquals(listOf("前文", "后文"), viewModel.items.value.filterIsInstance<JournalEditorItem.Text>().map { it.text })
        assertEquals(component.editorKey, viewModel.items.value[1].editorKey)
        advanceUntilIdle()
    }

    @Test
    fun removingAttachmentDropsItsEmptyAnchorsButKeepsTextStylesAndKeys() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        val before = JournalEditorItem.Text(value = TextFieldValue("前文"), color = 123L)
        val after = JournalEditorItem.Text(value = TextFieldValue("后文"), textSize = JournalTextSize.LARGE)
        val audio = JournalEditorItem.Media(type = JournalBlockType.AUDIO, assets = listOf(testAsset))
        viewModel.items.value = listOf(before, JournalEditorItem.Text(), audio, JournalEditorItem.Text(), after)
        viewModel.removeMediaBlock(2)
        runCurrent()
        assertEquals(listOf(before.editorKey, after.editorKey), viewModel.items.value.map { it.editorKey })
        assertEquals(listOf("前文", "后文"), repository.lastBlocks.map { it.text })
        assertEquals(123L, repository.lastBlocks[0].textColor)
        assertEquals(JournalTextSize.LARGE, repository.lastBlocks[1].textSize)
        viewModel.updateText(1, TextFieldValue("后文还能写"))
        advanceUntilIdle()
        assertEquals("后文还能写", repository.lastBlocks.last().text)
    }

    @Test
    fun deletingLastImageLeavesOneEmptyPageAnchorAndNoStoredBlankBlocks() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        viewModel.items.value = journalItemsWithTextAnchors(listOf(
            JournalEditorItem.Media(type = JournalBlockType.IMAGE, assets = listOf(testAsset)),
        ))
        viewModel.removeImage(1, 0)
        runCurrent()
        assertEquals(1, viewModel.items.value.size)
        assertEquals(emptyList<JournalBlockDraft>(), repository.lastBlocks)
        viewModel.updateText(0, TextFieldValue("继续写正文"))
        advanceUntilIdle()
        assertEquals("继续写正文", repository.lastBlocks.single().text)
    }

    @Test
    fun undoAttachmentDeletionFindsItsOriginalNeighboursAfterAnchorCompaction() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        val title = JournalEditorItem.Text(value = TextFieldValue("标题"), isTitle = true)
        val media = JournalEditorItem.Media(type = JournalBlockType.IMAGE, assets = listOf(testAsset))
        val after = JournalEditorItem.Text(value = TextFieldValue("后文"))
        viewModel.items.value = journalItemsWithTextAnchors(listOf(title, media, after))
        viewModel.removeMediaBlock(2)
        runCurrent()
        assertEquals(listOf(title.editorKey, after.editorKey), viewModel.items.value.map { it.editorKey })
        viewModel.undoDeletion()
        runCurrent()
        assertEquals(listOf(title.editorKey, media.editorKey, after.editorKey), viewModel.items.value
            .filterNot { it is JournalEditorItem.Text && it.text.isEmpty() }.map { it.editorKey })
    }

    @Test
    fun cursorAnchorNormalizationPreservesDeliberateNewlinesAndIsIdempotent() {
        val text = JournalEditorItem.Text(value = TextFieldValue("\n\n"))
        val anchored = journalItemsWithTextAnchors(listOf(JournalEditorItem.Text(), text, JournalEditorItem.Text()))
        assertEquals(listOf(text), anchored)
        assertEquals(anchored, journalItemsWithTextAnchors(anchored))
    }

    @Test
    fun checklistEnterCreatesIndependentRowsAndCheckedStatePersists() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        viewModel.setListStyle(JournalListStyle.CHECKLIST)
        viewModel.updateText(0, TextFieldValue("买菜\n做饭", TextRange(5)))
        assertEquals(listOf("买菜", "做饭"), viewModel.items.value.filterIsInstance<JournalEditorItem.Text>().map { it.text })
        assertEquals(1, viewModel.selectedIndex.value)
        viewModel.toggleChecklist(0)
        advanceUntilIdle()
        assertEquals(listOf(true, false), repository.lastBlocks.map { it.isChecked })
        assertEquals(listOf(JournalListStyle.CHECKLIST, JournalListStyle.CHECKLIST), repository.lastBlocks.map { it.listStyle })
        viewModel.toggleChecklist(0)
        advanceUntilIdle()
        assertEquals(listOf(false, false), repository.lastBlocks.map { it.isChecked })
    }

    @Test
    fun emptyChecklistIsPersistentAndSecondEnterLeavesList() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        viewModel.setListStyle(JournalListStyle.CHECKLIST)
        advanceUntilIdle()
        assertEquals(JournalListStyle.CHECKLIST, repository.lastBlocks.single().listStyle)
        viewModel.updateText(0, TextFieldValue("\n", TextRange(1)))
        advanceUntilIdle()
        assertEquals(JournalListStyle.NONE, (viewModel.items.value.single() as JournalEditorItem.Text).listStyle)
        assertTrue(repository.lastBlocks.isEmpty())
    }

    @Test
    fun numberedParagraphSplitPreservesAlignmentAndInlineStylesWithoutTextPrefixes() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 10)); runCurrent()
        viewModel.items.value = listOf(JournalEditorItem.Text(
            value = TextFieldValue("前文\n后文"),
            styleSpans = listOf(JournalTextStyleSpan(3, 5, bold = true)),
        ))
        viewModel.setTextAlignment(JournalTextAlignment.RIGHT)
        viewModel.setListStyle(JournalListStyle.LETTERED)
        advanceUntilIdle()
        assertEquals(listOf("前文", "后文"), repository.lastBlocks.map { it.text })
        assertEquals(listOf(JournalTextAlignment.RIGHT, JournalTextAlignment.RIGHT), repository.lastBlocks.map { it.textAlignment })
        assertEquals(listOf(JournalTextStyleSpan(0, 2, bold = true)), repository.lastBlocks.last().textStyleSpans)
        assertEquals(1, journalListOrdinal(viewModel.items.value, 0))
        assertEquals(2, journalListOrdinal(viewModel.items.value, 1))
        viewModel.selectItem(1)
        viewModel.setListStyle(JournalListStyle.NONE)
        assertEquals("后文", (viewModel.items.value[1] as JournalEditorItem.Text).text)
    }

    @Test
    fun textAnchorsNeverDeleteEmptyChecklistOrResetNumberingAcrossAdjacentItems() {
        val first = JournalEditorItem.Text(listStyle = JournalListStyle.CHECKLIST)
        val second = JournalEditorItem.Text(listStyle = JournalListStyle.CHECKLIST)
        val normalized = journalItemsWithTextAnchors(listOf(first, JournalEditorItem.Text(), second))
        assertEquals(listOf(first, second), normalized)
        val numbered = (1..3).map { JournalEditorItem.Text(value = TextFieldValue("条目"), listStyle = JournalListStyle.NUMBERED) }
        val mixed = numbered + JournalEditorItem.Component(type = JournalBlockType.LOCATION) + numbered.first().copy(editorKey = "new-list")
        assertEquals(3, journalListOrdinal(mixed, 2))
        assertEquals(1, journalListOrdinal(mixed, 4))
    }

    private fun newViewModel(repository: RecordingJournalRepository) = JournalViewModel(
        journalRepository = repository,
        mediaStore = UnusedMediaStore,
        audioRecorder = UnusedAudioRecorder,
        locationProvider = UnusedLocationProvider,
        dailyReviewRepository = EmptyDailyReviewRepository,
        lifeRepository = EmptyLifeRepository,
    )

    @Test
    fun nativeBlankCanvasFirstInputIsOnlyBodyAndRoundTripsWithoutHiddenTitle() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 11)); runCurrent()
        val initial = journalItemsToDocumentBuffer(viewModel.items.value)
        assertEquals("", initial.renderedText)
        val typed = initial.replaceText(0, 0, "第一行\n第二行")
        viewModel.updateDocumentBuffer(typed, typed.length, typed.length)
        advanceUntilIdle()
        assertTrue(repository.lastBlocks.all { it.type == JournalBlockType.TEXT_LINE })
        assertEquals("第一行\n第二行", journalItemsToDocumentBuffer(viewModel.items.value).renderedText)
        assertEquals(listOf("第一行\n", "第二行"), repository.lastBlocks.map { it.text })
    }

    @Test
    fun nativeCrossParagraphSelectionAppliesAlignmentToEveryTouchedParagraph() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 11)); runCurrent()
        val buffer = journalItemsToDocumentBuffer(viewModel.items.value)
            .replaceText(0, 0, "one\ntwo\nthree\nfour")
        viewModel.updateDocumentBuffer(buffer, 2, 12)

        viewModel.setTextAlignment(JournalTextAlignment.CENTER)
        advanceUntilIdle()

        assertEquals(
            listOf(
                JournalTextAlignment.CENTER,
                JournalTextAlignment.CENTER,
                JournalTextAlignment.CENTER,
                JournalTextAlignment.LEFT,
            ),
            repository.lastBlocks.map { it.textAlignment },
        )
    }

    @Test
    fun nativeChecklistStableKeyTogglesOnlyItsParagraphAndReturnIsUnchecked() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 11)); runCurrent()
        var buffer = journalItemsToDocumentBuffer(viewModel.items.value).replaceText(0, 0, "第一项")
        viewModel.updateDocumentBuffer(buffer, 1, 1)
        viewModel.setListStyle(JournalListStyle.CHECKLIST)
        val firstKey = viewModel.items.value.first().editorKey
        viewModel.toggleChecklist(firstKey)
        assertEquals(TextRange(1), viewModel.documentSelection.value)
        buffer = journalItemsToDocumentBuffer(viewModel.items.value).replaceText(3, 3, "\n第二项")
        viewModel.updateDocumentBuffer(buffer, buffer.length, buffer.length)
        assertEquals(listOf(true, false), viewModel.items.value.filterIsInstance<JournalEditorItem.Text>().map { it.isChecked })
        val secondKey = viewModel.items.value.last().editorKey
        viewModel.toggleChecklist(secondKey)
        viewModel.toggleChecklist(firstKey)
        advanceUntilIdle()
        assertEquals(listOf(false, true), repository.lastBlocks.map { it.isChecked })
        assertEquals("第一项\n第二项", journalItemsToDocumentBuffer(viewModel.items.value).renderedText)
        assertEquals(0L, viewModel.focusRequest.value.token)
    }

    @Test
    fun nativeSelectionDeletingMetadataClearsCanonicalDataWithoutUndoGhosts() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        repository.storedDocument = metadataDocument()
        val viewModel = newViewModel(repository)
        viewModel.setDate(repository.storedDocument!!.entry.entryDate); runCurrent()
        val buffer = journalItemsToDocumentBuffer(viewModel.items.value)
        val erased = buffer.replaceText(1, 4, "")
        viewModel.updateDocumentBuffer(erased, 1, 1)
        advanceUntilIdle()
        assertEquals("前后", journalItemsToDocumentBuffer(viewModel.items.value).renderedText)
        assertEquals(setOf(JournalBlockType.LOCATION, JournalBlockType.TAGS, JournalBlockType.LINKS), repository.removedMetadata)
        assertEquals(null, repository.storedDocument!!.entry.locationName)
        assertTrue(repository.storedDocument!!.tags.isEmpty())
        assertTrue(repository.storedDocument!!.linkedGoalIds.isEmpty())
        assertTrue(repository.storedDocument!!.linkedTodoIds.isEmpty())
        repeat(4) { viewModel.undoEdit(); viewModel.redoEdit() }
        advanceUntilIdle()
        assertTrue(viewModel.items.value.none { it is JournalEditorItem.Component })
        assertTrue(repository.lastBlocks.none { it.type in JOURNAL_COMPONENT_TYPES })
    }

    @Test
    fun nativeSwipeMetadataRemovalUsesSameAtomicSaveAndPreservesOtherMediaUndo() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        repository.storedDocument = metadataDocument()
        val viewModel = newViewModel(repository)
        viewModel.setDate(repository.storedDocument!!.entry.entryDate); runCurrent()
        val media = JournalEditorItem.Media(type = JournalBlockType.IMAGE, assets = listOf(testAsset))
        viewModel.items.value = viewModel.items.value + media
        viewModel.selectDocumentRange(0, 0)
        viewModel.removeMediaBlock(viewModel.items.value.lastIndex)
        viewModel.removeComponent(viewModel.items.value.indexOfFirst { it is JournalEditorItem.Component && it.type == JournalBlockType.LOCATION })
        advanceUntilIdle()
        assertEquals(null, repository.storedDocument!!.entry.locationName)
        repeat(2) { viewModel.undoEdit() }
        advanceUntilIdle()
        assertTrue(viewModel.items.value.any { it is JournalEditorItem.Media })
        assertTrue(viewModel.items.value.none { it is JournalEditorItem.Component && it.type == JournalBlockType.LOCATION })
        viewModel.redoEdit()
        advanceUntilIdle()
        assertTrue(repository.lastBlocks.none { it.type == JournalBlockType.LOCATION })
    }

    @Test
    fun failedMetadataRemovalKeepsRetryIntentAndNeverRestoresGhostOnUndo() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        repository.storedDocument = metadataDocument()
        repository.failMetadataRemoval = true
        val viewModel = newViewModel(repository)
        viewModel.setDate(repository.storedDocument!!.entry.entryDate); runCurrent()
        viewModel.removeComponent(viewModel.items.value.indexOfFirst { it is JournalEditorItem.Component && it.type == JournalBlockType.TAGS })
        advanceUntilIdle()
        assertEquals(listOf("旅行"), repository.storedDocument!!.tags)
        assertTrue(viewModel.mediaImportError.value != null)
        viewModel.undoEdit()
        assertTrue(viewModel.items.value.none { it is JournalEditorItem.Component && it.type == JournalBlockType.TAGS })
        repository.failMetadataRemoval = false
        viewModel.persistImmediately()
        advanceUntilIdle()
        assertTrue(repository.storedDocument!!.tags.isEmpty())
        assertTrue(repository.lastBlocks.none { it.type == JournalBlockType.TAGS })
    }

    @Test
    fun nativeSmartReturnIsOneUndoStepAndPreservesLiteralTypedPrefixes() = runTest(dispatcher) {
        val repository = RecordingJournalRepository(blockFirstSave = false)
        val viewModel = newViewModel(repository)
        viewModel.setDate(LocalDate.of(2026, 9, 11)); runCurrent()
        var buffer = journalItemsToDocumentBuffer(viewModel.items.value).replaceText(0, 0, "A. 第一项")
        viewModel.updateDocumentBuffer(buffer, buffer.length, buffer.length)
        val returned = buffer.editText(buffer.length, buffer.length, "\n")
        viewModel.updateDocumentBuffer(returned.document, returned.caret, returned.caret)
        assertEquals("A. 第一项\nB. ", journalItemsToDocumentBuffer(viewModel.items.value).renderedText)
        viewModel.undoEdit()
        assertEquals("A. 第一项", journalItemsToDocumentBuffer(viewModel.items.value).renderedText)
        viewModel.redoEdit()
        buffer = journalItemsToDocumentBuffer(viewModel.items.value)
        assertEquals("A. 第一项\nB. ", buffer.renderedText)
        val exit = buffer.editText(buffer.length, buffer.length, "\n")
        viewModel.updateDocumentBuffer(exit.document, exit.caret, exit.caret)
        advanceUntilIdle()
        assertEquals("A. 第一项\n", journalItemsToDocumentBuffer(viewModel.items.value).renderedText)
        assertTrue(repository.lastBlocks.all { it.listStyle == JournalListStyle.NONE })
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], manifest = org.robolectric.annotation.Config.NONE)
class JournalImportRegressionTest {
    @Test
    fun successfulImportStaysInDocumentFlowAndCreatesWritableContinuation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = RecordingJournalRepository(blockFirstSave = false)
            val store = object : JournalMediaStore {
                override suspend fun import(uri: Uri) = ImportedJournalMedia(
                    privatePath = "C:/journal/inline.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 12L,
                    checksum = "inline",
                )
                override suspend fun delete(privatePath: String) = true
            }
            val viewModel = JournalViewModel(repository, store, UnusedAudioRecorder, UnusedLocationProvider, EmptyDailyReviewRepository, EmptyLifeRepository)
            viewModel.setDate(LocalDate.of(2026, 9, 10))
            runCurrent()
            viewModel.updateText(0, TextFieldValue("前后", TextRange(1)))

            viewModel.importMedia(listOf(Uri.parse("content://test/image")))
            advanceUntilIdle()

            assertEquals(listOf("前", "IMAGE", "后"), viewModel.items.value.map {
                when (it) {
                    is JournalEditorItem.Text -> it.text
                    is JournalEditorItem.Media -> it.type.name
                    is JournalEditorItem.Component -> it.type.name
                }
            })
            assertEquals(1, viewModel.selectedIndex.value)
            assertEquals(0L, viewModel.focusRequest.value.token)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedSlowImportDoesNotRollBackTextTypedDuringImport() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val release = CompletableDeferred<Unit>()
            val repository = RecordingJournalRepository(blockFirstSave = false)
            val store = object : JournalMediaStore {
                override suspend fun import(uri: Uri): ImportedJournalMedia {
                    release.await()
                    error("Simulated storage failure")
                }
                override suspend fun delete(privatePath: String) = true
            }
            val viewModel = JournalViewModel(repository, store, UnusedAudioRecorder, UnusedLocationProvider, EmptyDailyReviewRepository, EmptyLifeRepository)
            viewModel.setDate(LocalDate.of(2026, 9, 4))
            runCurrent()
            viewModel.updateText(0, TextFieldValue("导入前"))
            viewModel.importMedia(listOf(Uri.parse("content://test/video")))
            runCurrent()
            viewModel.updateText(0, TextFieldValue("导入期间继续写的文字"))
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals("导入期间继续写的文字", (viewModel.items.value.single() as JournalEditorItem.Text).text)
            assertEquals("导入期间继续写的文字", repository.lastBlocks.single().text)
            assertEquals(false, viewModel.importingMedia.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class RecordingJournalRepository(
    private val blockFirstSave: Boolean = true,
) : JournalRepository {
    val firstSaveStarted = CompletableDeferred<Unit>()
    val allowFirstSaveToFinish = CompletableDeferred<Unit>()
    val completedTextSnapshots = mutableListOf<String>()
    var lastBlocks = emptyList<JournalBlockDraft>()
    var lastLocation: RecordedLocation? = null
    val lastBlockTypes: List<JournalBlockType> get() = lastBlocks.map { it.type }
    private var saveCount = 0
    var delayedLoadDate: LocalDate? = null
    val allowLoad = CompletableDeferred<Unit>()
    val savedDates = mutableListOf<LocalDate>()
    var storedDocument: JournalDocument? = null
    var failMetadataRemoval = false
    var removedMetadata = emptySet<JournalBlockType>()
    var failDocumentRead = false
    var documentReadCount = 0

    override fun observeDocument(date: LocalDate): Flow<JournalDocument?> = flow {
        documentReadCount++
        check(!failDocumentRead) { "Simulated document read failure" }
        if (date == delayedLoadDate) allowLoad.await()
        emit(storedDocument?.takeIf { it.entry.entryDate == date })
    }

    override suspend fun saveBlocks(date: LocalDate, blocks: List<JournalBlockDraft>): List<String> {
        savedDates += date
        saveCount += 1
        if (blockFirstSave && saveCount == 1) {
            firstSaveStarted.complete(Unit)
            allowFirstSaveToFinish.await()
        }
        completedTextSnapshots += blocks.filter { it.type == JournalBlockType.TEXT_LINE }
            .joinToString(separator = "\n") { it.text.orEmpty() }
        lastBlocks = blocks
        return blocks.indices.map { "block-$saveCount-$it" }
    }

    override suspend fun setLocation(
        date: LocalDate,
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        lastLocation = RecordedLocation(date, locationName, latitude, longitude)
        storedDocument = storedDocument?.copy(entry = storedDocument!!.entry.copy(
            locationName = locationName, latitude = latitude, longitude = longitude,
        ))
    }

    override suspend fun saveBlocksRemovingComponents(date: LocalDate, blocks: List<JournalBlockDraft>, removedTypes: Set<JournalBlockType>): List<String> {
        check(!failMetadataRemoval) { "Simulated atomic save failure" }
        val ids = saveBlocks(date, blocks)
        removedMetadata = removedMetadata + removedTypes
        storedDocument = storedDocument?.let { doc -> doc.copy(
            entry = if (JournalBlockType.LOCATION in removedTypes) doc.entry.copy(locationName = null, latitude = null, longitude = null) else doc.entry,
            tags = if (JournalBlockType.TAGS in removedTypes) emptyList() else doc.tags,
            linkedGoalIds = if (JournalBlockType.LINKS in removedTypes) emptyList() else doc.linkedGoalIds,
            linkedTodoIds = if (JournalBlockType.LINKS in removedTypes) emptyList() else doc.linkedTodoIds,
        ) }
        return ids
    }

    override suspend fun registerMedia(
        privatePath: String,
        previewPath: String?,
        mimeType: String,
        sizeBytes: Long,
        checksum: String?,
        durationMillis: Long?,
    ): JournalMediaAsset = testAsset.copy(
        privatePath = privatePath,
        previewPath = previewPath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        checksum = checksum,
        durationMillis = durationMillis,
    )

    override suspend fun abandonUnlinkedMedia(assetId: String): Boolean = false
    override suspend fun linkGoal(date: LocalDate, goalId: String): Boolean = false
    override suspend fun unlinkGoal(date: LocalDate, goalId: String) = Unit
    override suspend fun pendingMediaCleanup(): List<JournalMediaAsset> = emptyList()
    override suspend fun confirmMediaFileDeleted(assetId: String): Boolean = false
}

private object EmptyDailyReviewRepository : DailyReviewRepository {
    override fun observe(date: LocalDate): Flow<DailyReview> =
        flowOf(DailyReview(date, completedTodos = emptyList(), checkedHabits = emptyList()))
}

private object EmptyLifeRepository : LifeRepository {
    override fun observeGoals(): Flow<List<LifeGoalWithEvents>> = flowOf(emptyList())
    override suspend fun createGoal(title: String, note: String?, type: LifeGoalType): String = error("not used")
    override suspend fun updateGoal(goalId: String, title: String, note: String?, type: LifeGoalType, accentColor: Long?) = Unit
    override suspend fun toggleManualResult(goalId: String, occurredOn: LocalDate): String? = null
    override suspend fun deleteManualEvent(eventId: String) = Unit
    override suspend fun setPosition(goalId: String, position: Long) = Unit
    override suspend fun reorderGoals(goalIds: List<String>) = Unit
    override suspend fun deleteGoal(goalId: String) = Unit
    override suspend fun addToDate(goalId: String, date: LocalDate): String? = null
}

private object UnusedMediaStore : JournalMediaStore {
    override suspend fun import(uri: Uri): ImportedJournalMedia = error("not used")
    override suspend fun delete(privatePath: String): Boolean = false
}

private object UnusedAudioRecorder : JournalAudioRecorder {
    override fun start() = Unit
    override suspend fun stop(): ImportedJournalMedia = error("not used")
    override fun cancel() = Unit
}

private object UnusedLocationProvider : JournalLocationProvider {
    override suspend fun currentLocation(): JournalLocation = error("not used")
}

private object FixedLocationProvider : JournalLocationProvider {
    override suspend fun currentLocation(): JournalLocation =
        JournalLocation("广州市 · 天河区", latitude = 23.13, longitude = 113.36)
}

private data class RecordedLocation(
    val date: LocalDate,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

private object CompletingAudioRecorder : JournalAudioRecorder {
    var started = false

    override fun start() {
        started = true
    }

    override suspend fun stop(): ImportedJournalMedia = ImportedJournalMedia(
        privatePath = "C:/journal/voice.m4a",
        mimeType = "audio/mp4",
        sizeBytes = 42L,
        checksum = "checksum",
        durationMillis = 1_500L,
    )

    override fun cancel() = Unit
}

private val testAsset = JournalMediaAsset(
    id = "asset-1",
    privatePath = "C:/journal/photo.jpg",
    mimeType = "image/jpeg",
    sizeBytes = 12,
    createdAt = java.time.Instant.EPOCH,
)

private fun metadataDocument(): JournalDocument {
    val now = java.time.Instant.EPOCH
    val entry = com.fishking.core.model.JournalEntry("doc", LocalDate.of(2026, 9, 11), "宁波", 29.8, 121.5, now, now)
    val types = listOf(JournalBlockType.TEXT_LINE, JournalBlockType.LOCATION, JournalBlockType.TAGS, JournalBlockType.LINKS, JournalBlockType.TEXT_LINE)
    return JournalDocument(entry, types.mapIndexed { index, type ->
        com.fishking.core.model.JournalContentBlock(com.fishking.core.model.JournalBlock(
            "node-$index", entry.id, index.toLong(), type, text = when (index) { 0 -> "前"; 4 -> "后"; else -> null },
            createdAt = now, updatedAt = now,
        ))
    }, linkedGoalIds = listOf("goal"), linkedTodoIds = listOf("todo"), tags = listOf("旅行"))
}
