package com.fishking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ActiveTodoReminder(
    val reminderId: String,
    val occurrenceId: String,
    val title: String,
    val displayDate: LocalDate,
    val dayOffset: Int,
    val localTime: LocalTime,
)

@Dao
interface TodoDao {
    @Query(
        """
        SELECT reminder.id AS reminderId,
               reminder.occurrenceId AS occurrenceId,
               occurrence.title AS title,
               occurrence.displayDate AS displayDate,
               reminder.dayOffset AS dayOffset,
               reminder.localTime AS localTime
        FROM todo_reminders AS reminder
        JOIN todo_occurrences AS occurrence ON occurrence.id = reminder.occurrenceId
        WHERE reminder.isEnabled = 1
          AND occurrence.deletedAt IS NULL
          AND occurrence.status = 'OPEN'
        ORDER BY occurrence.displayDate, reminder.localTime, reminder.position
        """,
    )
    fun observeActiveReminders(): Flow<List<ActiveTodoReminder>>

    @Query(
        """
        SELECT reminder.id AS reminderId,
               reminder.occurrenceId AS occurrenceId,
               occurrence.title AS title,
               occurrence.displayDate AS displayDate,
               reminder.dayOffset AS dayOffset,
               reminder.localTime AS localTime
        FROM todo_reminders AS reminder
        JOIN todo_occurrences AS occurrence ON occurrence.id = reminder.occurrenceId
        WHERE reminder.id = :reminderId
          AND reminder.isEnabled = 1
          AND occurrence.deletedAt IS NULL
          AND occurrence.status = 'OPEN'
        LIMIT 1
        """,
    )
    suspend fun activeReminder(reminderId: String): ActiveTodoReminder?

    @Query(
        """
        SELECT * FROM todo_occurrences
        WHERE deletedAt IS NULL AND ((planScope = 'DATE' AND displayDate = :date) OR
            (planScope != 'DATE' AND ((status = 'OPEN' AND displayDate <= :date) OR
                (status = 'COMPLETED' AND completedAt >= :dayStart AND completedAt < :dayEnd))))
        ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END,
                 CASE priority WHEN 'URGENT' THEN 0 ELSE 1 END,
                 position ASC
        """,
    )
    fun observeForDate(date: LocalDate,
        dayStart: Instant = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
        dayEnd: Instant = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()): Flow<List<TodoOccurrenceEntity>>

    @Query("SELECT DISTINCT occurrenceId FROM todo_life_goal_cross_ref")
    fun observeGoalTodoIds(): Flow<List<String>>

