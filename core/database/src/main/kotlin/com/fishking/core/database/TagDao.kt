package com.fishking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query(
        """
        SELECT tag.* FROM tags AS tag
        JOIN todo_tag_cross_ref AS link ON link.tagId = tag.id
        WHERE link.occurrenceId = :occurrenceId
        ORDER BY tag.createdAt ASC
        """,
    )
    fun observeForTodo(occurrenceId: String): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tag.* FROM tags AS tag
        JOIN life_goal_tag_cross_ref AS link ON link.tagId = tag.id
        WHERE link.lifeGoalId = :goalId
        ORDER BY tag.createdAt ASC
        """,
    )
    fun observeForLifeGoal(goalId: String): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tag.* FROM tags AS tag
        JOIN journal_tag_cross_ref AS link ON link.tagId = tag.id
        WHERE link.journalId = :journalId
        ORDER BY tag.createdAt ASC
        """,
    )
    fun observeForJournal(journalId: String): Flow<List<TagEntity>>

    @Query("DELETE FROM todo_tag_cross_ref WHERE occurrenceId = :occurrenceId")
    suspend fun deleteTodoLinks(occurrenceId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTodo(links: List<TodoTagCrossRef>): List<Long>

    @Query("DELETE FROM life_goal_tag_cross_ref WHERE lifeGoalId = :goalId")
    suspend fun deleteLifeGoalLinks(goalId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkLifeGoal(links: List<LifeGoalTagCrossRef>): List<Long>

    @Query("DELETE FROM journal_tag_cross_ref WHERE journalId = :journalId")
    suspend fun deleteJournalLinks(journalId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkJournal(links: List<JournalTagCrossRef>): List<Long>
}
