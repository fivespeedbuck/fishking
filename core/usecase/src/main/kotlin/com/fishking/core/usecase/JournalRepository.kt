package com.fishking.core.usecase

import com.fishking.core.model.JournalBlockDraft
import com.fishking.core.model.JournalBlockType
import com.fishking.core.model.JournalDocument
import com.fishking.core.model.JournalMediaAsset
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface JournalRepository {
    suspend fun deleteEntry(id: String) = Unit
    fun observeTimeline(): Flow<List<com.fishking.core.model.JournalTimelineItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeTimelineRange(from: LocalDate, through: LocalDate): Flow<List<com.fishking.core.model.JournalTimelineItem>> = observeTimeline()
    fun forEntry(id: String, time: java.time.LocalTime?): JournalRepository = this
    fun observeEntryDates(): Flow<Set<LocalDate>> = kotlinx.coroutines.flow.flowOf(emptySet())
    fun observeDocument(date: LocalDate): Flow<JournalDocument?>

    /** Replaces the ordered editor document and returns stable block ids in the same order. */
    suspend fun saveBlocks(date: LocalDate, blocks: List<JournalBlockDraft>): List<String>

    /** Commit document and explicitly removed canonical metadata together. */
    suspend fun saveBlocksRemovingComponents(
        date: LocalDate,
        blocks: List<JournalBlockDraft>,
        removedTypes: Set<JournalBlockType>,
    ): List<String> {
        check(removedTypes.isEmpty()) { "This repository must implement atomic component removal" }
        return saveBlocks(date, blocks)
    }

    suspend fun setLocation(
        date: LocalDate,
        locationName: String?,
        latitude: Double? = null,
        longitude: Double? = null,
    )

    suspend fun registerMedia(
        privatePath: String,
        previewPath: String?,
        mimeType: String,
        sizeBytes: Long,
        checksum: String? = null,
        durationMillis: Long? = null,
    ): JournalMediaAsset

    /** Marks a newly imported but unlinked asset for the same confirm-after-file-delete cleanup path. */
    suspend fun abandonUnlinkedMedia(assetId: String): Boolean

    suspend fun linkGoal(date: LocalDate, goalId: String): Boolean

    suspend fun unlinkGoal(date: LocalDate, goalId: String)

    suspend fun linkTodo(date: LocalDate, todoId: String): Boolean = false

    suspend fun unlinkTodo(date: LocalDate, todoId: String) = Unit

    suspend fun setTags(date: LocalDate, names: List<String>): Boolean = false

    /** Assets are returned only after their database links are gone; callers delete files, then confirm. */
    suspend fun pendingMediaCleanup(): List<JournalMediaAsset>

    suspend fun confirmMediaFileDeleted(assetId: String): Boolean
}
