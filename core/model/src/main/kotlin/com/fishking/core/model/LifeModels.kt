package com.fishking.core.model

import java.time.Instant
import java.time.LocalDate

enum class LifeGoalType {
    ONE_TIME,
    ONGOING,
}

enum class LifeGoalResult {
    CHECK,
    CROSS,
}

enum class LifeGoalEventSource {
    MANUAL,
    TODO,
}

data class LifeGoal(
    val id: String,
    val title: String,
    val note: String? = null,
    val type: LifeGoalType = LifeGoalType.ONE_TIME,
    val position: Long,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LifeGoalEvent(
    val id: String,
    val goalId: String,
    val occurredOn: LocalDate,
    val result: LifeGoalResult,
    val source: LifeGoalEventSource,
    val sourceTodoOccurrenceId: String? = null,
    val position: Long,
    val createdAt: Instant,
)

data class LifeGoalWithEvents(
    val goal: LifeGoal,
    val events: List<LifeGoalEvent>,
    val linkedJournalDates: List<LocalDate> = emptyList(),
) {
    val currentResult: LifeGoalResult? get() = events.maxByOrNull { it.position }?.result
}
