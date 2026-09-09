package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
class RoomHabitRepositoryTest {
    private lateinit var database: FishKingDatabase
    private lateinit var repository: RoomHabitRepository
    private val today = LocalDate.of(2026, 9, 4)
    private val currentWeek = HabitRules.weekStart(today)
    private val now = Instant.parse("2026-09-04T07:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FishKingDatabase::class.java,
        ).allowMainThreadQueries().build()
        val next = AtomicInteger()
        repository = RoomHabitRepository(
            database = database,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            newId = { "habit-generated-${next.incrementAndGet()}" },
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun dailyMultiCountCyclesAndDeletesZeroRecord() = runTest {
        val habitId = createHabit(HabitPeriod.DAILY, targetCount = 2)

        assertEquals(1, repository.toggleCheckIn(habitId, today))
        assertEquals(2, repository.toggleCheckIn(habitId, today))
        assertEquals(0, repository.toggleCheckIn(habitId, today))

        assertNull(database.habitDao().dayRecord(habitId, today))
        assertEquals(0, repository.observeWeek(currentWeek).first().single().countOn(today))
    }

    @Test
    fun pastCheckInIsMarkedAsBackfilled() = runTest {
        val habitId = createHabit(
            period = HabitPeriod.DAILY,
            targetCount = 1,
            startDate = currentWeek,
        )
        val pastDate = today.minusDays(1)

        repository.toggleCheckIn(habitId, pastDate)

        assertTrue(database.habitDao().dayRecord(habitId, pastDate)?.isBackfilled == true)
    }

    @Test
    fun weeklyProgressCountsDistinctRealDaysOnly() = runTest {
        val habitId = createHabit(
            period = HabitPeriod.WEEKLY,
            targetCount = 4,
            startDate = currentWeek,
        )

        repository.toggleCheckIn(habitId, currentWeek)
        repository.toggleCheckIn(habitId, currentWeek.plusDays(1))
        assertEquals(2, repository.observeWeek(currentWeek).first().single().weeklyEffectiveDayCount)

        repository.toggleCheckIn(habitId, currentWeek)
        assertEquals(1, repository.observeWeek(currentWeek).first().single().weeklyEffectiveDayCount)
    }

    @Test
    fun skippedWeekStillAcceptsFactsAndCanBeRestored() = runTest {
        val habitId = createHabit(
            period = HabitPeriod.WEEKLY,
            targetCount = 4,
            startDate = currentWeek,
        )

        assertEquals(true, repository.toggleWeekSkip(habitId, currentWeek.plusDays(3)))
        assertTrue(repository.observeWeek(currentWeek).first().single().isSkipped)
        assertEquals(1, repository.toggleCheckIn(habitId, currentWeek.plusDays(1)))
        assertEquals(1, repository.observeWeek(currentWeek).first().single().weeklyEffectiveDayCount)

        assertEquals(false, repository.toggleWeekSkip(habitId, currentWeek))
        assertFalse(repository.observeWeek(currentWeek).first().single().isSkipped)
        assertEquals(1, repository.observeWeek(currentWeek).first().single().weeklyEffectiveDayCount)
    }

    @Test
    fun nextWeekEditCreatesVersionWithoutRecalculatingPastTarget() = runTest {
        val habitId = createHabit(
            period = HabitPeriod.DAILY,
            targetCount = 2,
            startDate = currentWeek,
        )
        val nextWeek = currentWeek.plusWeeks(1)

        repository.updateHabit(
            habitId = habitId,
            effectiveFromWeek = nextWeek.plusDays(2),
            title = "新刷牙",
            color = 0xFFED8FAE,
            period = HabitPeriod.WEEKLY,
            targetCount = 4,
        )

        val past = repository.observeWeek(currentWeek).first().single()
        val future = repository.observeWeek(nextWeek).first().single()
        assertEquals(HabitPeriod.DAILY, past.period)
        assertEquals(2, past.targetCount)
        assertEquals("刷牙", past.title)
        assertEquals(0xFF8FA7E4, past.color)
        // A Wednesday edit no longer rewrites Mon/Tue. Per-day lookup exposes the new rule.
        assertEquals(HabitPeriod.DAILY, future.ruleOn(nextWeek.plusDays(1)).period)
        assertEquals(HabitPeriod.WEEKLY, future.ruleOn(nextWeek.plusDays(2)).period)
        assertEquals(4, future.ruleOn(nextWeek.plusDays(2)).targetCount)
        assertEquals("新刷牙", future.ruleOn(nextWeek.plusDays(2)).title)
    }

    @Test
    fun endingFromWeekHidesCurrentAndFutureButKeepsEarlierWeek() = runTest {
        val previousWeek = currentWeek.minusWeeks(1)
        val habitId = createHabit(
            period = HabitPeriod.WEEKLY,
            targetCount = 3,
            startDate = previousWeek,
        )
        repository.toggleCheckIn(habitId, previousWeek.plusDays(2))

        repository.endHabitFromWeek(habitId, currentWeek.plusDays(4))

        assertEquals(1, repository.observeWeek(previousWeek).first().size)
        assertEquals(0, repository.observeWeek(currentWeek).first().size)
        assertEquals(0, repository.observeWeek(currentWeek.plusWeeks(1)).first().size)
        assertEquals(1, database.habitDao().dayRecord(habitId, previousWeek.plusDays(2))?.count)
    }

    @Test
    fun deletingHistoricalHabitHidesItEverywhereButKeepsRecoverableHistory() = runTest {
        val previousWeek = currentWeek.minusWeeks(2)
        val habitId = createHabit(HabitPeriod.DAILY, 1, previousWeek)
        repository.toggleCheckIn(habitId, previousWeek.plusDays(1))

        repository.deleteHabit(habitId)

        assertTrue(repository.observeTimeline(today).first().isEmpty())
        assertNotNull(database.habitDao().findHabit(habitId)?.deletedAt)
        assertEquals(1, database.habitDao().dayRecord(habitId, previousWeek.plusDays(1))?.count)
    }

    @Test
    fun timelineEndsAtCurrentWeekAndKeepsHistoricalVersionPresentation() = runTest {
        val twoWeeksAgo = currentWeek.minusWeeks(2)
        val habitId = createHabit(
            period = HabitPeriod.DAILY,
            targetCount = 2,
            startDate = twoWeeksAgo.plusDays(2),
        )
        repository.toggleCheckIn(habitId, twoWeeksAgo.plusDays(3))
        repository.updateHabit(
            habitId = habitId,
            effectiveFromWeek = currentWeek.minusWeeks(1),
            title = "新刷牙",
            color = 0xFFED8FAE,
            period = HabitPeriod.WEEKLY,
            targetCount = 4,
        )
        repository.toggleWeekSkip(habitId, currentWeek.minusWeeks(1))

        val timeline = repository.observeTimeline(today).first()

        assertEquals(
            listOf(twoWeeksAgo, currentWeek.minusWeeks(1), currentWeek),
            timeline.map { it.weekStart },
        )
        assertEquals(HabitPeriod.DAILY, timeline[0].items.single().period)
        assertEquals(2, timeline[0].items.single().targetCount)
        assertEquals("刷牙", timeline[0].items.single().title)
        assertEquals(0xFF8FA7E4, timeline[0].items.single().color)
        assertTrue(timeline[1].items.single().isSkipped)
        assertEquals(HabitPeriod.WEEKLY, timeline[2].items.single().period)
        assertEquals(4, timeline[2].items.single().targetCount)
        assertEquals("新刷牙", timeline[2].items.single().title)
        assertEquals(0xFFED8FAE, timeline[2].items.single().color)
        assertEquals(1, timeline[0].items.single().records.single().count)
    }

    @Test
    fun timelineDoesNotShowWeeksBeforeTheFirstHabit() = runTest {
        createHabit(
            period = HabitPeriod.WEEKLY,
            targetCount = 4,
            startDate = currentWeek.minusDays(1),
        )

        val timeline = repository.observeTimeline(today).first()

        assertEquals(listOf(currentWeek.minusWeeks(1), currentWeek), timeline.map { it.weekStart })
        assertEquals(1, timeline.first().items.size)
    }

    @Test
    fun explicitEarlyBackfillExtendsOnlyFirstVersionAndPreservesLaterRules() = runTest {
        val id = createHabit(HabitPeriod.DAILY, 2, currentWeek)
        repository.updateHabit(id, currentWeek.plusWeeks(1), "新版", 0xFF355C52, HabitPeriod.WEEKLY, 4)
        val past = currentWeek.minusMonths(2)
        val preview = repository.observeTimelineRange(today, past, currentWeek.plusWeeks(4)).first()
        assertTrue(preview.first().items.single().records.isEmpty())
        assertEquals(currentWeek, database.habitDao().findHabit(id)!!.startDate)
        assertEquals(1, repository.toggleCheckIn(id, past))
        assertEquals(past, database.habitDao().findHabit(id)!!.startDate)
        assertEquals(2, repository.observeWeek(HabitRules.weekStart(past)).first().single().targetCount)
        assertEquals(4, repository.observeWeek(currentWeek.plusWeeks(1)).first().single().targetCount)
        assertTrue(database.habitDao().dayRecord(id, past)!!.isBackfilled)
        assertNull(repository.toggleCheckIn(id, today.plusDays(1)))
    }

    @Test
    fun monthRangeProvidesPastAndFutureWeeksWithoutInventingFacts() = runTest {
        createHabit(HabitPeriod.WEEKLY, 2)
        val first = HabitRules.weekStart(today.withDayOfMonth(1))
        val last = HabitRules.weekStart(today.withDayOfMonth(today.lengthOfMonth()))
        val month = repository.observeTimelineRange(today, first, last).first()
        assertEquals(first, month.first().weekStart)
        assertEquals(last, month.last().weekStart)
        assertTrue(month.flatMap { it.items }.all { it.records.isEmpty() })
        val expanded = repository.observeTimelineRange(today, first.minusWeeks(4), last.plusWeeks(4)).first()
        assertEquals(month.size + 8, expanded.size)
    }

    @Test
    fun reorderedHabitsPersistAcrossWeekSnapshots() = runTest {
        val first = createHabit(HabitPeriod.DAILY, 1)
        val second = createHabit(HabitPeriod.WEEKLY, 2)
        val third = createHabit(HabitPeriod.DAILY, 3)

        repository.reorderHabits(listOf(third, first, second))

        assertEquals(listOf(third, first, second), repository.observeWeek(currentWeek).first().map { it.id })
        assertEquals(listOf(0L, 1_024L, 2_048L), listOf(third, first, second).map { id ->
            database.habitDao().findHabit(id)!!.position
        })
    }

    @Test
    fun scheduledWeeklyHabitAllowsOffPlanFactsAndEarlyFactConsumesFirstCandidate() = runTest {
        val id = repository.createHabit("训练 #健康", 0xFF8FA7E4, currentWeek, HabitPeriod.WEEKLY, 1, setOf(1, 3, 5))
        assertEquals(1, repository.toggleCheckIn(id, currentWeek.plusDays(1)))
        val item = repository.observeWeek(currentWeek).first().single()
        assertEquals(setOf(1, 3, 5), item.scheduleDays)
        assertFalse(item.isScheduledOn(currentWeek.plusDays(2))) // Tue fact consumed Wed slot.
        assertFalse(item.isScheduledOn(currentWeek.plusDays(4))) // target reached, no Fri nag.
    }

    @Test
    fun habitCreatedMidweekCanStillSkipItsCurrentWeek() = runTest {
        val id = createHabit(HabitPeriod.DAILY, 1, startDate = today)
        assertEquals(true, repository.toggleWeekSkip(id, currentWeek))
        assertTrue(repository.observeWeek(currentWeek).first().single().isSkipped)
    }

    @Test
    fun monthlyHabitUsesSelectedMonthDaysAcrossWeeks() = runTest {
        val start = LocalDate.of(2026, 9, 1)
        val id = repository.createHabit("交账单", 0xFFF3CB6C, start, HabitPeriod.MONTHLY, 1, setOf(4, 15))
        assertEquals(1, repository.toggleCheckIn(id, LocalDate.of(2026, 9, 4)))
        assertEquals(1, repository.toggleCheckIn(id, LocalDate.of(2026, 9, 3)))
        val item = repository.observeWeek(currentWeek).first().single()
        assertEquals(HabitPeriod.MONTHLY, item.period)
        assertEquals(setOf(4, 15), item.scheduleDays)
    }

    @Test
    fun fixedCadenceKeepsCalendarSlotsWhileDynamicCadenceReanchorsOnlyRealTodayFacts() = runTest {
        val start = LocalDate.of(2026, 9, 1)
        val fixed = repository.createHabitWithSchedule("固定", 1, start, HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, start)
        val dynamic = repository.createHabitWithSchedule("间隔", 2, start, HabitPeriod.AFTER_COMPLETION_N_DAYS, 1, emptySet(), 3, start)

        assertEquals(LocalDate.of(2026, 9, 1), repository.previewCheckIn(fixed, start)?.nextDueDate)
        assertEquals(1, repository.toggleCheckIn(fixed, start.plusDays(1))) // early consumes Sep 1 slot
        assertEquals(LocalDate.of(2026, 9, 4), repository.previewCheckIn(fixed, start.plusDays(1))?.nextDueDate)

        assertEquals(1, repository.toggleCheckIn(dynamic, today))
        assertEquals(today.plusDays(3), repository.previewCheckIn(dynamic, today)?.nextDueDate)
        assertEquals(1, repository.toggleCheckIn(dynamic, start.plusDays(1))) // historical backfill
        assertEquals(today.plusDays(3), repository.previewCheckIn(dynamic, today)?.nextDueDate)
        repository.setCheckInAffectsScheduleAnchor(dynamic, start.plusDays(1), true)
        assertEquals(today.plusDays(3), repository.previewCheckIn(dynamic, today)?.nextDueDate) // earlier anchor cannot replace latest
    }

    private suspend fun createHabit(
        period: HabitPeriod,
        targetCount: Int,
        startDate: LocalDate = today,
    ): String = repository.createHabit(
        title = "刷牙",
        color = 0xFF8FA7E4,
        startDate = startDate,
        period = period,
        targetCount = targetCount,
    )
}
