package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.JournalBlockEntity
import com.fishking.core.database.JournalBlockMediaCrossRef
import com.fishking.core.database.JournalEntity
import com.fishking.core.database.JournalLifeGoalCrossRef
import com.fishking.core.database.JournalTodoCrossRef
import com.fishking.core.database.JournalTagCrossRef
import com.fishking.core.database.MediaAssetEntity
import com.fishking.core.database.TagEntity
import com.fishking.core.model.JournalBlock
import com.fishking.core.model.JournalBlockDraft
import com.fishking.core.model.JournalBlockType
import com.fishking.core.model.JournalContentBlock
import com.fishking.core.model.JournalDocument
import com.fishking.core.model.JournalEntry
import com.fishking.core.model.JournalMediaAsset
import com.fishking.core.model.JournalTextSize
import com.fishking.core.model.JournalTextStyleSpan
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class RoomJournalRepository(
    private val database: FishKingDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val entryId: String? = null,
    private val entryTime: java.time.LocalTime? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : JournalRepository {
    private val journalDao = database.journalDao()
    private val goalDao = database.lifeGoalDao()
    private val todoDao = database.todoDao()
    private val tagDao = database.tagDao()
    override suspend fun deleteEntry(id: String) {
        database.withTransaction { journalDao.findById(id)?.let { journalDao.updateJournal(it.copy(deletedAt = clock.instant())) } }
    }
    override fun observeEntryDates() = journalDao.observeEntryDates().map { it.toSet() }
    override fun forEntry(id: String, time: java.time.LocalTime?): JournalRepository =
        RoomJournalRepository(database, clock, id, time, newId)
    override fun observeTimeline() = observeTimelineRange(LocalDate.MIN, LocalDate.MAX)
    override fun observeTimelineRange(from: LocalDate, through: LocalDate) = journalDao.observeTimeline(from, through).map { rows -> rows.map {
        com.fishking.core.model.JournalTimelineItem(
            id = it.id,
            date = it.entryDate,
            time = it.entryTime?.let(java.time.LocalTime::parse),
            title = it.title,
            excerpt = it.excerpt,
            mediaCount = it.mediaCount,
            location = it.locationName,
            thumbnailPaths = listOfNotNull(it.thumbnailPath1, it.thumbnailPath2, it.thumbnailPath3),
            tags = it.tagNames.split(' ').filter(String::isNotBlank),
        )
    } }

    override fun observeDocument(date: LocalDate) = (entryId?.let(journalDao::observeDocumentById) ?: journalDao.observeDocument(date)).map { document ->
        document?.let { row ->
            JournalDocument(
                entry = row.journal.toModel(),
                blocks = row.blocks.sortedBy { it.position }.map { block ->
                    JournalContentBlock(
                        block = block.toModel(),
                        media = journalDao.mediaForBlock(block.id).map { it.toModel() },
                    )
                },
                linkedGoalIds = row.goals.asSequence()
                    .filter { it.deletedAt == null }
                    .map { it.id }
                    .distinct()
                    .toList(),
                linkedTodoIds = row.todos.asSequence()
                    .filter { it.deletedAt == null }
                    .map { it.id }
                    .distinct()
                    .toList(),
                tags = row.tags.sortedBy { it.createdAt }.map { it.name },
            )
        }
    }

    override suspend fun saveBlocks(date: LocalDate, blocks: List<JournalBlockDraft>): List<String> {
        validateBlocks(blocks)
        return database.withTransaction {
            val journal = getOrCreateJournal(date)
            val now = clock.instant()
            val ids = blocks.map { it.id ?: newId() }
            require(ids.distinct().size == ids.size) { "Journal block ids must be unique" }
            val previousBlocks = journalDao.blocksForJournal(journal.id)
            val previousAssetIds = previousBlocks.flatMap { block ->
                journalDao.mediaForBlock(block.id).map { it.id }
            }.toSet()
            val existingById = ids.associateWith { id ->
                journalDao.findBlock(id)?.also { existing ->
                    require(existing.journalId == journal.id) {
                        "A journal block cannot be moved between journal dates"
                    }
                }
            }
            val entities = blocks.mapIndexed { index, draft ->
                JournalBlockEntity(
                    id = ids[index],
                    journalId = journal.id,
                    position = (index + 1L) * POSITION_STEP,
                    type = draft.type.name,
                    text = draft.text,
                    textColor = draft.textColor,
                    textSize = draft.textSize.name,
                    textStyleSpans = encodeTextStyleSpans(draft.textStyleSpans),
                    createdAt = existingById[ids[index]]?.createdAt ?: now,
                    updatedAt = now,
                )
            }
            if (entities.isEmpty()) {
                journalDao.deleteAllBlocks(journal.id)
            } else {
                journalDao.upsertBlocks(entities)
                entities.forEachIndexed { index, entity ->
                    journalDao.deleteMediaLinksForBlock(entity.id)
                    blocks[index].mediaAssetIds.distinct().forEachIndexed { mediaIndex, assetId ->
                        journalDao.clearMediaPendingDelete(assetId)
                        journalDao.linkMedia(
                            JournalBlockMediaCrossRef(
                                blockId = entity.id,
                                assetId = assetId,
                                position = mediaIndex,
                            ),
                        )
                    }
                }
                journalDao.deleteBlocksExcept(journal.id, ids)
            }
            val keptAssetIds = blocks.flatMap(JournalBlockDraft::mediaAssetIds).toSet()
            (previousAssetIds - keptAssetIds).forEach { assetId ->
                journalDao.markMediaPendingDeleteIfOrphan(assetId, now)
            }
            journalDao.updateJournal(journal.copy(updatedAt = now))
            ids
        }
    }

    override suspend fun setLocation(
        date: LocalDate,
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        require((latitude == null) == (longitude == null)) {
            "Latitude and longitude must be provided together"
        }
        latitude?.let { require(it in -90.0..90.0) { "Latitude is out of range" } }
        longitude?.let { require(it in -180.0..180.0) { "Longitude is out of range" } }
        database.withTransaction {
            val journal = getOrCreateJournal(date)
            journalDao.updateJournal(
                journal.copy(
                    locationName = locationName?.trim()?.takeIf(String::isNotEmpty),
                    latitude = latitude,
                    longitude = longitude,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun registerMedia(
        privatePath: String,
        previewPath: String?,
        mimeType: String,
        sizeBytes: Long,
        checksum: String?,
        durationMillis: Long?,
    ): JournalMediaAsset {
        require(privatePath.isNotBlank()) { "Media privatePath cannot be blank" }
        require(mimeType.isNotBlank()) { "Media mimeType cannot be blank" }
        require(sizeBytes >= 0L) { "Media sizeBytes cannot be negative" }
        require(durationMillis == null || durationMillis >= 0L) { "Media duration cannot be negative" }
        val id = newId()
        val entity = MediaAssetEntity(
                id = id,
                privatePath = privatePath,
                previewPath = previewPath,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksum = checksum,
                durationMillis = durationMillis,
                pendingDeleteAt = null,
                createdAt = clock.instant(),
            )
        journalDao.insertMediaAsset(entity)
        return entity.toModel()
    }

    override suspend fun abandonUnlinkedMedia(assetId: String): Boolean =
        journalDao.markMediaPendingDeleteIfOrphan(assetId, clock.instant()) > 0

    override suspend fun linkGoal(date: LocalDate, goalId: String): Boolean =
        database.withTransaction {
            val goal = goalDao.findGoal(goalId) ?: return@withTransaction false
            if (goal.deletedAt != null) return@withTransaction false
            val journal = getOrCreateJournal(date)
            journalDao.linkGoal(JournalLifeGoalCrossRef(journal.id, goalId))
            true
        }

    override suspend fun unlinkGoal(date: LocalDate, goalId: String) {
        database.withTransaction {
            val journal = if (entryId != null) journalDao.findById(entryId) else journalDao.findByDate(date)
            if (journal == null) return@withTransaction
            journalDao.unlinkGoal(journal.id, goalId)
        }
    }

    override suspend fun linkTodo(date: LocalDate, todoId: String): Boolean =
        database.withTransaction {
            val todo = todoDao.findOccurrence(todoId) ?: return@withTransaction false
            if (todo.deletedAt != null) return@withTransaction false
            val journal = getOrCreateJournal(date)
            journalDao.linkTodo(JournalTodoCrossRef(journal.id, todoId))
            true
        }

    override suspend fun unlinkTodo(date: LocalDate, todoId: String) {
        database.withTransaction {
            val journal = if (entryId != null) journalDao.findById(entryId) else journalDao.findByDate(date)
            if (journal == null) return@withTransaction
            journalDao.unlinkTodo(journal.id, todoId)
        }
    }

    override suspend fun setTags(date: LocalDate, names: List<String>): Boolean =
        database.withTransaction {
            val journal = getOrCreateJournal(date)
            val tags = ensureTags(names)
            tagDao.deleteJournalLinks(journal.id)
            tagDao.linkJournal(tags.map { JournalTagCrossRef(journal.id, it.id) })
            true
        }

    override suspend fun pendingMediaCleanup(): List<JournalMediaAsset> =
        journalDao.orphanedMedia().map { it.toModel() }

    override suspend fun confirmMediaFileDeleted(assetId: String): Boolean =
        journalDao.deleteOrphanedMedia(assetId) > 0

    private suspend fun getOrCreateJournal(date: LocalDate): JournalEntity {
        val existing = if (entryId != null) journalDao.findById(entryId) else journalDao.findByDate(date)
        existing?.let { require(it.deletedAt == null) { "Journal was deleted" }; require(it.entryDate == date) { "Journal entry date mismatch" }; return it }
        val now = clock.instant()
        return JournalEntity(
            id = entryId ?: newId(),
            entryDate = date,
            entryTime = entryTime?.toString(),
            locationName = null,
            latitude = null,
            longitude = null,
            createdAt = now,
            updatedAt = now,
        ).also { journalDao.insertJournal(it) }
    }

    private suspend fun ensureTags(names: List<String>): List<TagEntity> {
        val cleaned = names.map(::cleanTagName).distinctBy(::normalizeTagName)
        return cleaned.map { name ->
            val normalized = normalizeTagName(name)
            tagDao.findByNormalizedName(normalized) ?: TagEntity(
                id = newId(),
                name = name,
                normalizedName = normalized,
                createdAt = clock.instant(),
            ).also { candidate ->
                tagDao.insertTag(candidate)
            }.let { tagDao.findByNormalizedName(normalized) ?: it }
        }
    }

    private fun cleanTagName(value: String): String {
        val clean = value.trim().removePrefix("#").trim()
        require(clean.isNotEmpty()) { "Tag name cannot be blank" }
        require(clean.none(Char::isWhitespace)) { "Tag name cannot contain whitespace" }
        return clean
    }

    private fun normalizeTagName(value: String): String = value.lowercase(Locale.ROOT)

    private fun validateBlocks(blocks: List<JournalBlockDraft>) {
        require(blocks.count { it.type == JournalBlockType.TITLE } <= 1) { "A journal has at most one title" }
        blocks.forEach { block ->
            when (block.type) {
                JournalBlockType.TITLE, JournalBlockType.TEXT_LINE -> require(block.mediaAssetIds.isEmpty()) {
                    "Text lines cannot contain media assets"
                }.also {
                    validateTextStyleSpans(block.text.orEmpty(), block.textStyleSpans)
                }
                JournalBlockType.IMAGE, JournalBlockType.GIF -> require(block.mediaAssetIds.size in 1..3) {
                    "Image rows must contain one to three assets"
                }
                JournalBlockType.VIDEO, JournalBlockType.AUDIO -> require(block.mediaAssetIds.size == 1) {
                    "Video and audio blocks require exactly one asset"
                }
            }
        }
    }

    private fun JournalEntity.toModel() = JournalEntry(
        id = id,
        entryDate = entryDate,
        entryTime = entryTime?.let(java.time.LocalTime::parse),
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun JournalBlockEntity.toModel() = JournalBlock(
        id = id,
        journalId = journalId,
        position = position,
        type = JournalBlockType.valueOf(type),
        text = text,
        textColor = textColor,
        textSize = textSize?.let { runCatching { JournalTextSize.valueOf(it) }.getOrNull() }
            ?: JournalTextSize.BODY,
        textStyleSpans = decodeTextStyleSpans(textStyleSpans, text.orEmpty().length),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MediaAssetEntity.toModel() = JournalMediaAsset(
        id = id,
        privatePath = privatePath,
        previewPath = previewPath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        checksum = checksum,
        durationMillis = durationMillis,
        pendingDeleteAt = pendingDeleteAt,
        createdAt = createdAt,
    )

    private companion object {
        const val POSITION_STEP = 1_024L

        fun validateTextStyleSpans(text: String, spans: List<JournalTextStyleSpan>) {
            var previousEnd = 0
            spans.sortedBy(JournalTextStyleSpan::start).forEach { span ->
                require(span.start >= previousEnd) { "Journal text style spans must not overlap" }
                require(span.start >= 0 && span.endExclusive <= text.length && span.start < span.endExclusive) {
                    "Journal text style span is outside its text"
                }
                require(span.color != null || span.textSize != null) { "An empty journal text style span is invalid" }
                previousEnd = span.endExclusive
            }
        }

        fun encodeTextStyleSpans(spans: List<JournalTextStyleSpan>): String? =
            spans.takeIf(List<JournalTextStyleSpan>::isNotEmpty)?.joinToString(";") { span ->
                listOf(
                    span.start.toString(),
                    span.endExclusive.toString(),
                    span.color?.toString().orEmpty(),
                    span.textSize?.name.orEmpty(),
                ).joinToString(",")
            }

        fun decodeTextStyleSpans(value: String?, textLength: Int): List<JournalTextStyleSpan> {
            if (value.isNullOrBlank() || textLength == 0) return emptyList()
            val decoded = value.split(';').mapNotNull { encoded ->
                val parts = encoded.split(',', limit = 4)
                if (parts.size != 4) return@mapNotNull null
                val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                val color = parts[2].takeIf(String::isNotEmpty)?.toLongOrNull()
                val size = parts[3].takeIf(String::isNotEmpty)?.let {
                    runCatching { JournalTextSize.valueOf(it) }.getOrNull()
                }
                JournalTextStyleSpan(start, end, color, size)
            }.filter { span ->
                span.start >= 0 && span.start < span.endExclusive && span.endExclusive <= textLength &&
                    (span.color != null || span.textSize != null)
            }.sortedBy(JournalTextStyleSpan::start)

            var previousEnd = 0
            return decoded.filter { span ->
                val keep = span.start >= previousEnd
                if (keep) previousEnd = span.endExclusive
                keep
            }
        }
    }
}
