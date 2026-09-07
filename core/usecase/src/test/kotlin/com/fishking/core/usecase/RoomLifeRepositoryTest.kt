package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.LifeGoalType
import com.fishking.core.database.JournalEntity
import com.fishking.core.database.JournalLifeGoalCrossRef
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
class RoomLifeRepositoryTest {
    private lateinit var database: FishKingDatabase
    private lateinit var homeRepository: RoomHomeRepository
    private lateinit var repository: RoomLifeRepository
    private val date = LocalDate.of(2026, 9, 4)
    private val now = Instant.parse("2026-09-04T07:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FishKingDatabase::class.java,
        ).allowMainThreadQueries().build()
        val ids = AtomicInteger()
        val idFactory = { "life-generated-${ids.incrementAndGet()}" }
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        homeRepository = RoomHomeRepository(database, clock, idFactory)
        repository = RoomLifeRepository(database, homeRepository, clock, idFactory)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun createAndEditUseLightweightDefaultsAndCleanOptionalNote() = runTest {
        val id = repository.createGoal("  去泰山  ", "   ")

        var goal = repository.observeGoals().first().single().goal
        assertEquals("去泰山", goal.title)
        assertEquals(LifeGoalType.ONE_TIME, goal.type)
        assertNull(goal.note)

        repository.updateGoal(id, "去看日出", "  和朋友一起  ", LifeGoalType.ONGOING)
        goal = repository.observeGoals().first().single().goal
        assertEquals("去看日出", goal.title)
        assertEquals("和朋友一起", goal.note)
        assertEquals(LifeGoalType.ONGOING, goal.type)
    }

    @Test
    fun manualToggleAppendsStableCheckCrossHistoryEvenAtSameInstant() = runTest {
        val id = repository.createGoal("戒烟", type = LifeGoalType.ONGOING)

        repository.toggleManualResult(id, date)
        repository.toggleManualResult(id, date)
        repository.toggleManualResult(id, date)

        val result = repository.observeGoals().first().single()
        assertEquals(
            listOf(LifeGoalResult.CHECK, LifeGoalResult.CROSS, LifeGoalResult.CHECK),
            result.events.map { it.result },
        )
        assertEquals(listOf(1_024L, 2_048L, 3_072L), result.events.map { it.position })
        assertEquals(LifeGoalResult.CHECK, result.currentResult)
    }

    @Test
    fun deletingManualEventRevealsPreviousResult() = runTest {
        val id = repository.createGoal("持续锻炼", type = LifeGoalType.ONGOING)
        repository.toggleManualResult(id, date)
        repository.toggleManualResult(id, date)
        val lastEventId = repository.toggleManualResult(id, date)!!

        repository.deleteManualEvent(lastEventId)

        val result = repository.observeGoals().first().single()
        assertEquals(listOf(LifeGoalResult.CHECK, LifeGoalResult.CROSS), result.events.map { it.result })
        assertEquals(LifeGoalResult.CROSS, result.currentResult)
    }

    @Test
    fun addingGoalToDateLinksTodoAndCompletionCreatesAutomaticCheck() = runTest {
        val goalId = repository.createGoal("去泰山")

        val todoId = repository.addToDate(goalId, date)
        assertNotNull(todoId)
        homeRepository.toggleCompletion(todoId!!)

        val result = repository.observeGoals().first().single()
        assertEquals(1, result.events.size)
        assertEquals(LifeGoalResult.CHECK, result.events.single().result)
        assertEquals(LifeGoalEventSource.TODO, result.events.single().source)
        assertEquals(todoId, result.events.single().sourceTodoOccurrenceId)
    }

    @Test
    fun automaticEventCannotBeRemovedThroughManualHistoryEditor() = runTest {
        val goalId = repository.createGoal("完成项目")
        val todoId = repository.addToDate(goalId, date)!!
        homeRepository.toggleCompletion(todoId)
        val automaticEventId = repository.observeGoals().first().single().events.single().id

        repository.deleteManualEvent(automaticEventId)

        assertEquals(1, repository.observeGoals().first().single().events.size)
    }

    @Test
    fun deletingGoalHidesHistoryAndPreventsLaterTodoCompletionFromAddingEvent() = runTest {
        val goalId = repository.createGoal("看极光")
        repository.toggleManualResult(goalId, date)
        val todoId = repository.addToDate(goalId, date)!!

        repository.deleteGoal(goalId)
        homeRepository.toggleCompletion(todoId)

        assertEquals(0, repository.observeGoals().first().size)
        assertFalse(database.todoDao().activeLinkedGoalIds(todoId).contains(goalId))
        assertNull(database.lifeGoalDao().findGoal(goalId))
        assertTrue(database.lifeGoalDao().eventsForGoal(goalId).isEmpty())
    }

    @Test
    fun goalsExposeOnlyTheirRealLinkedJournalDates() = runTest {
        val goalId = repository.createGoal("去泰山")
        val otherGoalId = repository.createGoal("学潜水")
        val later = date.plusDays(3)
        listOf(date, later).forEachIndexed { index, journalDate ->
            val journalId = "journal-$index"
            database.journalDao().insertJournal(
                JournalEntity(
                    id = journalId,
                    entryDate = journalDate,
                    locationName = null,
                    latitude = null,
                    longitude = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.journalDao().linkGoal(JournalLifeGoalCrossRef(journalId, goalId))
            if (journalDate == later) {
                database.journalDao().linkGoal(JournalLifeGoalCrossRef(journalId, otherGoalId))
            }
        }

        val goals = repository.observeGoals().first().associateBy { it.goal.id }

        assertEquals(listOf(date, later), goals.getValue(goalId).linkedJournalDates)
        assertEquals(listOf(later), goals.getValue(otherGoalId).linkedJournalDates)
    }

    @Test
    fun reorderGoalsPersistsCompleteVisibleOrderWithStableSpacing() = runTest {
        val first = repository.createGoal("第一条")
        val second = repository.createGoal("第二条")
        val third = repository.createGoal("第三条")

        repository.reorderGoals(listOf(third, first, second))

        val goals = repository.observeGoals().first().map { it.goal }
        assertEquals(listOf(third, first, second), goals.map { it.id })
        assertEquals(listOf(1_024L, 2_048L, 3_072L), goals.map { it.position })
    }
}
