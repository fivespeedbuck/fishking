package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.model.HabitPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class RoomDailyReviewRepositoryTest {
    private lateinit var database: FishKingDatabase
    private lateinit var homeRepository: RoomHomeRepository
    private lateinit var habitRepository: RoomHabitRepository
    private lateinit var repository: RoomDailyReviewRepository
    private val date = LocalDate.of(2026, 9, 4)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FishKingDatabase::class.java,
        ).allowMainThreadQueries().build()
        val next = AtomicInteger()
        val clock = Clock.fixed(Instant.parse("2026-09-04T07:00:00Z"), ZoneOffset.UTC)
        val idFactory = { "review-generated-${next.incrementAndGet()}" }
        homeRepository = RoomHomeRepository(database, clock, idFactory)
        habitRepository = RoomHabitRepository(database, clock, idFactory)
        repository = RoomDailyReviewRepository(homeRepository, habitRepository)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun reviewIsLiveAndContainsOnlyCompletedTodosAndCheckedHabits() = runTest {
        val completedTodo = homeRepository.createTodo("已完成", date)
        val openTodo = homeRepository.createTodo("未完成", date)
        homeRepository.toggleCompletion(completedTodo)
        val checkedHabit = habitRepository.createHabit("刷牙", 0xFF8FA7E4, date, HabitPeriod.DAILY, 2)
        habitRepository.createHabit("喝水", 0xFF355C52, date, HabitPeriod.DAILY, 1)
        habitRepository.toggleCheckIn(checkedHabit, date)

        var review = repository.observe(date).first()
        assertEquals(listOf(completedTodo), review.completedTodos.map { it.id })
        assertEquals(listOf(checkedHabit), review.checkedHabits.map { it.habitId })
        assertEquals(1, review.checkedHabits.single().count)

        homeRepository.toggleCompletion(completedTodo)
        habitRepository.toggleCheckIn(checkedHabit, date)
        habitRepository.toggleCheckIn(checkedHabit, date)
        review = repository.observe(date).first()
        assertEquals(emptyList<String>(), review.completedTodos.map { it.id })
        assertTrue(review.checkedHabits.isEmpty())
        assertTrue(review.isEmpty)
        assertEquals("未完成", database.todoDao().findOccurrence(openTodo)?.title)
    }

    @Test
    fun weeklyReviewUsesHomeProgressButRequiresARealCheckOnThatDay() = runTest {
        val id = habitRepository.createHabit("锻炼 #健康", 0xFF8FA7E4, date.minusDays(3), HabitPeriod.WEEKLY, 4)
        habitRepository.toggleCheckIn(id, date.minusDays(2))
        habitRepository.toggleCheckIn(id, date.minusDays(1))
        assertTrue(repository.observe(date).first().isEmpty)
        habitRepository.toggleCheckIn(id, date)
        val summary = repository.observe(date).first().checkedHabits.single()
        assertEquals(1, summary.count)
        assertEquals(3, summary.displayCount)
        assertEquals(4, summary.targetCount)
    }

    @Test
    fun deletingCompletedTodoRemovesItFromJournalReviewImmediately() = runTest {
        val todoId = homeRepository.createTodo("临时任务", date)
        homeRepository.toggleCompletion(todoId)
        assertEquals(1, repository.observe(date).first().completedTodos.size)

        homeRepository.deleteTodo(todoId)

        assertTrue(repository.observe(date).first().completedTodos.isEmpty())
    }
}
