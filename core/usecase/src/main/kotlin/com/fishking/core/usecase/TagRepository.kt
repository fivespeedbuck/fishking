package com.fishking.core.usecase

import com.fishking.core.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>

    fun observeTodoTags(occurrenceId: String): Flow<List<Tag>>

    fun observeLifeGoalTags(goalId: String): Flow<List<Tag>>

    suspend fun setTodoTags(occurrenceId: String, names: List<String>): Boolean

    suspend fun setLifeGoalTags(goalId: String, names: List<String>): Boolean
}
