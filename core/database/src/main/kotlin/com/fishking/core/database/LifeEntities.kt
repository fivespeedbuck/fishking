package com.fishking.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "life_goals",
    indices = [Index(value = ["deletedAt", "position"])],
)
data class LifeGoalEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val note: String?,
    val type: String,
    val position: Long,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "life_goal_events",
    foreignKeys = [
        ForeignKey(
            entity = LifeGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("goalId"),
        Index("occurredOn"),
        Index(value = ["goalId", "position"], unique = true),
        Index(value = ["goalId", "sourceTodoOccurrenceId"], unique = true),
    ],
)
data class LifeGoalEventEntity(
    @androidx.room.PrimaryKey val id: String,
    val goalId: String,
    val occurredOn: LocalDate,
    val result: String,
    val source: String,
    val sourceTodoOccurrenceId: String?,
    val position: Long,
    val createdAt: Instant,
)

@Entity(
    tableName = "todo_life_goal_cross_ref",
    primaryKeys = ["occurrenceId", "lifeGoalId"],
    foreignKeys = [
        ForeignKey(
            entity = TodoOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
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
data class TodoLifeGoalCrossRef(
    val occurrenceId: String,
    val lifeGoalId: String,
)

@Entity(
    tableName = "life_goal_tag_cross_ref",
    primaryKeys = ["lifeGoalId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LifeGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["lifeGoalId"],
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
data class LifeGoalTagCrossRef(
    val lifeGoalId: String,
    val tagId: String,
)
