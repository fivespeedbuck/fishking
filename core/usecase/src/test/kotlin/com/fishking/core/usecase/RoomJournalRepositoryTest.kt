package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.LifeGoalEntity
import com.fishking.core.model.JournalBlockDraft
import com.fishking.core.model.JournalBlockType
import com.fishking.core.model.JournalTextSize
import com.fishking.core.model.JournalTextStyleSpan
import com.fishking.core.model.JournalTextAlignment
import com.fishking.core.model.JournalListStyle
import com.fishking.core.model.LifeGoalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class RoomJournalRepositoryTest {
    private lateinit var database: FishKingDatabase
    private lateinit var repository: RoomJournalRepository
    private val date = LocalDate.of(2026, 9, 4)
    private val now = Instant.parse("2026-09-04T07:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FishKingDatabase::class.java,
        ).allowMainThreadQueries().build()
        val next = AtomicInteger()
        repository = RoomJournalRepository(
            database = database,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            newId = { "journal-generated-${next.incrementAndGet()}" },
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun timelineRangeDoesNotLoadOutsideRequestedMonth() = runTest {
        val previous = repository.forEntry("previous", java.time.LocalTime.NOON)
        val current = repository.forEntry("current", java.time.LocalTime.NOON)
        previous.saveBlocks(date.minusMonths(1), listOf(JournalBlockDraft(type = JournalBlockType.TITLE, text = "上月")))
        current.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TITLE, text = "本月")))
        val month = java.time.YearMonth.from(date)
        assertEquals(listOf("current"), repository.observeTimelineRange(month.atDay(1), month.atEndOfMonth()).first().map { it.id })
        assertEquals(2, repository.observeTimelineRange(month.minusMonths(1).atDay(1), month.atEndOfMonth()).first().size)
    }

    @Test
    fun malformedOptionalEntryTimeDoesNotMakeTimelineOrDocumentUnreadable() = runTest {
        val scoped = repository.forEntry("legacy-time", java.time.LocalTime.of(9, 10))
        scoped.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "原正文")))
        val original = database.journalDao().findById("legacy-time")!!
        for (invalidTime in listOf("", "not-a-time", "25:90", "09:10:00+08:00")) {
            database.journalDao().updateJournal(original.copy(entryTime = invalidTime))
            val timeline = repository.observeTimelineRange(date, date).first().single()
            assertEquals("legacy-time", timeline.id)
            assertEquals("原正文", timeline.excerpt)
            assertNull(timeline.time)
            val document = scoped.observeDocument(date).first()!!
            assertEquals("原正文", document.blocks.single().block.text)
            assertNull(document.entry.entryTime)
            assertEquals(invalidTime, database.journalDao().findById("legacy-time")!!.entryTime)
        }
        database.journalDao().updateJournal(original.copy(entryTime = "09:10:25.123"))
        assertEquals(java.time.LocalTime.of(9, 10, 25, 123_000_000), repository.observeTimeline().first().single().time)
    }

    @Test
    fun metadataComponentsRoundTripInBodyWhileLocationAndTagsStayCanonical() = runTest {
        repository.setLocation(date, "宁波", 29.8, 121.5)
        repository.setTags(date, listOf("生活", "旅行"))
        val types = listOf(JournalBlockType.TEXT_LINE, JournalBlockType.LOCATION, JournalBlockType.TEXT_LINE,
            JournalBlockType.LINKS, JournalBlockType.TEXT_LINE, JournalBlockType.TAGS, JournalBlockType.TEXT_LINE)
        val ids = repository.saveBlocks(date, types.map { JournalBlockDraft(type = it, text = if (it == JournalBlockType.TEXT_LINE) "文字" else null) })
        val saved = repository.observeDocument(date).first()!!
        assertEquals(types, saved.blocks.map { it.block.type })
        assertEquals(ids, saved.blocks.map { it.block.id })
        assertEquals("宁波", saved.entry.locationName)
        assertEquals(setOf("生活", "旅行"), saved.tags.toSet())
        assertTrue(saved.blocks.filter { it.block.type != JournalBlockType.TEXT_LINE }.all { it.media.isEmpty() && it.block.text == null })
        repository.setLocation(date, "杭州")
        assertEquals("杭州", repository.observeDocument(date).first()!!.entry.locationName)
    }

    @Test
    fun componentRemovalCommitsCanonicalMetadataAndDocumentInOneTransaction() = runTest {
        repository.setLocation(date, "宁波", 29.8, 121.5)
        repository.setTags(date, listOf("旅行"))
        insertGoal("goal", deletedAt = null)
        repository.linkGoal(date, "goal")
        val home = RoomHomeRepository(database, Clock.fixed(now, ZoneOffset.UTC)) { "linked-todo" }
        val todo = home.createTodo("待办", date)
        repository.linkTodo(date, todo)
        repository.saveBlocks(date, listOf(JournalBlockType.LOCATION, JournalBlockType.TAGS, JournalBlockType.LINKS)
            .map { JournalBlockDraft(type = it) })
        repository.saveBlocksRemovingComponents(date,
            listOf(JournalBlockDraft(id = "body", type = JournalBlockType.TEXT_LINE, text = "前后")),
            setOf(JournalBlockType.LOCATION, JournalBlockType.TAGS, JournalBlockType.LINKS))
        val saved = repository.observeDocument(date).first()!!
        assertNull(saved.entry.locationName)
        assertNull(saved.entry.latitude)
        assertNull(saved.entry.longitude)
        assertTrue(saved.tags.isEmpty())
        assertTrue(saved.linkedGoalIds.isEmpty())
        assertTrue(saved.linkedTodoIds.isEmpty())
        assertEquals(listOf("前后"), saved.blocks.map { it.block.text })
        assertNotNull(database.lifeGoalDao().findGoal("goal"))
        assertNotNull(database.todoDao().findOccurrence(todo))
    }

    @Test
    fun componentRemovalRollsMetadataBackIfBlockWriteFails() = runTest {
        repository.setLocation(date, "保留位置", 29.8, 121.5)
        repository.setTags(date, listOf("保留TAG"))
        repository.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.LOCATION), JournalBlockDraft(type = JournalBlockType.TAGS)))
        val duplicate = JournalBlockDraft(id = "duplicate", type = JournalBlockType.TEXT_LINE, text = "same id")
        assertTrue(runCatching {
            repository.saveBlocksRemovingComponents(date, listOf(duplicate, duplicate),
                setOf(JournalBlockType.LOCATION, JournalBlockType.TAGS))
        }.isFailure)
        val saved = repository.observeDocument(date).first()!!
        assertEquals("保留位置", saved.entry.locationName)
        assertEquals(listOf("保留TAG"), saved.tags)
        assertEquals(listOf(JournalBlockType.LOCATION, JournalBlockType.TAGS), saved.blocks.map { it.block.type })
    }

    @Test
    fun paragraphAlignmentAndIndependentChecklistStateSurviveRepositoryRecreation() = runTest {
        val drafts = JournalListStyle.values().mapIndexed { index, style ->
            JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "原始文字 $index",
                textAlignment = JournalTextAlignment.values()[index % 3], listStyle = style,
                isChecked = style == JournalListStyle.CHECKLIST,
                textStyleSpans = listOf(JournalTextStyleSpan(0, 2, bold = true)),
            )
        }
        val ids = repository.saveBlocks(date, drafts)
        val recreated = RoomJournalRepository(database)
        val saved = recreated.observeDocument(date).first()!!.blocks.map { it.block }
        assertEquals(drafts.map { it.text }, saved.map { it.text })
        assertEquals(drafts.map { it.textAlignment }, saved.map { it.textAlignment })
        assertEquals(drafts.map { it.listStyle }, saved.map { it.listStyle })
        assertEquals(drafts.map { it.isChecked }, saved.map { it.isChecked })
        assertEquals(drafts.map { it.textStyleSpans }, saved.map { it.textStyleSpans })
        assertEquals(ids, saved.map { it.id })
        recreated.saveBlocks(date, drafts.mapIndexed { index, draft -> draft.copy(id = ids[index], isChecked = false) })
        assertTrue(repository.observeDocument(date).first()!!.blocks.none { it.block.isChecked })
    }

    @Test
    fun nonChecklistCannotStoreCheckedState() = runTest {
        val result = runCatching { repository.saveBlocks(date, listOf(
            JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "普通文字", isChecked = true),
        )) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun sameDayEntriesKeepIndependentTextLocationAndCalendar() = runTest {
        val morning = repository.forEntry("morning", java.time.LocalTime.of(9, 0))
        val evening = repository.forEntry("evening", java.time.LocalTime.of(20, 30))
        morning.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "上午正文")))
        evening.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "晚上正文")))
        morning.setLocation(date, "上午地点")
        val photo = morning.registerMedia("/private/morning.jpg", null, "image/jpeg", 100)
        morning.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.IMAGE, mediaAssetIds = listOf(photo.id))))
        assertTrue(evening.observeDocument(date).first()!!.blocks.all { it.media.isEmpty() })
        assertEquals("晚上正文", evening.observeDocument(date).first()!!.blocks.single().block.text)
        assertNull(evening.observeDocument(date).first()!!.entry.locationName)
        assertEquals(listOf("morning", "evening"), repository.observeTimeline().first().map { it.id })
        morning.saveBlocks(date, emptyList())
        assertEquals(listOf("evening"), repository.observeTimeline().first().map { it.id })
        assertEquals(setOf(date), repository.observeEntryDates().first())
    }

    @Test
    fun deletingOneTimelineEntryHidesOnlyThatEntryAndKeepsItsRowsRecoverable() = runTest {
        val morning = repository.forEntry("morning-delete", java.time.LocalTime.of(9, 0))
        val evening = repository.forEntry("evening-keep", java.time.LocalTime.of(20, 0))
        morning.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "上午正文")))
        evening.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "晚上正文")))

        repository.deleteEntry("morning-delete")

        assertEquals(listOf("evening-keep"), repository.observeTimeline().first().map { it.id })
        assertNull(morning.observeDocument(date).first())
        assertNotNull(database.journalDao().findById("morning-delete")?.deletedAt)
        assertTrue(database.journalDao().blocksForJournal("morning-delete").isNotEmpty())
    }

    @Test
    fun calendarMarksOnlyPersistedNonEmptyDiaryDates() = runTest {
        repository.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "")))
        assertTrue(repository.observeEntryDates().first().isEmpty())
        repository.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TITLE, text = "标题")))
        assertEquals(setOf(date), repository.observeEntryDates().first())
        repository.saveBlocks(date, emptyList())
        assertTrue(repository.observeEntryDates().first().isEmpty())
    }

    @Test
    fun titleIsIndependentFromExistingBodyAndRoundTripsWithoutChangingItsIdentity() = runTest {
        val body = repository.saveBlocks(date, listOf(JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "原正文"))).single()
        repository.saveBlocks(date, listOf(
            JournalBlockDraft(type = JournalBlockType.TITLE, text = "今天的标题"),
            JournalBlockDraft(id = body, type = JournalBlockType.TEXT_LINE, text = "原正文"),
        ))
        val blocks = repository.observeDocument(date).first()!!.blocks
        assertEquals(JournalBlockType.TITLE, blocks[0].block.type)
        assertEquals("今天的标题", blocks[0].block.text)
        assertEquals(body, blocks[1].block.id)
        assertEquals("原正文", blocks[1].block.text)
    }

    @Test
    fun textLinesKeepOrderColorAndStableIdentityAcrossAutosave() = runTest {
        val ids = repository.saveBlocks(
            date,
            listOf(
                JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "第一行", textColor = 0xFF355C52),
                JournalBlockDraft(type = JournalBlockType.TEXT_LINE, text = "第二行", textColor = 0xFFED8FAE),
            ),
        )
        repository.saveBlocks(
            date,
            listOf(
                JournalBlockDraft(id = ids[1], type = JournalBlockType.TEXT_LINE, text = "第二行修改", textColor = 0xFFED8FAE),
                JournalBlockDraft(id = ids[0], type = JournalBlockType.TEXT_LINE, text = "第一行", textColor = 0xFF355C52),
            ),
        )

        val document = repository.observeDocument(date).first()!!
        assertEquals(listOf(ids[1], ids[0]), document.blocks.map { it.block.id })
        assertEquals(listOf("第二行修改", "第一行"), document.blocks.map { it.block.text })
        assertEquals(listOf(0xFFED8FAE, 0xFF355C52), document.blocks.map { it.block.textColor })
        assertEquals(listOf(now, now), document.blocks.map { it.block.createdAt })
    }

    @Test
    fun richTextBaseSizeAndStyledRunsRoundTrip() = runTest {
        repository.saveBlocks(
            date,
            listOf(
                JournalBlockDraft(
                    type = JournalBlockType.TEXT_LINE,
                    text = "今天很开心",
                    textColor = 0xFF355C52,
                    textSize = JournalTextSize.LARGE,
                    textStyleSpans = listOf(
                        JournalTextStyleSpan(
                            start = 2,
                            endExclusive = 5,
                            color = 0xFFED8FAE,
                            textSize = JournalTextSize.TITLE,
                            bold = true,
                            underline = true,
                            highlightColor = 0x66FFE066,
                        ),
                    ),
                ),
            ),
        )

        val block = repository.observeDocument(date).first()!!.blocks.single().block
        assertEquals(JournalTextSize.LARGE, block.textSize)
        assertEquals(
            listOf(JournalTextStyleSpan(
                2, 5, 0xFFED8FAE, JournalTextSize.TITLE,
                bold = true,
                underline = true,
                highlightColor = 0x66FFE066,
            )),
            block.textStyleSpans,
        )
    }

    @Test
    fun imageRowKeepsOneToThreeAssetsInSelectedOrder() = runTest {
        val first = registerImage("first.jpg")
        val second = registerImage("second.gif", "image/gif")
        val third = registerImage("third.png", "image/png")

        repository.saveBlocks(
            date,
            listOf(
                JournalBlockDraft(
                    type = JournalBlockType.IMAGE,
                    mediaAssetIds = listOf(second, first, third),
                ),
            ),
        )

        val media = repository.observeDocument(date).first()!!.blocks.single().media
        assertEquals(listOf(second, first, third), media.map { it.id })
    }

    @Test
    fun journalTagsAndImagePreviewsRoundTripIntoTheTimelineCard() = runTest {
        val first = registerImage("timeline-first.jpg")
        val second = registerImage("timeline-second.png", "image/png")
        val audio = repository.registerMedia(
            privatePath = "media/voice.m4a",
            previewPath = null,
            mimeType = "audio/mp4",
            sizeBytes = 64L,
        )
        repository.saveBlocks(
            date,
            listOf(
                JournalBlockDraft(type = JournalBlockType.IMAGE, mediaAssetIds = listOf(first, second)),
                JournalBlockDraft(type = JournalBlockType.AUDIO, mediaAssetIds = listOf(audio.id)),
            ),
        )

        assertTrue(repository.setTags(date, listOf("生活", "#宁波")))

        val document = repository.observeDocument(date).first()!!
        assertEquals(setOf("生活", "宁波"), document.tags.toSet())
        val timeline = repository.observeTimeline().first().single()
        assertEquals(listOf("preview/timeline-first.jpg", "preview/timeline-second.png"), timeline.thumbnailPaths)
        assertEquals(3, timeline.mediaCount)
        assertEquals(setOf("生活", "宁波"), timeline.tags.toSet())

        assertTrue(repository.setTags(date, listOf("旅行")))
        assertEquals(listOf("旅行"), repository.observeDocument(date).first()!!.tags)
    }

    @Test
    fun imageRowRejectsMoreThanThreeAssetsWithoutCreatingJournal() = runTest {
        val ids = (1..4).map { registerImage("$it.jpg") }

        val error = runCatching {
            repository.saveBlocks(
                date,
                listOf(JournalBlockDraft(type = JournalBlockType.IMAGE, mediaAssetIds = ids)),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertNull(database.journalDao().findByDate(date))
    }

    @Test
    fun optionalLocationCanBeSetManuallyAndCleared() = runTest {
        repository.setLocation(date, "  泰山南天门  ", 36.255, 117.101)

        var entry = repository.observeDocument(date).first()!!.entry
        assertEquals("泰山南天门", entry.locationName)
        assertEquals(36.255, entry.latitude!!, 0.0)
        assertEquals(117.101, entry.longitude!!, 0.0)

        repository.setLocation(date, null)
        entry = repository.observeDocument(date).first()!!.entry
        assertNull(entry.locationName)
        assertNull(entry.latitude)
        assertNull(entry.longitude)
    }

    @Test
    fun journalLinksOnlyActiveGoalsAndUnlinksWithoutDeletingGoal() = runTest {
        insertGoal("goal-active", deletedAt = null)
        insertGoal("goal-deleted", deletedAt = now)

        assertTrue(repository.linkGoal(date, "goal-active"))
        assertFalse(repository.linkGoal(date, "goal-deleted"))
        assertEquals(listOf("goal-active"), repository.observeDocument(date).first()!!.linkedGoalIds)

        repository.unlinkGoal(date, "goal-active")
        assertEquals(emptyList<String>(), repository.observeDocument(date).first()!!.linkedGoalIds)
        assertNotNull(database.lifeGoalDao().findGoal("goal-active"))
    }

    @Test
    fun journalLinksCompletedAndOpenTodosWithoutCopyingThem() = runTest {
        val home = RoomHomeRepository(database, Clock.fixed(now, ZoneOffset.UTC)) { "linked-todo" }
        val todoId = home.createTodo("当天待办", date)
        assertTrue(repository.linkTodo(date, todoId))
        assertEquals(listOf(todoId), repository.observeDocument(date).first()!!.linkedTodoIds)
        home.toggleCompletion(todoId)
        assertEquals(listOf(todoId), repository.observeDocument(date).first()!!.linkedTodoIds)
        repository.unlinkTodo(date, todoId)
        assertTrue(repository.observeDocument(date).first()!!.linkedTodoIds.isEmpty())
        assertNotNull(database.todoDao().findOccurrence(todoId))
    }

    @Test
    fun removedMediaIsQueuedOnlyAfterItsLastDatabaseLinkIsGone() = runTest {
        val assetId = registerImage("shared.jpg")
        val ids = repository.saveBlocks(
            date,
            listOf(
                JournalBlockDraft(type = JournalBlockType.IMAGE, mediaAssetIds = listOf(assetId)),
                JournalBlockDraft(type = JournalBlockType.IMAGE, mediaAssetIds = listOf(assetId)),
            ),
        )

        repository.saveBlocks(
            date,
            listOf(JournalBlockDraft(id = ids[1], type = JournalBlockType.IMAGE, mediaAssetIds = listOf(assetId))),
        )
        assertEquals(0, repository.pendingMediaCleanup().size)

        repository.saveBlocks(date, emptyList())
        assertEquals(listOf(assetId), repository.pendingMediaCleanup().map { it.id })
        assertTrue(repository.confirmMediaFileDeleted(assetId))
        assertNull(database.journalDao().findMediaAsset(assetId))
    }

    @Test
    fun failedImportCanAbandonUnlinkedMetadataThroughConfirmedCleanup() = runTest {
        val assetId = registerImage("failed-import.jpg")

        assertTrue(repository.abandonUnlinkedMedia(assetId))
        assertEquals(listOf(assetId), repository.pendingMediaCleanup().map { it.id })
        assertTrue(repository.confirmMediaFileDeleted(assetId))
        assertNull(database.journalDao().findMediaAsset(assetId))
    }

    private suspend fun registerImage(name: String, mimeType: String = "image/jpeg"): String =
        repository.registerMedia(
            privatePath = "media/$name",
            previewPath = "preview/$name",
            mimeType = mimeType,
            sizeBytes = 128L,
        ).id

    private suspend fun insertGoal(id: String, deletedAt: Instant?) {
        database.lifeGoalDao().insertGoal(
            LifeGoalEntity(
                id = id,
                title = id,
                note = null,
                type = LifeGoalType.ONE_TIME.name,
                position = 1_024L,
                deletedAt = deletedAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
