package com.fishking.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "habits",
    indices = [Index(value = ["endedFromWeek", "position"])],
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val color: Long,
    val startDate: LocalDate,
    val endedFromWeek: LocalDate?,
    val position: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Entity(
    tableName = "habit_versions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("habitId"),
        Index(value = ["habitId", "effectiveFromWeek"], unique = true),
    ],
)
data class HabitVersionEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val effectiveFromWeek: LocalDate,
    val effectiveUntilExclusive: LocalDate?,
    val title: String,
    val color: Long,
    val period: String,
    val targetCount: Int,
    /** Comma-separated ISO weekdays or month-day values; empty means the legacy flexible rule. */
    val scheduleDays: String = "",
    val createdAt: Instant,
)

@Entity(
    tableName = "habit_day_records",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
        ),
    ],
    indices = [Index("date")],
)
data class HabitDayRecordEntity(
    val habitId: String,
    val date: LocalDate,
    val count: Int,
    val isBackfilled: Boolean,
    val updatedAt: Instant,
)

@Entity(
    tableName = "habit_week_skips",
    primaryKeys = ["habitId", "weekStart"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
        ),
    ],
    indices = [Index("weekStart")],
)
data class HabitWeekSkipEntity(
    val habitId: String,
    val weekStart: LocalDate,
    val createdAt: Instant,
)
