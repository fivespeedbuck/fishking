package com.fishking.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class LifeGoalWithEventsEntity(
    @Embedded val goal: LifeGoalEntity,
    @Relation(parentColumn = "id", entityColumn = "goalId")
    val events: List<LifeGoalEventEntity>,
)

data class LifeGoalJournalDateRow(
    val lifeGoalId: String,
    val entryDate: LocalDate,
)

@Dao
interface LifeGoalDao {
    @Transaction
    @Query("SELECT * FROM life_goals WHERE deletedAt IS NULL ORDER BY position ASC")
    fun observeActiveGoals(): Flow<List<LifeGoalWithEventsEntity>>

    @Query(
        """
        SELECT DISTINCT link.lifeGoalId AS lifeGoalId, journal.entryDate AS entryDate
        FROM journal_life_goal_cross_ref AS link
        JOIN journals AS journal ON journal.id = link.journalId
        JOIN life_goals AS goal ON goal.id = link.lifeGoalId
        WHERE goal.deletedAt IS NULL AND journal.deletedAt IS NULL
        ORDER BY journal.entryDate ASC
        """,
    )
    fun observeLinkedJournalDates(): Flow<List<LifeGoalJournalDateRow>>

    @Query("SELECT * FROM life_goals WHERE id = :id LIMIT 1")
    suspend fun findGoal(id: String): LifeGoalEntity?

    @Query("SELECT * FROM life_goals WHERE deletedAt IS NULL ORDER BY position ASC")
    suspend fun activeGoals(): List<LifeGoalEntity>

    @Query("SELECT MAX(position) FROM life_goals WHERE deletedAt IS NULL")
    suspend fun maximumGoalPosition(): Long?

    @Insert
    suspend fun insertGoal(goal: LifeGoalEntity)

    @Update
    suspend fun updateGoal(goal: LifeGoalEntity)

    @Query("DELETE FROM life_goals WHERE id = :id")
    suspend fun deleteGoal(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<LifeGoalEventEntity>): List<Long>

    @Query("SELECT * FROM life_goal_events WHERE goalId = :goalId ORDER BY position ASC")
    suspend fun eventsForGoal(goalId: String): List<LifeGoalEventEntity>

    @Query("SELECT MAX(position) FROM life_goal_events WHERE goalId = :goalId")
    suspend fun maximumEventPosition(goalId: String): Long?

    @Query("DELETE FROM life_goal_events WHERE id = :id")
    suspend fun deleteEvent(id: String): Int

    @Query("DELETE FROM life_goal_events WHERE id = :id AND source = 'MANUAL'")
    suspend fun deleteManualEvent(id: String): Int

    @Query("DELETE FROM life_goal_events WHERE source = 'TODO' AND sourceTodoOccurrenceId = :occurrenceId")
    suspend fun deleteAutoEventsForOccurrence(occurrenceId: String): Int
}
