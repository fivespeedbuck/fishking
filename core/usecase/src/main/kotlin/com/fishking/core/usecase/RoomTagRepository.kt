package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.LifeGoalTagCrossRef
import com.fishking.core.database.TagEntity
import com.fishking.core.database.TodoTagCrossRef
import com.fishking.core.database.toModel
import com.fishking.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.Locale
import java.util.UUID

class RoomTagRepository(
    private val database: FishKingDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : TagRepository {
    private val tagDao = database.tagDao()

    override fun observeAll(): Flow<List<Tag>> = tagDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeTodoTags(occurrenceId: String): Flow<List<Tag>> =
        tagDao.observeForTodo(occurrenceId).map { rows -> rows.map { it.toModel() } }

    override fun observeLifeGoalTags(goalId: String): Flow<List<Tag>> =
        tagDao.observeForLifeGoal(goalId).map { rows -> rows.map { it.toModel() } }

    override suspend fun setTodoTags(occurrenceId: String, names: List<String>): Boolean =
        database.withTransaction {
            val occurrence = database.todoDao().findOccurrence(occurrenceId) ?: return@withTransaction false
            if (occurrence.deletedAt != null) return@withTransaction false
            val tags = ensureTags(names)
            tagDao.deleteTodoLinks(occurrenceId)
            tagDao.linkTodo(tags.map { TodoTagCrossRef(occurrenceId, it.id) })
            true
        }

    override suspend fun setLifeGoalTags(goalId: String, names: List<String>): Boolean =
        database.withTransaction {
            val goal = database.lifeGoalDao().findGoal(goalId) ?: return@withTransaction false
            if (goal.deletedAt != null) return@withTransaction false
            val tags = ensureTags(names)
            tagDao.deleteLifeGoalLinks(goalId)
            tagDao.linkLifeGoal(tags.map { LifeGoalTagCrossRef(goalId, it.id) })
            true
        }

    private suspend fun ensureTags(names: List<String>): List<TagEntity> {
        val cleaned = names.map(::cleanName).distinctBy { normalizeName(it) }
        return cleaned.map { name ->
            val normalized = normalizeName(name)
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

    private fun cleanName(value: String): String {
        val clean = value.trim().removePrefix("#").trim()
        require(clean.isNotEmpty()) { "Tag name cannot be blank" }
        require(clean.none(Char::isWhitespace)) { "Tag name cannot contain whitespace" }
        return clean
    }

    private fun normalizeName(value: String): String = value.lowercase(Locale.ROOT)
}
