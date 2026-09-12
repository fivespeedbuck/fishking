package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
        assertEquals(0xFFED8FAE, past.color)
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
    fun deletedHabitChildrenStayRecoverableButAreExcludedFromActiveProjectionFlows() = runTest {
        val previousWeek = currentWeek.minusWeeks(1)
        val habitId = createHabit(HabitPeriod.DAILY, 1, previousWeek)
        repository.toggleCheckIn(habitId, previousWeek.plusDays(1))
        repository.toggleWeekSkip(habitId, previousWeek)

        repository.deleteHabit(habitId)

        assertTrue(database.habitDao().observeAllVersions().first().isEmpty())
        assertTrue(database.habitDao().observeAllRecords().first().isEmpty())
        assertTrue(database.habitDao().observeAllSkips().first().isEmpty())
        assertNotNull(database.habitDao().firstVersion(habitId))
        assertNotNull(database.habitDao().dayRecord(habitId, previousWeek.plusDays(1)))
        assertTrue(database.habitDao().isWeekSkipped(habitId, previousWeek))
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
        assertEquals(0xFFED8FAE, timeline[0].items.single().color)
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
        val future = today.plusDays(1)
        assertEquals(1, repository.toggleCheckIn(id, future))
        assertEquals(1, database.habitDao().dayRecord(id, future)?.count)
        assertEquals(4, repository.observeWeek(HabitRules.weekStart(currentWeek.plusWeeks(1))).first().single().targetCount)
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
    fun visibleWeeksShareOneConvertedRecordHistoryPerHabit() = runTest {
        val previousWeek = currentWeek.minusWeeks(1)
        val habitId = createHabit(HabitPeriod.DAILY, 1, previousWeek)
        repository.toggleCheckIn(habitId, previousWeek.plusDays(1))
        repository.toggleCheckIn(habitId, today)

        val weeks = repository.observeTimelineRange(today, previousWeek, currentWeek).first()

        assertEquals(2, weeks.size)
        assertSame(weeks[0].items.single().records, weeks[1].items.single().records)
        assertEquals(2, weeks[0].items.single().records.size)
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
    fun fixedCadenceKeepsCalendarSlotsWhileDynamicCadenceReanchorsFromLatestRealFact() = runTest {
        val start = LocalDate.of(2026, 9, 1)
        val fixed = repository.createHabitWithSchedule("固定", 1, start, HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, start)
        val dynamic = repository.createHabitWithSchedule("间隔", 2, start, HabitPeriod.AFTER_COMPLETION_N_DAYS, 1, emptySet(), 3, start)

        assertEquals(LocalDate.of(2026, 9, 1), repository.previewCheckIn(fixed, start)?.nextDueDate)
        assertEquals(1, repository.toggleCheckIn(fixed, start.plusDays(1))) // early consumes Sep 1 slot
        assertEquals(LocalDate.of(2026, 9, 4), repository.previewCheckIn(fixed, start.plusDays(1))?.nextDueDate)

        assertEquals(1, repository.toggleCheckIn(dynamic, today))
        assertEquals(today.plusDays(3), repository.previewCheckIn(dynamic, today)?.nextDueDate)
        assertEquals(1, repository.toggleCheckIn(dynamic, start.plusDays(1))) // an older backfill cannot replace a later completion
        assertEquals(today.plusDays(3), repository.previewCheckIn(dynamic, today)?.nextDueDate)

        val backfillOnly = repository.createHabitWithSchedule("补记间隔", 3, start, HabitPeriod.AFTER_COMPLETION_N_DAYS, 1, emptySet(), 3, start)
        val yesterday = today.minusDays(1)
        assertEquals(1, repository.toggleCheckIn(backfillOnly, yesterday))
        assertEquals(yesterday.plusDays(3), repository.previewCheckIn(backfillOnly, today)?.nextDueDate)
        assertFalse(repository.previewCheckIn(backfillOnly, today)!!.isDue)
    }

    @Test
    fun intervalScheduleCanMoveFromFutureBackBeforeItsOriginalStart() = runTest {
        val original = LocalDate.of(2026, 9, 11)
        val future = LocalDate.of(2026, 9, 25)
        val earlier = LocalDate.of(2026, 9, 10)
        val id = repository.createHabitWithSchedule(
            "固定节奏", 1L, original, HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, original,
        )

        repository.updateHabitWithSchedule(
            id, original, "固定节奏", 1L, HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, future,
        )
        assertEquals(future, repository.observeWeek(HabitRules.weekStart(future)).first().single().ruleOn(future).scheduleStartDate)

        repository.updateHabitWithSchedule(
            id, earlier, "固定节奏", 1L, HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, earlier,
        )

        assertEquals(earlier, database.habitDao().findHabit(id)!!.startDate)
        assertEquals(earlier, repository.observeWeek(HabitRules.weekStart(earlier)).first().single().ruleOn(earlier).scheduleStartDate)
        assertTrue(repository.previewCheckIn(id, earlier)!!.isDue)
    }

    @Test
    fun savingVisibleCadenceReplacesAHiddenFutureCadence() = runTest {
        val start = LocalDate.of(2026, 9, 11)
        val hiddenFuture = LocalDate.of(2026, 9, 13)
        val hiddenFutureStart = LocalDate.of(2026, 9, 30)
        val id = repository.createHabitWithSchedule(
            "测试一下", 1L, start, HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, start,
        )
        repository.updateHabitWithSchedule(
            id, hiddenFuture, "测试一下", 1L,
            HabitPeriod.AFTER_COMPLETION_N_DAYS, 1, emptySet(), 3, hiddenFutureStart,
        )
        repository.updateHabitWithSchedule(
            id, LocalDate.of(2026, 10, 5), "测试一下", 1L,
            HabitPeriod.DAILY, 1, emptySet(), 1, LocalDate.of(2026, 10, 5),
        )

        // Saving the visible editor means "use this setting from here on".
        repository.updateHabitWithSchedule(
            id, start, "测试一下", 1L,
            HabitPeriod.EVERY_N_DAYS, 1, emptySet(), 3, start,
            replaceFutureSchedule = true,
        )

        val secondWeek = repository.observeWeek(LocalDate.of(2026, 9, 14)).first().single()
        assertEquals(HabitPeriod.EVERY_N_DAYS, secondWeek.ruleOn(LocalDate.of(2026, 9, 14)).period)
        assertEquals(start, secondWeek.ruleOn(LocalDate.of(2026, 9, 14)).scheduleStartDate)
        assertTrue(secondWeek.isScheduledOn(LocalDate.of(2026, 9, 14)))
        assertTrue(secondWeek.isScheduledOn(LocalDate.of(2026, 9, 17)))
        assertFalse(secondWeek.isScheduledOn(LocalDate.of(2026, 9, 15)))
        val october = repository.observeWeek(LocalDate.of(2026, 10, 5)).first().single()
        assertEquals(HabitPeriod.EVERY_N_DAYS, october.ruleOn(LocalDate.of(2026, 10, 5)).period)
        assertTrue(october.isScheduledOn(LocalDate.of(2026, 10, 5)))
        assertFalse(october.isScheduledOn(LocalDate.of(2026, 10, 6)))
        assertTrue(database.habitDao().versionsAfter(id, start).isEmpty())
    }

    @Test
    fun colourIsGlobalAcrossHistoryWhileTitlesRemainVersioned() = runTest {
        val oldColour = 0xFF8FA7E4L
        val newColour = 0xFFED8FAEL
        val start = currentWeek.minusWeeks(1)
        val id = repository.createHabit("旧标题", oldColour, start, HabitPeriod.DAILY, 1)
        val effective = currentWeek.plusDays(3)
        repository.updateHabitWithSchedule(id, effective, "新标题", newColour, HabitPeriod.DAILY, 1, emptySet(), 1, start)
        val history = repository.observeWeek(start).first().single()
        val editedWeek = repository.observeWeek(currentWeek).first().single()
        val future = repository.observeWeek(currentWeek.plusWeeks(1)).first().single()
        assertEquals(newColour, history.ruleOn(start.plusDays(6)).color)
        assertEquals("旧标题", history.ruleOn(start.plusDays(6)).title)
        for (day in 0L..6L) {
            val date = currentWeek.plusDays(day)
            assertEquals(newColour, editedWeek.ruleOn(date).color)
            assertEquals(if (date < effective) "旧标题" else "新标题", editedWeek.ruleOn(date).title)
        }
        assertEquals(newColour, future.color)
        assertEquals(newColour, future.ruleOn(currentWeek.plusWeeks(1)).color)
    }

    @Test
    fun earlierIdentityEditPropagatesAcrossFutureRulesWithoutChangingTheirSchedule() = runTest {
        val start = currentWeek.minusWeeks(1)
        val future = currentWeek.plusWeeks(1)
        val id = repository.createHabit("原色", 1L, start, HabitPeriod.DAILY, 1)
        repository.updateHabitWithSchedule(id, future, "未来色", 3L, HabitPeriod.WEEKLY, 3, setOf(1, 3, 5), 1, future)
        repository.updateHabitWithSchedule(id, currentWeek.plusDays(2), "本周色", 2L, HabitPeriod.DAILY, 1, emptySet(), 1, start)
        val week = repository.observeWeek(currentWeek).first().single()
        assertEquals(2L, week.ruleOn(currentWeek.plusDays(1)).color)
        assertEquals("原色", week.ruleOn(currentWeek.plusDays(1)).title)
        assertEquals(2L, week.ruleOn(currentWeek.plusDays(2)).color)
        assertEquals(future, database.habitDao().versionFor(id, currentWeek.plusDays(3))!!.effectiveUntilExclusive)
        val futureRule = repository.observeWeek(future).first().single().ruleOn(future)
        assertEquals(2L, futureRule.color)
        assertEquals("本周色", futureRule.title)
        assertEquals(HabitPeriod.WEEKLY, futureRule.period)
        assertEquals(3, futureRule.targetCount)
        assertEquals(setOf(1, 3, 5), futureRule.scheduleDays)
        assertEquals(future, futureRule.scheduleStartDate)
    }

    @Test
    fun anotherPagesExistingObserverReceivesGlobalColourWithoutChangingHistoricalFacts() = runTest {
        val start = currentWeek.minusWeeks(1)
        val id = repository.createHabit("旧标题", 1L, start, HabitPeriod.DAILY, 2)
        repository.toggleCheckIn(id, today.minusDays(1))
        val habitPage = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))
        // Room invalidation is delivered by a real executor. Keeping this
        // collector on runTest's virtual scheduler lets withTimeout advance
        // straight to 5 seconds before that executor can publish the update.
        val observer = async(Dispatchers.Default.limitedParallelism(1), start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                habitPage.observeTimeline(today).first { weeks ->
                    weeks.firstOrNull { it.weekStart == currentWeek }?.items?.singleOrNull()?.color == 2L
                }
            }
        }
        repository.updateHabitWithSchedule(id, today, "新标题", 2L, HabitPeriod.DAILY, 2, emptySet(), 1, start)
        val week = observer.await().first { it.weekStart == currentWeek }.items.single()
        assertEquals("新标题", week.title)
        assertEquals(2L, week.color)
        assertEquals(2L, week.ruleOn(today.minusDays(1)).color)
        assertEquals("旧标题", week.ruleOn(today.minusDays(1)).title)
        assertEquals(2L, week.ruleOn(today).color)
        assertEquals(2L, week.ruleOn(today.plusDays(1)).color)
        assertEquals(1, week.countOn(today.minusDays(1)))
    }

    @Test
    fun confirmingCurrentColourRepairsAStaleHistoricalVersion() = runTest {
        val start = currentWeek.minusWeeks(1)
        val id = repository.createHabit("统一颜色", 1L, start, HabitPeriod.DAILY, 1)
        repository.updateHabitWithSchedule(id, today, "统一颜色", 2L, HabitPeriod.DAILY, 1, emptySet(), 1, start)

        val historical = database.habitDao().firstVersion(id)!!
        database.habitDao().updateVersion(historical.copy(color = 1L))
        assertEquals(1L, repository.observeWeek(start).first().single().ruleOn(start).color)
        assertEquals(2L, repository.observeWeek(currentWeek).first().single().ruleOn(today).color)

        // The edited version already has colour 2. Older change-only logic
        // skipped the global update here and left the historical card stale.
        repository.updateHabitWithSchedule(id, today, "统一颜色", 2L, HabitPeriod.DAILY, 1, emptySet(), 1, start)

        assertEquals(2L, repository.observeWeek(start).first().single().ruleOn(start).color)
        assertEquals(2L, repository.observeWeek(currentWeek).first().single().ruleOn(today).color)
    }

    @Test
    fun scheduleOnlyEditDoesNotOverwriteAnExplicitFutureIdentity() = runTest {
        val start = currentWeek.minusWeeks(1)
        val future = currentWeek.plusWeeks(1)
        val id = repository.createHabit("原名", 1L, start, HabitPeriod.DAILY, 1)
        repository.updateHabitWithSchedule(id, future, "未来名", 3L, HabitPeriod.WEEKLY, 2, emptySet(), 1, future)
        repository.updateHabitWithSchedule(id, today, "原名", 3L, HabitPeriod.DAILY, 3, emptySet(), 1, start)
        val rule = repository.observeWeek(future).first().single().ruleOn(future)
        assertEquals("未来名", rule.title)
        assertEquals(3L, rule.color)
        assertEquals(2, rule.targetCount)
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
