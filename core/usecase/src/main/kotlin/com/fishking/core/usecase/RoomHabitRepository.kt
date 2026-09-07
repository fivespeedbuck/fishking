package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.HabitDayRecordEntity
import com.fishking.core.database.HabitEntity
import com.fishking.core.database.HabitVersionEntity
import com.fishking.core.database.HabitWeekSkipEntity
import com.fishking.core.database.toModel
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
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
        database.withTransaction { habitDao.findHabit(id)?.let { habitDao.updateHabit(it.copy(deletedAt = clock.instant())) } }
    }

    override fun observeWeek(weekStart: LocalDate) = HabitRules.weekStart(weekStart).let { normalizedWeek ->
        val weekEnd = normalizedWeek.plusDays(6)
        val recordFrom = normalizedWeek.withDayOfMonth(1)
        val recordThrough = weekEnd.withDayOfMonth(weekEnd.lengthOfMonth())
        combine(
            habitDao.observeForWeek(normalizedWeek, normalizedWeek.plusDays(6)),
            habitDao.observeRecords(recordFrom, recordThrough),
        ) { habits, records ->
            habits.map { row ->
                HabitWeekItem(
                    id = row.id,
                    title = row.title,
                    color = row.color,
                    startDate = row.startDate,
                    position = row.position,
                    weekStart = normalizedWeek,
                    versionId = row.versionId,
                    period = HabitPeriod.valueOf(row.period),
                    targetCount = row.targetCount,
                    scheduleDays = row.scheduleDays.split(',').mapNotNull(String::toIntOrNull).toSet(),
                    isSkipped = row.isSkipped,
                    records = records.filter { it.habitId == row.id }.map { it.toModel() },
                )
            }
        }
    }

    override fun observeTimeline(currentDate: LocalDate) = timeline(currentDate, null)

    override fun observeTimelineFrom(currentDate: LocalDate, fromWeek: LocalDate) = timeline(currentDate, HabitRules.weekStart(fromWeek))

    override fun observeTimelineRange(currentDate: LocalDate, fromWeek: LocalDate, throughWeek: LocalDate) =
        timeline(currentDate, HabitRules.weekStart(fromWeek), HabitRules.weekStart(throughWeek))

    private fun timeline(currentDate: LocalDate, fromWeek: LocalDate?, throughWeek: LocalDate? = null) = combine(
        habitDao.observeAllHabits(),
        habitDao.observeAllVersions(),
        habitDao.observeAllRecords(),
        habitDao.observeAllSkips(),
    ) { habits, versions, records, skips ->
        val currentWeek = HabitRules.weekStart(currentDate)
        val eligibleHabits = habits.filter { !it.startDate.isAfter(currentDate) }
        val earliestRealWeek = eligibleHabits.minOfOrNull { HabitRules.weekStart(it.startDate) }
        if (fromWeek == null && earliestRealWeek == null) return@combine emptyList()
        // Explicit range observers also back the continuous UI and recurring-todo
        // projection. Keep their empty weeks visible even before the first habit exists.
        val earliestWeek = fromWeek ?: earliestRealWeek ?: currentWeek
        generateSequence(throughWeek ?: currentWeek) { week ->
            week.minusWeeks(1).takeUnless { it.isBefore(earliestWeek) }
        }.toList().asReversed().map { weekStart ->
            val weekEnd = weekStart.plusDays(6)
            HabitWeekSnapshot(
                weekStart = weekStart,
                items = eligibleHabits.asSequence()
                    .filter { habit ->
                        val endedFromWeek = habit.endedFromWeek
                        (fromWeek != null || !habit.startDate.isAfter(weekEnd)) &&
                            (endedFromWeek == null || endedFromWeek.isAfter(weekStart))
                    }
                    .mapNotNull { habit ->
                        val version = versions.asSequence()
                            .filter { it.habitId == habit.id }
                            .filter { !it.effectiveFromWeek.isAfter(weekStart) }
                            .filter {
                                val effectiveUntil = it.effectiveUntilExclusive
                                effectiveUntil == null || effectiveUntil.isAfter(weekStart)
                            }
                            .maxByOrNull { it.effectiveFromWeek }
                            ?: versions.filter { it.habitId == habit.id }.minByOrNull { it.effectiveFromWeek }
                                ?.takeIf { fromWeek != null && weekStart.isBefore(it.effectiveFromWeek) }
                            ?: return@mapNotNull null
                        HabitWeekItem(
                            id = habit.id,
                            title = version.title,
                            color = version.color,
                            startDate = habit.startDate,
                            position = habit.position,
                            weekStart = weekStart,
                            versionId = version.id,
                            period = HabitPeriod.valueOf(version.period),
                            targetCount = version.targetCount,
                            scheduleDays = version.scheduleDays.split(',').mapNotNull(String::toIntOrNull).toSet(),
                            isSkipped = skips.any { it.habitId == habit.id && it.weekStart == weekStart },
                            records = records.asSequence()
                                .filter { it.habitId == habit.id }
                                .filter {
                                    if (HabitPeriod.valueOf(version.period) == HabitPeriod.MONTHLY) {
                                        val firstMonthStart = weekStart.withDayOfMonth(1)
                                        val lastMonthEnd = weekEnd.withDayOfMonth(weekEnd.lengthOfMonth())
                                        !it.date.isBefore(firstMonthStart) && !it.date.isAfter(lastMonthEnd)
                                    } else {
                                        !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd)
                                    }
                                }
                                .map { it.toModel() }
                                .toList(),
                        )
                    }
                    .sortedBy(HabitWeekItem::position)
                    .toList(),
            )
        }.toList()
    }

    override suspend fun createHabit(
        title: String,
        color: Long,
        startDate: LocalDate,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int>,
    ): String {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Habit title cannot be blank" }
        require(targetCount > 0) { "Habit targetCount must be positive" }
        val now = clock.instant()
        val habitId = newId()
        database.withTransaction {
            habitDao.insertHabit(
                HabitEntity(
                    id = habitId,
                    title = cleanTitle,
                    color = color,
                    startDate = startDate,
                    endedFromWeek = null,
                    position = (habitDao.maximumPosition() ?: 0L) + POSITION_STEP,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            habitDao.insertVersion(
                HabitVersionEntity(
                    id = newId(),
                    habitId = habitId,
                    effectiveFromWeek = HabitRules.weekStart(startDate),
                    effectiveUntilExclusive = null,
                    title = cleanTitle,
                    color = color,
                    period = period.name,
                    targetCount = targetCount,
                    scheduleDays = scheduleDays.sorted().joinToString(","),
                    createdAt = now,
                ),
            )
        }
        return habitId
    }

    override suspend fun updateHabit(
        habitId: String,
        effectiveFromWeek: LocalDate,
        title: String,
        color: Long,
        period: HabitPeriod,
        targetCount: Int,
        scheduleDays: Set<Int>,
    ) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Habit title cannot be blank" }
        require(targetCount > 0) { "Habit targetCount must be positive" }
        val normalizedWeek = HabitRules.weekStart(effectiveFromWeek)
        database.withTransaction {
            val habit = habitDao.findHabit(habitId) ?: return@withTransaction
            if (normalizedWeek.isBefore(HabitRules.weekStart(habit.startDate))) return@withTransaction
            if (habit.endedFromWeek != null && !normalizedWeek.isBefore(habit.endedFromWeek)) {
                return@withTransaction
            }
            val current = habitDao.versionFor(habitId, normalizedWeek) ?: return@withTransaction
            val now = clock.instant()
            habitDao.updateHabit(habit.copy(title = cleanTitle, color = color, updatedAt = now))
            if (current.effectiveFromWeek == normalizedWeek) {
                habitDao.updateVersion(
                    current.copy(
                        title = cleanTitle,
                        color = color,
                        period = period.name,
                        targetCount = targetCount,
                        scheduleDays = scheduleDays.sorted().joinToString(","),
                    ),
                )
            } else {
                habitDao.updateVersion(current.copy(effectiveUntilExclusive = normalizedWeek))
                habitDao.insertVersion(
                    HabitVersionEntity(
                        id = newId(),
                        habitId = habitId,
                        effectiveFromWeek = normalizedWeek,
                        effectiveUntilExclusive = null,
                        title = cleanTitle,
                        color = color,
                        period = period.name,
                        targetCount = targetCount,
                        scheduleDays = scheduleDays.sorted().joinToString(","),
                        createdAt = now,
                    ),
                )
            }
        }
    }

    override suspend fun toggleCheckIn(habitId: String, date: LocalDate): Int? =
        database.withTransaction {
            val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
            if (habit.deletedAt != null) return@withTransaction null
            val weekStart = HabitRules.weekStart(date)
            if (date.isAfter(LocalDate.now(clock))) return@withTransaction null
            if (habit.endedFromWeek != null && !weekStart.isBefore(habit.endedFromWeek)) {
                return@withTransaction null
            }
            if (date.isBefore(habit.startDate)) {
                val firstVersion = habitDao.firstVersion(habitId) ?: return@withTransaction null
                habitDao.updateHabit(habit.copy(startDate = date, updatedAt = clock.instant()))
                if (weekStart.isBefore(firstVersion.effectiveFromWeek)) {
                    habitDao.updateVersion(firstVersion.copy(effectiveFromWeek = weekStart))
                }
            }
            val version = habitDao.versionFor(habitId, weekStart) ?: return@withTransaction null
            val current = habitDao.dayRecord(habitId, date)
            val period = HabitPeriod.valueOf(version.period)
            val schedule = version.scheduleDays.split(',').mapNotNull(String::toIntOrNull).toSet()
            val scheduled = when (period) {
                HabitPeriod.DAILY -> true
                HabitPeriod.WEEKLY -> schedule.isEmpty() || date.dayOfWeek.value in schedule
                HabitPeriod.MONTHLY -> schedule.isEmpty() || date.dayOfMonth in schedule
            }
            if (!scheduled) return@withTransaction null
            val nextCount = when (period) {
                HabitPeriod.DAILY -> HabitRules.nextDailyCount(current?.count ?: 0, version.targetCount)
                HabitPeriod.WEEKLY -> if ((current?.count ?: 0) > 0) 0 else 1
                HabitPeriod.MONTHLY -> if ((current?.count ?: 0) > 0) 0 else 1
            }
            if (nextCount == 0) {
                habitDao.deleteDayRecord(habitId, date)
            } else {
                habitDao.upsertDayRecord(
                    HabitDayRecordEntity(
                        habitId = habitId,
                        date = date,
                        count = nextCount,
                        isBackfilled = current?.isBackfilled == true || date.isBefore(LocalDate.now(clock)),
                        updatedAt = clock.instant(),
                    ),
                )
            }
            nextCount
        }

    override suspend fun toggleWeekSkip(habitId: String, weekStart: LocalDate): Boolean? =
        database.withTransaction {
            val normalizedWeek = HabitRules.weekStart(weekStart)
            val habit = habitDao.findHabit(habitId) ?: return@withTransaction null
            if (normalizedWeek.plusDays(6).isBefore(habit.startDate)) return@withTransaction null
            if (habit.endedFromWeek != null && !normalizedWeek.isBefore(habit.endedFromWeek)) {
                return@withTransaction null
            }
            if (habitDao.versionFor(habitId, normalizedWeek) == null) return@withTransaction null
            val wasSkipped = habitDao.isWeekSkipped(habitId, normalizedWeek)
            if (wasSkipped) {
                habitDao.restoreWeek(habitId, normalizedWeek)
            } else {
                habitDao.skipWeek(
                    HabitWeekSkipEntity(
                        habitId = habitId,
                        weekStart = normalizedWeek,
                        createdAt = clock.instant(),
                    ),
                )
            }
            !wasSkipped
        }

    override suspend fun endHabitFromWeek(habitId: String, weekStart: LocalDate) {
        val normalizedWeek = HabitRules.weekStart(weekStart)
        database.withTransaction {
            val habit = habitDao.findHabit(habitId) ?: return@withTransaction
            if (normalizedWeek.isBefore(HabitRules.weekStart(habit.startDate))) return@withTransaction
            if (habit.endedFromWeek != null && !normalizedWeek.isBefore(habit.endedFromWeek)) {
                return@withTransaction
            }
            habitDao.updateHabit(
                habit.copy(endedFromWeek = normalizedWeek, updatedAt = clock.instant()),
            )
        }
    }

    override suspend fun reorderHabits(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        require(orderedIds.distinct().size == orderedIds.size) { "Habit order cannot contain duplicate ids" }
        database.withTransaction {
            val now = clock.instant()
            orderedIds.forEachIndexed { index, id ->
                check(habitDao.updatePosition(id, index.toLong() * POSITION_STEP, now) == 1) {
                    "Habit $id disappeared while reordering"
                }
            }
        }
    }

    private companion object {
        const val POSITION_STEP = 1_024L
    }
}
