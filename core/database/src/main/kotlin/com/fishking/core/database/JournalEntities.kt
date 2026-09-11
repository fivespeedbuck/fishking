package com.fishking.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "journals",
    indices = [Index(value = ["entryDate"])],
)
data class JournalEntity(
    @androidx.room.PrimaryKey val id: String,
    val entryDate: LocalDate,
    val locationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val entryTime: String? = null,
    val deletedAt: Instant? = null,
)

@Entity(
    tableName = "journal_blocks",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["journalId", "position"], unique = true)],
)
data class JournalBlockEntity(
    @androidx.room.PrimaryKey val id: String,
    val journalId: String,
    val position: Long,
    val type: String,
    val text: String?,
    val textColor: Long?,
    val textSize: String?,
    val textStyleSpans: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    @androidx.room.ColumnInfo(defaultValue = "'LEFT'") val textAlignment: String = "LEFT",
    @androidx.room.ColumnInfo(defaultValue = "'NONE'") val listStyle: String = "NONE",
    @androidx.room.ColumnInfo(defaultValue = "0") val isChecked: Boolean = false,
)

@Entity(
    tableName = "media_assets",
    indices = [Index("checksum")],
)
data class MediaAssetEntity(
    @androidx.room.PrimaryKey val id: String,
    val privatePath: String,
    val previewPath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String?,
    val durationMillis: Long?,
    val pendingDeleteAt: Instant?,
    val createdAt: Instant,
)

@Entity(
    tableName = "journal_block_media_cross_ref",
    primaryKeys = ["blockId", "assetId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("assetId"), Index(value = ["blockId", "position"], unique = true)],
)
data class JournalBlockMediaCrossRef(
    val blockId: String,
    val assetId: String,
    val position: Int,
)

@Entity(
    tableName = "journal_life_goal_cross_ref",
    primaryKeys = ["journalId", "lifeGoalId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LifeGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["lifeGoalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lifeGoalId")],
)
data class JournalLifeGoalCrossRef(
    val journalId: String,
    val lifeGoalId: String,
)

@Entity(
    tableName = "journal_todo_cross_ref",
    primaryKeys = ["journalId", "todoOccurrenceId"],
    foreignKeys = [
        ForeignKey(entity = JournalEntity::class, parentColumns = ["id"], childColumns = ["journalId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TodoOccurrenceEntity::class, parentColumns = ["id"], childColumns = ["todoOccurrenceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("todoOccurrenceId")],
)
data class JournalTodoCrossRef(
    val journalId: String,
    val todoOccurrenceId: String,
)

@Entity(
    tableName = "journal_tag_cross_ref",
    primaryKeys = ["journalId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class JournalTagCrossRef(
    val journalId: String,
    val tagId: String,
)
