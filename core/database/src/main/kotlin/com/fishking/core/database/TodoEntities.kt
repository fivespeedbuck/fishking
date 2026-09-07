package com.fishking.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "todo_series",
    indices = [Index("activeUntilExclusive")],
)
data class TodoSeriesEntity(
    @PrimaryKey val id: String,
    val activeUntilExclusive: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "todo_series_versions",
    foreignKeys = [
        ForeignKey(
            entity = TodoSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seriesId"),
        Index(value = ["seriesId", "effectiveFromNominalDate"], unique = true),
    ],
)
data class TodoSeriesVersionEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val effectiveFromNominalDate: LocalDate,
    val effectiveUntilExclusive: LocalDate?,
    val title: String,
    val recurrenceFrequency: String,
    val monthlyOverflowPolicy: String,
    val priority: String,
    val accentColor: Long?,
    val createdAt: Instant,
)

@Entity(
    tableName = "todo_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = TodoSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TodoSeriesVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesVersionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("seriesId"),
        Index("seriesVersionId"),
        Index(value = ["seriesId", "nominalDate"], unique = true),
        Index(value = ["displayDate", "deletedAt", "position"]),
    ],
)
data class TodoOccurrenceEntity(
    @PrimaryKey val id: String,
    val seriesId: String?,
    val seriesVersionId: String?,
    val nominalDate: LocalDate,
    val displayDate: LocalDate,
    val title: String,
    val priority: String,
    val accentColor: Long?,
    val status: String,
    val completedAt: Instant?,
    val position: Long,
    val isSeriesException: Boolean,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    @androidx.room.ColumnInfo(defaultValue = "'DATE'") val planScope: String = "DATE",
    val planDeadline: LocalDate? = null,
)

@Entity(
    tableName = "todo_reminders",
    foreignKeys = [
        ForeignKey(
            entity = TodoOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("occurrenceId"), Index(value = ["occurrenceId", "position"], unique = true)],
)
data class TodoReminderEntity(
    @PrimaryKey val id: String,
    val occurrenceId: String,
    val dayOffset: Int,
    val localTime: LocalTime,
    val position: Long,
    val isEnabled: Boolean,
    val createdAt: Instant,
)

@Entity(
    tableName = "todo_series_reminders",
    foreignKeys = [
        ForeignKey(
            entity = TodoSeriesVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seriesVersionId"),
        Index(value = ["seriesVersionId", "position"], unique = true),
    ],
)
data class TodoSeriesReminderEntity(
    @PrimaryKey val id: String,
    val seriesVersionId: String,
    val dayOffset: Int,
    val localTime: LocalTime,
    val position: Long,
    val isEnabled: Boolean,
    val createdAt: Instant,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Instant,
)

@Entity(
    tableName = "todo_tag_cross_ref",
    primaryKeys = ["occurrenceId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TodoOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
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
data class TodoTagCrossRef(
    val occurrenceId: String,
    val tagId: String,
)