    @Query("SELECT * FROM todo_occurrences WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    fun observeCompleted(): Flow<List<TodoOccurrenceEntity>>

    @Query("SELECT * FROM todo_occurrences WHERE planScope != 'DATE' AND deletedAt IS NULL ORDER BY createdAt")
    fun observePlanned(): Flow<List<TodoOccurrenceEntity>>

    @Query("SELECT * FROM todo_occurrences WHERE seriesId IS NOT NULL AND deletedAt IS NULL AND displayDate BETWEEN :start AND :end ORDER BY displayDate, position")
    fun observeRecurringRange(start: LocalDate, end: LocalDate): Flow<List<TodoOccurrenceEntity>>

    @Query(
        """
        SELECT * FROM todo_occurrences
        WHERE displayDate = :date
          AND deletedAt IS NULL
          AND status = :status
          AND priority = :priority
        ORDER BY position ASC, createdAt ASC, id ASC
        """,
    )
    suspend fun occurrencesInGroup(
        date: LocalDate,
        status: String,
        priority: String,
    ): List<TodoOccurrenceEntity>

    @Query("SELECT * FROM todo_occurrences WHERE id = :id LIMIT 1")
    suspend fun findOccurrence(id: String): TodoOccurrenceEntity?

    @Query("SELECT * FROM todo_series_versions WHERE id = :id LIMIT 1")
    suspend fun findSeriesVersion(id: String): TodoSeriesVersionEntity?

    @Query("SELECT MIN(position) FROM todo_occurrences WHERE displayDate = :date AND deletedAt IS NULL")
    suspend fun minimumPosition(date: LocalDate): Long?

    @Insert
    suspend fun insertOccurrence(occurrence: TodoOccurrenceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(occurrences: List<TodoOccurrenceEntity>)

    @Query("SELECT * FROM todo_occurrences WHERE seriesId = :seriesId AND nominalDate = :nominalDate LIMIT 1")
    suspend fun findOccurrence(seriesId: String, nominalDate: LocalDate): TodoOccurrenceEntity?

    @Update
    suspend fun updateOccurrence(occurrence: TodoOccurrenceEntity)

    @Query(
        """
        UPDATE todo_occurrences
        SET position = :position, updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun updatePosition(id: String, position: Long, updatedAt: Instant): Int

    @Query(
        """
        UPDATE todo_occurrences
        SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun updateCompletion(
        id: String,
        status: String,
        completedAt: Instant?,
        updatedAt: Instant,
    ): Int

    @Query("UPDATE todo_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteOccurrence(id: String, deletedAt: Instant): Int

    @Insert
    suspend fun insertSeries(series: TodoSeriesEntity)

    @Query("SELECT * FROM todo_series WHERE id = :id LIMIT 1")
    suspend fun findSeries(id: String): TodoSeriesEntity?

    @Update
    suspend fun updateSeries(series: TodoSeriesEntity)

    @Insert
    suspend fun insertSeriesVersion(version: TodoSeriesVersionEntity)

    @Query("SELECT * FROM todo_series")
    suspend fun allSeries(): List<TodoSeriesEntity>

    @Query("SELECT * FROM todo_series_versions WHERE seriesId = :seriesId ORDER BY effectiveFromNominalDate ASC")
    suspend fun versionsForSeries(seriesId: String): List<TodoSeriesVersionEntity>

    @Query(
        """
        SELECT * FROM todo_series_versions
        WHERE seriesId = :seriesId
          AND effectiveFromNominalDate <= :nominalDate
          AND (effectiveUntilExclusive IS NULL OR effectiveUntilExclusive > :nominalDate)
        ORDER BY effectiveFromNominalDate DESC
        LIMIT 1
        """,
    )
    suspend fun versionFor(seriesId: String, nominalDate: LocalDate): TodoSeriesVersionEntity?

    @Insert
    suspend fun insertReminder(reminder: TodoReminderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReminders(reminders: List<TodoReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeriesReminders(reminders: List<TodoSeriesReminderEntity>)

    @Query("SELECT * FROM todo_series_reminders WHERE seriesVersionId = :seriesVersionId ORDER BY position")
    suspend fun seriesRemindersForVersion(seriesVersionId: String): List<TodoSeriesReminderEntity>

    @Query("SELECT * FROM todo_reminders WHERE occurrenceId = :occurrenceId ORDER BY position")
    fun observeReminders(occurrenceId: String): Flow<List<TodoReminderEntity>>

    @Query("SELECT r.* FROM todo_reminders r JOIN todo_occurrences t ON t.id = r.occurrenceId WHERE t.displayDate = :date AND t.deletedAt IS NULL ORDER BY r.position")
    fun observeRemindersForDate(date: LocalDate): Flow<List<TodoReminderEntity>>

    @Query("SELECT * FROM todo_reminders WHERE occurrenceId = :occurrenceId ORDER BY position")
    suspend fun remindersForOccurrence(occurrenceId: String): List<TodoReminderEntity>

    @Query("DELETE FROM todo_reminders WHERE id = :id")
    suspend fun deleteReminder(id: String): Int

    @Query("DELETE FROM todo_reminders WHERE occurrenceId = :occurrenceId")
    suspend fun deleteRemindersForOccurrence(occurrenceId: String): Int

    @Query(
        """
        UPDATE todo_occurrences
        SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE seriesId = :seriesId
          AND nominalDate >= :fromNominalDate
          AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSeriesOccurrencesFrom(
        seriesId: String,
        fromNominalDate: LocalDate,
        deletedAt: Instant,
    ): Int

    @Query(
        """
        UPDATE todo_occurrences
        SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE seriesId = :seriesId
          AND nominalDate > :afterNominalDate
          AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSeriesOccurrencesAfter(
        seriesId: String,
        afterNominalDate: LocalDate,
        deletedAt: Instant,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkGoal(link: TodoLifeGoalCrossRef): Long

    @Query("DELETE FROM todo_life_goal_cross_ref WHERE occurrenceId = :occurrenceId AND lifeGoalId = :goalId")
    suspend fun unlinkGoal(occurrenceId: String, goalId: String): Int

    @Query(
        """
        SELECT lifeGoalId FROM todo_life_goal_cross_ref AS link
        JOIN life_goals AS goal ON goal.id = link.lifeGoalId
        WHERE link.occurrenceId = :occurrenceId AND goal.deletedAt IS NULL
        """,
    )
    suspend fun activeLinkedGoalIds(occurrenceId: String): List<String>

    @Query(
        """
        SELECT lifeGoalId FROM todo_life_goal_cross_ref AS link
        JOIN life_goals AS goal ON goal.id = link.lifeGoalId
        WHERE link.occurrenceId = :occurrenceId AND goal.deletedAt IS NULL
        """,
    )
    fun observeActiveLinkedGoalIds(occurrenceId: String): Flow<List<String>>

    @Query(
        """
        SELECT link.lifeGoalId FROM todo_life_goal_cross_ref AS link
        JOIN life_goals AS goal ON goal.id = link.lifeGoalId
        WHERE link.occurrenceId = (
            SELECT occurrence.id FROM todo_occurrences AS occurrence
            WHERE occurrence.seriesId = :seriesId
            ORDER BY occurrence.nominalDate ASC LIMIT 1
        )
          AND goal.deletedAt IS NULL
        """,
    )
    suspend fun linkedGoalTemplateForSeries(seriesId: String): List<String>
}
