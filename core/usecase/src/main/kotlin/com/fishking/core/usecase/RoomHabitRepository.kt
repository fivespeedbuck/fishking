package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.HabitDayRecordEntity
import com.fishking.core.database.HabitEntity
import com.fishking.core.database.HabitVersionEntity
import com.fishking.core.database.HabitWeekSkipEntity
import com.fishking.core.database.toModel
import com.fishking.core.model.HabitDayState
import com.fishking.core.model.HabitDayRecord
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import com.fishking.core.model.HabitScheduleRules
import com.fishking.core.model.HabitVersion
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitWeekSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
            val index = HabitProjectionIndex(versions, records, skips)
            val end = week.plusDays(6)
            habits.filter { !it.startDate.isAfter(end) }
                .filter { habit -> habit.endedFromWeek?.isAfter(week) != false }
                .mapNotNull { itemForWeek(it, index, week) }
                .sortedBy(HabitWeekItem::position)
        }.flowOn(Dispatchers.Default)
    }

    override fun observeTimeline(currentDate: LocalDate) = timeline(currentDate, null)
    override fun observeTimelineFrom(currentDate: LocalDate, fromWeek: LocalDate) = timeline(currentDate, HabitRules.weekStart(fromWeek))
    override fun observeTimelineRange(currentDate: LocalDate, fromWeek: LocalDate, throughWeek: LocalDate) = timeline(currentDate, HabitRules.weekStart(fromWeek), HabitRules.weekStart(throughWeek))

    private fun timeline(currentDate: LocalDate, fromWeek: LocalDate?, throughWeek: LocalDate? = null) = combine(
        habitDao.observeAllHabits(), habitDao.observeAllVersions(), habitDao.observeAllRecords(), habitDao.observeAllSkips(),
    ) { habits, versions, records, skips ->
        val index = HabitProjectionIndex(versions, records, skips)
        val currentWeek = HabitRules.weekStart(currentDate)
        val active = habits.filter { !it.startDate.isAfter(currentDate) }
        val earliest = fromWeek ?: active.minOfOrNull { HabitRules.weekStart(it.startDate) } ?: return@combine emptyList()
        generateSequence(throughWeek ?: currentWeek) { it.minusWeeks(1).takeUnless { candidate -> candidate.isBefore(earliest) } }
            .toList().asReversed().map { week ->
                HabitWeekSnapshot(week, active.asSequence()
                    .filter { habit -> (fromWeek != null || !habit.startDate.isAfter(week.plusDays(6))) && habit.endedFromWeek?.isAfter(week) != false }
                    .mapNotNull { itemForWeek(it, index, week, currentDate) }
                    .sortedBy(HabitWeekItem::position).toList())
            }
    }.flowOn(Dispatchers.Default)

    private fun itemForWeek(habit: HabitEntity, index: HabitProjectionIndex, week: LocalDate, referenceDate: LocalDate = week): HabitWeekItem? {
        val end = week.plusDays(6)
        val versions = index.versionsByHabit[habit.id].orEmpty()
        val activeInWeek = versions.filter { version -> !version.effectiveFromDate.isAfter(end) && version.effectiveUntilExclusive?.isAfter(week) != false }
        // An explicit backfill range can intentionally include weeks before the first saved
        // version. Keep the initial rule visible there without changing normal date lookup.
        val intersecting = if (activeInWeek.isEmpty() && versions.minOfOrNull(HabitVersion::effectiveFromDate)?.let { week.isBefore(it) } == true) {
            listOf(versions.minByOrNull(HabitVersion::effectiveFromDate)!!)
        } else activeInWeek
        // The current-week heading represents today, historical headings the
        // last day of that week, future headings the first day. Day circles
        // still use their own dated rule and never inherit this summary colour.
        val summaryDate = maxOf(week, minOf(referenceDate, end))
        val summary = intersecting.filter { !it.effectiveFromDate.isAfter(summaryDate) }
            .filter { it.effectiveUntilExclusive?.isAfter(summaryDate) != false }
            .maxByOrNull(HabitVersion::effectiveFromDate)
            ?: intersecting.minByOrNull(HabitVersion::effectiveFromDate) ?: return null
        return HabitWeekItem(
            id = habit.id, title = summary.title, color = summary.color, startDate = habit.startDate, position = habit.position,
            weekStart = week, versionId = summary.id, period = summary.period, targetCount = summary.targetCount,
            scheduleDays = summary.scheduleDays, intervalDays = summary.intervalDays, scheduleStartDate = summary.scheduleStartDate,
            isSkipped = HabitWeekKey(habit.id, week) in index.skippedWeeks,
            // The indexed immutable list is intentionally shared by every visible week for
            // this habit. Older code converted and copied the entire history once per week.
            records = index.recordsByHabit[habit.id].orEmpty(), versions = intersecting,
        )
    }

    private data class HabitWeekKey(val habitId: String, val weekStart: LocalDate)

    private data class HabitProjectionIndex(
        val versionsByHabit: Map<String, List<HabitVersion>>,
        val recordsByHabit: Map<String, List<HabitDayRecord>>,
        val skippedWeeks: Set<HabitWeekKey>,
    ) {
        constructor(
            versions: List<HabitVersionEntity>,
            records: List<HabitDayRecordEntity>,
            skips: List<HabitWeekSkipEntity>,
        ) : this(
            versionsByHabit = versions.groupBy(HabitVersionEntity::habitId)
                .mapValues { (_, values) -> values.map { it.toModel() } },
            recordsByHabit = records.groupBy(HabitDayRecordEntity::habitId)
                .mapValues { (_, values) -> values.map { it.toModel() } },
            skippedWeeks = skips.mapTo(HashSet(skips.size)) { HabitWeekKey(it.habitId, it.weekStart) },
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
        updateHabitWithSchedule(habitId, effectiveFromWeek, title, color, period, targetCount, scheduleDays, 1, effectiveFromWeek, replaceFutureSchedule = true)

    override suspend fun updateHabitWithSchedule(habitId: String, effectiveFromDate: LocalDate, title: String, color: Long, period: HabitPeriod, targetCount: Int, scheduleDays: Set<Int>, intervalDays: Int, scheduleStartDate: LocalDate, replaceFutureSchedule: Boolean) {
        validate(title, period, targetCount, scheduleDays, intervalDays)
        database.withTransaction {
            var habit = habitDao.findHabit(habitId) ?: return@withTransaction
            // Editing a habit from a later visible day back to an earlier day
            // is valid. Older code silently returned here when the requested
            // effective date preceded the original start, which made the
            // picker appear to accept the value but discard it on save.
            if (effectiveFromDate.isBefore(habit.startDate)) {
                ensureHistoryStartsOn(habit, effectiveFromDate)
                habit = habitDao.findHabit(habitId) ?: return@withTransaction
            }
            if (habit.endedFromWeek != null && !effectiveFromDate.isBefore(habit.endedFromWeek)) return@withTransaction
            val current = habitDao.versionFor(habitId, effectiveFromDate) ?: return@withTransaction
            val titleChanged = current.title != title.trim()
            val now = clock.instant(); habitDao.updateHabit(habit.copy(title = title.trim(), color = color, updatedAt = now))
            if (replaceFutureSchedule) habitDao.deleteVersionsAfter(habitId, effectiveFromDate)
            if (current.effectiveFromDate == effectiveFromDate) {
                habitDao.updateVersion(versionEntity(current.id, habitId, effectiveFromDate, if (replaceFutureSchedule) null else current.effectiveUntilExclusive, title.trim(), color, period, targetCount, scheduleDays, intervalDays, scheduleStartDate, current.createdAt))
            } else {
                habitDao.updateVersion(current.copy(effectiveUntilExclusive = effectiveFromDate))
                // Ordinary dated edits split only this interval. A confirmed
                // editor save can instead replace every later hidden schedule,
                // making the visible settings authoritative from this date on.
                habitDao.insertVersion(versionEntity(newId(), habitId, effectiveFromDate, if (replaceFutureSchedule) null else current.effectiveUntilExclusive, title.trim(), color, period, targetCount, scheduleDays, intervalDays, scheduleStartDate, now))
            }
            // Colour is the habit's global visual identity. Titles and schedule
            // semantics retain their dated versions; colour edits do not touch
            // historical facts, targets, period, title or cadence.
            // Always heal every dated projection. Older builds could leave one
            // version with a stale colour even when the version being edited
            // already matched the selected colour, so a change-only guard made
            // that inconsistency permanent across the home and habit pages.
            habitDao.recolorAllVersions(habitId, color)
            if (titleChanged && !replaceFutureSchedule) habitDao.versionsAfter(habitId, effectiveFromDate).forEach { future ->
                habitDao.updateVersion(future.copy(
                    title = title.trim(),
                ))
            }
        }
    }

    override suspend fun previewCheckIn(habitId: String, date: LocalDate): HabitDayState? = database.withTransaction {
        val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
        if (habit.deletedAt != null ||
            (habit.endedFromWeek != null && !HabitRules.weekStart(date).isBefore(habit.endedFromWeek))) return@withTransaction null
        val rule = habitDao.versionFor(habitId, date)?.toModel() ?: return@withTransaction null
        HabitScheduleRules.state(rule, habitDao.recordsForHabit(habitId).map { it.toModel() }, date)
    }

    override suspend fun toggleCheckIn(habitId: String, date: LocalDate): Int? = database.withTransaction {
        val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
        // The date being edited owns the record, including an explicitly selected
        // future day in the home week view. Never silently discard its gesture.
        if (habit.deletedAt != null || (habit.endedFromWeek != null && !HabitRules.weekStart(date).isBefore(habit.endedFromWeek))) return@withTransaction null
        ensureHistoryStartsOn(habit, date)
        val version = habitDao.versionFor(habitId, date) ?: return@withTransaction null
        val current = habitDao.dayRecord(habitId, date)
        val period = HabitPeriod.valueOf(version.period)
        val next = if (period == HabitPeriod.DAILY) HabitRules.nextDailyCount(current?.count ?: 0, version.targetCount) else if (current == null) 1 else 0
        if (next == 0) habitDao.deleteDayRecord(habitId, date) else habitDao.upsertDayRecord(HabitDayRecordEntity(
            habitId, date, next, current?.isBackfilled == true || date.isBefore(LocalDate.now(clock)),
            current?.affectsScheduleAnchor ?: (period == HabitPeriod.AFTER_COMPLETION_N_DAYS || !date.isBefore(LocalDate.now(clock))), clock.instant(),
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
