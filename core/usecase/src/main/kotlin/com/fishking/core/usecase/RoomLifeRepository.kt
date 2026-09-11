package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.LifeGoalEntity
import com.fishking.core.database.LifeGoalEventEntity
import com.fishking.core.database.toModel
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class RoomLifeRepository(
    private val database: FishKingDatabase,
    private val homeRepository: HomeRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : LifeRepository {
    private val goalDao = database.lifeGoalDao()

    override fun observeGoals() = combine(
        goalDao.observeActiveGoals(),
        goalDao.observeLinkedJournalDates(),
    ) { rows, journalRows ->
        val journalDatesByGoal = journalRows.groupBy({ it.lifeGoalId }, { it.entryDate })
        rows.map { row ->
            LifeGoalWithEvents(
                goal = row.goal.toModel(),
                events = row.events.map { it.toModel() }.sortedBy { it.position },
                linkedJournalDates = journalDatesByGoal[row.goal.id].orEmpty().distinct().sorted(),
            )
        }
    }

    override suspend fun createGoal(title: String, note: String?, type: LifeGoalType): String {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Life goal title cannot be blank" }
        val now = clock.instant()
        val id = newId()
        goalDao.insertGoal(
            LifeGoalEntity(
                id = id,
                title = cleanTitle,
                note = note.cleanOptionalText(),
                type = type.name,
                position = (goalDao.maximumGoalPosition() ?: 0L) + POSITION_STEP,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    override suspend fun updateGoal(
        goalId: String,
        title: String,
        note: String?,
        type: LifeGoalType,
        accentColor: Long?,
    ) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Life goal title cannot be blank" }
        database.withTransaction {
            val goal = goalDao.findGoal(goalId) ?: return@withTransaction
            if (goal.deletedAt != null) return@withTransaction
            goalDao.updateGoal(
                goal.copy(
                    title = cleanTitle,
                    note = note.cleanOptionalText(),
                    type = type.name,
                    accentColor = accentColor,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun toggleManualResult(goalId: String, occurredOn: LocalDate): String? =
        database.withTransaction {
            val goal = goalDao.findGoal(goalId) ?: return@withTransaction null
            if (goal.deletedAt != null) return@withTransaction null
            val events = goalDao.eventsForGoal(goalId)
            val nextResult = if (events.maxByOrNull { it.position }?.result == LifeGoalResult.CHECK.name) {
                LifeGoalResult.CROSS
            } else {
                LifeGoalResult.CHECK
            }
            insertManualResult(goalId, occurredOn, nextResult)
        }

    override suspend fun deleteManualEvent(eventId: String) {
        goalDao.deleteManualEvent(eventId)
    }

    override suspend fun setPosition(goalId: String, position: Long) {
        database.withTransaction {
            val goal = goalDao.findGoal(goalId) ?: return@withTransaction
            if (goal.deletedAt != null) return@withTransaction
            goalDao.updateGoal(goal.copy(position = position, updatedAt = clock.instant()))
        }
    }

    override suspend fun reorderGoals(goalIds: List<String>) {
        database.withTransaction {
            val activeGoals = goalDao.activeGoals()
            val activeById = activeGoals.associateBy(LifeGoalEntity::id)
            val requested = goalIds.distinct().mapNotNull(activeById::get)
            val requestedIds = requested.mapTo(mutableSetOf(), LifeGoalEntity::id)
            val ordered = requested + activeGoals.filterNot { it.id in requestedIds }
            val now = clock.instant()
            ordered.forEachIndexed { index, goal ->
                val position = (index + 1L) * POSITION_STEP
                if (goal.position != position) {
                    goalDao.updateGoal(goal.copy(position = position, updatedAt = now))
                }
            }
        }
    }

    override suspend fun deleteGoal(goalId: String) {
        goalDao.deleteGoal(goalId)
    }

    override suspend fun addToDate(goalId: String, date: LocalDate): String? {
        return database.withTransaction {
            val goal = goalDao.findGoal(goalId) ?: return@withTransaction null
            if (goal.deletedAt != null) return@withTransaction null
            val todoId = homeRepository.createTodo(
                title = goal.title,
                date = date,
                linkedGoalIds = listOf(goalId),
            )
            if (goal.accentColor != null) homeRepository.setAccentColor(todoId, goal.accentColor)
            todoId
        }
    }

    private fun String?.cleanOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private suspend fun insertManualResult(
        goalId: String,
        occurredOn: LocalDate,
        result: LifeGoalResult,
    ): String {
        val eventId = newId()
        goalDao.insertEvents(
            listOf(
                LifeGoalEventEntity(
                    id = eventId,
                    goalId = goalId,
                    occurredOn = occurredOn,
                    result = result.name,
                    source = LifeGoalEventSource.MANUAL.name,
                    sourceTodoOccurrenceId = null,
                    position = (goalDao.maximumEventPosition(goalId) ?: 0L) + POSITION_STEP,
                    createdAt = clock.instant(),
                ),
            ),
        )
        return eventId
    }

    private companion object {
        const val POSITION_STEP = 1_024L
    }
}
