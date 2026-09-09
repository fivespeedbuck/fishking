package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.HabitDayRecordEntity
import com.fishking.core.database.HabitEntity
import com.fishking.core.database.HabitVersionEntity
import com.fishking.core.database.HabitWeekSkipEntity
import com.fishking.core.database.toModel
import com.fishking.core.model.HabitDayState
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import com.fishking.core.model.HabitScheduleRules
import com.fishking.core.model.HabitVersion
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitWeekSnapshot
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class RoomHabitRepository(
    private val database: FishKingDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : HabitRepository {
    private val habitDao = database.habitDao()

    override suspend fun deleteHabit(id: String) {
        database.withTransaction {
        habitDao.findHabit(id)?.let { habitDao.updateHabit(it.copy(deletedAt = clock.instant())) }
        }
    }

    override fun observeWeek(weekStart: LocalDate) = HabitRules.weekStart(weekStart).let { week ->
        combine(habitDao.observeAllHabits(), habitDao.observeAllVersions(), habitDao.observeAllRecords(), habitDao.observeAllSkips()) { habits, versions, records, skips ->
            val end = week.plusDays(6)
            habits.filter { !it.startDate.isAfter(end) }
                .filter { habit -> habit.endedFromWeek?.isAfter(week) != false }
                .mapNotNull { itemForWeek(it, versions, records, skips, week) }
                .sortedBy(HabitWeekItem::position)
        }
    }

    override fun observeTimeline(currentDate: LocalDate) = timeline(currentDate, null)
    override fun observeTimelineFrom(currentDate: LocalDate, fromWeek: LocalDate) = timeline(currentDate, HabitRules.weekStart(fromWeek))
    override fun observeTimelineRange(currentDate: LocalDate, fromWeek: LocalDate, throughWeek: LocalDate) = timeline(currentDate, HabitRules.weekStart(fromWeek), HabitRules.weekStart(throughWeek))

    private fun timeline(currentDate: LocalDate, fromWeek: LocalDate?, throughWeek: LocalDate? = null) = combine(
        habitDao.observeAllHabits(), habitDao.observeAllVersions(), habitDao.observeAllRecords(), habitDao.observeAllSkips(),
    ) { habits, versions, records, skips ->
        val currentWeek = HabitRules.weekStart(currentDate)
        val active = habits.filter { !it.startDate.isAfter(currentDate) }
        val earliest = fromWeek ?: active.minOfOrNull { HabitRules.weekStart(it.startDate) } ?: return@combine emptyList()
        generateSequence(throughWeek ?: currentWeek) { it.minusWeeks(1).takeUnless { candidate -> candidate.isBefore(earliest) } }
            .toList().asReversed().map { week ->
                HabitWeekSnapshot(week, active.asSequence()
                    .filter { habit -> (fromWeek != null || !habit.startDate.isAfter(week.plusDays(6))) && habit.endedFromWeek?.isAfter(week) != false }
                    .mapNotNull { itemForWeek(it, versions, records, skips, week) }
                    .sortedBy(HabitWeekItem::position).toList())
            }
    }

    private fun itemForWeek(habit: HabitEntity, allVersions: List<HabitVersionEntity>, allRecords: List<HabitDayRecordEntity>, skips: List<HabitWeekSkipEntity>, week: LocalDate): HabitWeekItem? {
        val end = week.plusDays(6)
        val versions = allVersions.asSequence().filter { it.habitId == habit.id }.map { it.toModel() }.toList()
        val activeInWeek = versions.filter { version -> !version.effectiveFromDate.isAfter(end) && version.effectiveUntilExclusive?.isAfter(week) != false }
        // An explicit backfill range can intentionally include weeks before the first saved
        // version. Keep the initial rule visible there without changing normal date lookup.
        val intersecting = if (activeInWeek.isEmpty() && versions.minOfOrNull(HabitVersion::effectiveFromDate)?.let { week.isBefore(it) } == true) {
            listOf(versions.minByOrNull(HabitVersion::effectiveFromDate)!!)
        } else activeInWeek
        val summary = intersecting.firstOrNull { !it.effectiveFromDate.isAfter(week) } ?: intersecting.minByOrNull(HabitVersion::effectiveFromDate) ?: return null
        return HabitWeekItem(
            id = habit.id, title = summary.title, color = summary.color, startDate = habit.startDate, position = habit.position,
            weekStart = week, versionId = summary.id, period = summary.period, targetCount = summary.targetCount,
            scheduleDays = summary.scheduleDays, intervalDays = summary.intervalDays, scheduleStartDate = summary.scheduleStartDate,
            isSkipped = skips.any { it.habitId == habit.id && it.weekStart == week },
            records = allRecords.asSequence().filter { it.habitId == habit.id }.map { it.toModel() }.toList(), versions = intersecting,
        )
    }

    override suspend fun createHabit(title: String, color: Long, startDate: LocalDate, period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>): String =
        createHabitWithSchedule(title, color, startDate, period, targetCount, scheduleDays, 1, startDate)

    override suspend fun createHabitWithSchedule(title: String, color: Long, startDate: LocalDate, period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>, intervalDays: Int, scheduleStartDate: LocalDate): String {
        validate(title, period, targetCount, scheduleDays, intervalDays)
        val now = clock.instant(); val id = newId()
        database.withTransaction {
            habitDao.insertHabit(HabitEntity(id, title.trim(), color, startDate, null, (habitDao.maximumPosition() ?: 0) + POSITION_STEP, now, now))
            habitDao.insertVersion(versionEntity(newId(), id, startDate, null, title.trim(), color, period, targetCount, scheduleDays, intervalDays, scheduleStartDate, now))
        }
        return id
    }

    override suspend fun updateHabit(habitId: String, effectiveFromWeek: LocalDate, title: String, color: Long, period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>) =
        updateHabitWithSchedule(habitId, effectiveFromWeek, title, color, period, targetCount, scheduleDays, 1, effectiveFromWeek)

    override suspend fun updateHabitWithSchedule(habitId: String, effectiveFromDate: LocalDate, title: String, color: Long, period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>, intervalDays: Int, scheduleStartDate: LocalDate) {
        validate(title, period, targetCount, scheduleDays, intervalDays)
        database.withTransaction {
            val habit = habitDao.findHabit(habitId) ?: return@withTransaction
            if (effectiveFromDate.isBefore(habit.startDate) || (habit.endedFromWeek != null && !effectiveFromDate.isBefore(habit.endedFromWeek))) return@withTransaction
            val current = habitDao.versionFor(habitId, effectiveFromDate) ?: return@withTransaction
            val now = clock.instant(); habitDao.updateHabit(habit.copy(title = title.trim(), color = color, updatedAt = now))
            if (current.effectiveFromDate == effectiveFromDate) {
                habitDao.updateVersion(versionEntity(current.id, habitId, effectiveFromDate, current.effectiveUntilExclusive, title.trim(), color, period, targetCount, scheduleDays, intervalDays, scheduleStartDate, current.createdAt))
            } else {
                habitDao.updateVersion(current.copy(effectiveUntilExclusive = effectiveFromDate))
                habitDao.insertVersion(versionEntity(newId(), habitId, effectiveFromDate, null, title.trim(), color, period, targetCount, scheduleDays, intervalDays, scheduleStartDate, now))
            }
        }
    }

    override suspend fun previewCheckIn(habitId: String, date: LocalDate): HabitDayState? = database.withTransaction {
        val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
        if (habit.deletedAt != null || date.isAfter(LocalDate.now(clock))) return@withTransaction null
        val rule = habitDao.versionFor(habitId, date)?.toModel() ?: return@withTransaction null
        HabitScheduleRules.state(rule, habitDao.recordsForHabit(habitId).map { it.toModel() }, date)
    }

    override suspend fun toggleCheckIn(habitId: String, date: LocalDate): Int? = database.withTransaction {
        val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
        if (habit.deletedAt != null || date.isAfter(LocalDate.now(clock)) || (habit.endedFromWeek != null && !HabitRules.weekStart(date).isBefore(habit.endedFromWeek))) return@withTransaction null
        ensureHistoryStartsOn(habit, date)
        val version = habitDao.versionFor(habitId, date) ?: return@withTransaction null
        val current = habitDao.dayRecord(habitId, date)
        val next = if (HabitPeriod.valueOf(version.period) == HabitPeriod.DAILY) HabitRules.nextDailyCount(current?.count ?: 0, version.targetCount) else if (current == null) 1 else 0
        if (next == 0) habitDao.deleteDayRecord(habitId, date) else habitDao.upsertDayRecord(HabitDayRecordEntity(
            habitId, date, next, current?.isBackfilled == true || date.isBefore(LocalDate.now(clock)),
            current?.affectsScheduleAnchor ?: !date.isBefore(LocalDate.now(clock)), clock.instant(),
        ))
        next
    }

    override suspend fun setCheckInAffectsScheduleAnchor(habitId: String, date: LocalDate, affects: Boolean) {
        database.withTransaction {
            habitDao.dayRecord(habitId, date)?.let { habitDao.upsertDayRecord(it.copy(affectsScheduleAnchor = affects, updatedAt = clock.instant())) }
        }
    }

    private suspend fun ensureHistoryStartsOn(habit: HabitEntity, date: LocalDate) {
        if (!date.isBefore(habit.startDate)) return
        val first = habitDao.firstVersion(habit.id) ?: return
        habitDao.updateHabit(habit.copy(startDate = date, updatedAt = clock.instant()))
        if (date.isBefore(first.effectiveFromDate)) habitDao.updateVersion(first.copy(effectiveFromDate = date))
    }

    override suspend fun toggleWeekSkip(habitId: String, weekStart: LocalDate): Boolean? = database.withTransaction {
        val week = HabitRules.weekStart(weekStart); val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
        if (week.plusDays(6).isBefore(habit.startDate) ||
            (habit.endedFromWeek != null && !week.isBefore(habit.endedFromWeek)) ||
            habitDao.versionFor(habitId, week.plusDays(6)) == null
        ) return@withTransaction null
        val skipped = habitDao.isWeekSkipped(habitId, week)
        if (skipped) habitDao.restoreWeek(habitId, week) else habitDao.skipWeek(HabitWeekSkipEntity(habitId, week, clock.instant()))
        !skipped
    }

    override suspend fun endHabitFromWeek(habitId: String, weekStart: LocalDate) {
        database.withTransaction {
            val week = HabitRules.weekStart(weekStart)
            habitDao.findHabit(habitId)?.takeIf { !week.isBefore(HabitRules.weekStart(it.startDate)) }?.let {
                if (it.endedFromWeek?.let { existing -> week.isBefore(existing) } != false) habitDao.updateHabit(it.copy(endedFromWeek = week, updatedAt = clock.instant()))
            }
        }
    }

    override suspend fun reorderHabits(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return; require(orderedIds.distinct().size == orderedIds.size)
        database.withTransaction { val now = clock.instant(); orderedIds.forEachIndexed { index, id -> check(habitDao.updatePosition(id, index * POSITION_STEP, now) == 1) } }
    }

    private fun validate(title: String, period: HabitPeriod, target: Int, days: Set<Int>, interval: Int) {
        require(title.trim().isNotEmpty()); require(target > 0); require(interval > 0)
        if (period == HabitPeriod.EVERY_N_DAYS || period == HabitPeriod.AFTER_COMPLETION_N_DAYS) { require(target == 1); require(days.isEmpty()) }
    }

    private fun versionEntity(id: String, habitId: String, effective: LocalDate, until: LocalDate?, title: String, color: Long, period: HabitPeriod, target: Int, days: Set<Int>, interval: Int, start: LocalDate, created: java.time.Instant) =
        HabitVersionEntity(id, habitId, effective, until, title, color, period.name, target, days.sorted().joinToString(","), interval, start, created)

    private companion object { const val POSITION_STEP = 1_024L }
}
