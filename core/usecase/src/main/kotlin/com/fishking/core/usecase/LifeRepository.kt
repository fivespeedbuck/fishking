package com.fishking.core.usecase

import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface LifeRepository {
    fun observeGoals(): Flow<List<LifeGoalWithEvents>>

    suspend fun createGoal(
        title: String,
        note: String? = null,
        type: LifeGoalType = LifeGoalType.ONE_TIME,
    ): String

    suspend fun updateGoal(
        goalId: String,
        title: String,
        note: String?,
        type: LifeGoalType,
    )

    /** Appends CHECK after CROSS/empty, or CROSS after CHECK. Existing history is never overwritten. */
    suspend fun toggleManualResult(goalId: String, occurredOn: LocalDate): String?

    suspend fun deleteManualEvent(eventId: String)

    suspend fun setPosition(goalId: String, position: Long)

    /** Persists the complete visible order and keeps positions evenly spaced for later drag moves. */
    suspend fun reorderGoals(goalIds: List<String>)

    suspend fun deleteGoal(goalId: String)

    /** Creates and atomically links a one-time todo whose completion will append an automatic CHECK. */
    suspend fun addToDate(goalId: String, date: LocalDate): String?
}
