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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

        assertEquals(2, viewModel.items.value.size)
        assertEquals(testAsset.id, (viewModel.items.value[1] as JournalEditorItem.Media).assets.single().id)
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

    private fun newViewModel(repository: RecordingJournalRepository) = JournalViewModel(
        journalRepository = repository,
        mediaStore = UnusedMediaStore,
        audioRecorder = UnusedAudioRecorder,
        locationProvider = UnusedLocationProvider,
        dailyReviewRepository = EmptyDailyReviewRepository,
        lifeRepository = EmptyLifeRepository,
    )
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], manifest = org.robolectric.annotation.Config.NONE)
class JournalImportRegressionTest {
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

    override fun observeDocument(date: LocalDate): Flow<JournalDocument?> = flow {
        if (date == delayedLoadDate) allowLoad.await()
        emit(null)
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
    override suspend fun updateGoal(goalId: String, title: String, note: String?, type: LifeGoalType) = Unit
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
