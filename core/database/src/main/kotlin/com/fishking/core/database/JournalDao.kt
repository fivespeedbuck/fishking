package com.fishking.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.Instant

data class JournalDocumentEntity(
    @Embedded val journal: JournalEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "journalId",
        entity = JournalBlockEntity::class,
    )
    val blocks: List<JournalBlockEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = JournalLifeGoalCrossRef::class,
            parentColumn = "journalId",
            entityColumn = "lifeGoalId",
        ),
    )
    val goals: List<LifeGoalEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = JournalTodoCrossRef::class,
            parentColumn = "journalId",
            entityColumn = "todoOccurrenceId",
        ),
    )
    val todos: List<TodoOccurrenceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = JournalTagCrossRef::class,
            parentColumn = "journalId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

@Dao
interface JournalDao {
    @Transaction
    @Query("SELECT * FROM journals WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeDocumentById(id: String): Flow<JournalDocumentEntity?>

    @Query("SELECT * FROM journals WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): JournalEntity?

    @Query("""
        SELECT j.id, j.entryDate, j.entryTime, j.locationName,
            COALESCE((SELECT text FROM journal_blocks WHERE journalId = j.id AND type = 'TITLE' LIMIT 1), '') AS title,
            COALESCE((SELECT SUBSTR(text, 1, 160) FROM journal_blocks WHERE journalId = j.id AND type = 'TEXT_LINE' AND TRIM(COALESCE(text, '')) != '' ORDER BY position LIMIT 1), '') AS excerpt,
            (SELECT COUNT(*) FROM journal_block_media_cross_ref m JOIN journal_blocks b ON b.id = m.blockId WHERE b.journalId = j.id) AS mediaCount,
            (SELECT COALESCE(NULLIF(a.previewPath, ''), a.privatePath)
                FROM media_assets a
                JOIN journal_block_media_cross_ref m ON m.assetId = a.id
                JOIN journal_blocks b ON b.id = m.blockId
                WHERE b.journalId = j.id AND a.mimeType LIKE 'image/%'
                ORDER BY b.position, m.position LIMIT 1 OFFSET 0) AS thumbnailPath1,
            (SELECT COALESCE(NULLIF(a.previewPath, ''), a.privatePath)
                FROM media_assets a
                JOIN journal_block_media_cross_ref m ON m.assetId = a.id
                JOIN journal_blocks b ON b.id = m.blockId
                WHERE b.journalId = j.id AND a.mimeType LIKE 'image/%'
                ORDER BY b.position, m.position LIMIT 1 OFFSET 1) AS thumbnailPath2,
            (SELECT COALESCE(NULLIF(a.previewPath, ''), a.privatePath)
                FROM media_assets a
                JOIN journal_block_media_cross_ref m ON m.assetId = a.id
                JOIN journal_blocks b ON b.id = m.blockId
                WHERE b.journalId = j.id AND a.mimeType LIKE 'image/%'
                ORDER BY b.position, m.position LIMIT 1 OFFSET 2) AS thumbnailPath3,
            COALESCE((SELECT GROUP_CONCAT(tag.name, ' ')
                FROM tags tag
                JOIN journal_tag_cross_ref link ON link.tagId = tag.id
                WHERE link.journalId = j.id), '') AS tagNames
        FROM journals j WHERE j.deletedAt IS NULL AND j.entryDate BETWEEN :from AND :through AND EXISTS (SELECT 1 FROM journal_blocks b WHERE b.journalId = j.id AND
            (TRIM(COALESCE(b.text, '')) != '' OR b.type NOT IN ('TEXT_LINE', 'TITLE')))
        ORDER BY j.entryDate DESC, j.entryTime ASC, j.createdAt ASC, j.id
    """)
    fun observeTimeline(from: LocalDate, through: LocalDate): Flow<List<JournalTimelineRow>>
    @Query("""
        SELECT entryDate FROM journals j WHERE j.deletedAt IS NULL AND EXISTS (
            SELECT 1 FROM journal_blocks b WHERE b.journalId = j.id AND
            (TRIM(COALESCE(b.text, '')) != '' OR b.type NOT IN ('TEXT_LINE', 'TITLE'))
        ) ORDER BY entryDate
    """)
    fun observeEntryDates(): Flow<List<LocalDate>>

    @Transaction
    @Query("SELECT * FROM journals WHERE entryDate = :date AND deletedAt IS NULL LIMIT 1")
    fun observeDocument(date: LocalDate): Flow<JournalDocumentEntity?>

    @Query("SELECT * FROM journals WHERE entryDate = :date AND deletedAt IS NULL LIMIT 1")
    suspend fun findByDate(date: LocalDate): JournalEntity?

    @Insert
    suspend fun insertJournal(journal: JournalEntity)

    @Update
    suspend fun updateJournal(journal: JournalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlocks(blocks: List<JournalBlockEntity>)

    @Query("SELECT * FROM journal_blocks WHERE id = :id LIMIT 1")
    suspend fun findBlock(id: String): JournalBlockEntity?

    @Query("SELECT * FROM journal_blocks WHERE journalId = :journalId ORDER BY position ASC")
    suspend fun blocksForJournal(journalId: String): List<JournalBlockEntity>

    @Query("DELETE FROM journal_blocks WHERE journalId = :journalId AND id NOT IN (:keptIds)")
    suspend fun deleteBlocksExcept(journalId: String, keptIds: List<String>): Int

    @Query("DELETE FROM journal_blocks WHERE journalId = :journalId")
    suspend fun deleteAllBlocks(journalId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkGoal(link: JournalLifeGoalCrossRef): Long

    @Query("DELETE FROM journal_life_goal_cross_ref WHERE journalId = :journalId AND lifeGoalId = :goalId")
    suspend fun unlinkGoal(journalId: String, goalId: String): Int

    @Query("DELETE FROM journal_life_goal_cross_ref WHERE journalId = :journalId")
    suspend fun unlinkAllGoals(journalId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTodo(link: JournalTodoCrossRef): Long

    @Query("DELETE FROM journal_todo_cross_ref WHERE journalId = :journalId AND todoOccurrenceId = :todoId")
    suspend fun unlinkTodo(journalId: String, todoId: String): Int

    @Query("DELETE FROM journal_todo_cross_ref WHERE journalId = :journalId")
    suspend fun unlinkAllTodos(journalId: String): Int

    @Insert
    suspend fun insertMediaAsset(asset: MediaAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkMedia(link: JournalBlockMediaCrossRef)

    @Query("DELETE FROM journal_block_media_cross_ref WHERE blockId = :blockId")
    suspend fun deleteMediaLinksForBlock(blockId: String): Int

    @Query(
        """
        SELECT asset.* FROM media_assets AS asset
        JOIN journal_block_media_cross_ref AS link ON link.assetId = asset.id
        WHERE link.blockId = :blockId
        ORDER BY link.position ASC
        """,
    )
    suspend fun mediaForBlock(blockId: String): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE id = :id LIMIT 1")
    suspend fun findMediaAsset(id: String): MediaAssetEntity?

    @Query("UPDATE media_assets SET pendingDeleteAt = NULL WHERE id = :id")
    suspend fun clearMediaPendingDelete(id: String): Int

    @Query(
        """
        UPDATE media_assets
        SET pendingDeleteAt = :pendingDeleteAt
        WHERE id = :id
          AND id NOT IN (SELECT assetId FROM journal_block_media_cross_ref)
          AND pendingDeleteAt IS NULL
        """,
    )
    suspend fun markMediaPendingDeleteIfOrphan(id: String, pendingDeleteAt: Instant): Int

    @Query(
        """
        DELETE FROM media_assets
        WHERE id = :id
          AND pendingDeleteAt IS NOT NULL
          AND id NOT IN (SELECT assetId FROM journal_block_media_cross_ref)
        """,
    )
    suspend fun deleteOrphanedMedia(id: String): Int

    @Query(
        """
        SELECT * FROM media_assets
        WHERE id NOT IN (SELECT assetId FROM journal_block_media_cross_ref)
          AND pendingDeleteAt IS NOT NULL
        """,
    )
    suspend fun orphanedMedia(): List<MediaAssetEntity>
}

data class JournalTimelineRow(
    val id: String,
    val entryDate: LocalDate,
    val entryTime: String?,
    val title: String,
    val excerpt: String,
    val mediaCount: Int,
    val locationName: String?,
    val thumbnailPath1: String?,
    val thumbnailPath2: String?,
    val thumbnailPath3: String?,
    val tagNames: String,
)
