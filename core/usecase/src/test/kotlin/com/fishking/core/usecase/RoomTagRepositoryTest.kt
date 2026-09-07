package com.fishking.core.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.LifeGoalEntity
import com.fishking.core.model.LifeGoalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class RoomTagRepositoryTest {
    private lateinit var database: FishKingDatabase
    private lateinit var homeRepository: RoomHomeRepository
    private lateinit var repository: RoomTagRepository
    private val date = LocalDate.of(2026, 9, 4)
    private val now = Instant.parse("2026-09-04T07:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FishKingDatabase::class.java,
        ).allowMainThreadQueries().build()
        val next = AtomicInteger()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        homeRepository = RoomHomeRepository(database, clock) { "home-${next.incrementAndGet()}" }
        repository = RoomTagRepository(database, clock) { "tag-${next.incrementAndGet()}" }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun todoAndLifeGoalReuseNormalizedTagWithoutDuplicatingDisplayTag() = runTest {
        val todoId = homeRepository.createTodo("去旅行", date)
        insertGoal("goal-a")

        assertTrue(repository.setTodoTags(todoId, listOf("#旅行", "旅行")))
        assertTrue(repository.setLifeGoalTags("goal-a", listOf("旅行")))

        assertEquals(listOf("旅行"), repository.observeTodoTags(todoId).first().map { it.name })
        assertEquals(listOf("旅行"), repository.observeLifeGoalTags("goal-a").first().map { it.name })
        assertEquals(1, repository.observeAll().first().size)
        assertEquals(
            repository.observeTodoTags(todoId).first().single().id,
            repository.observeLifeGoalTags("goal-a").first().single().id,
        )
    }

    @Test
    fun englishTagDeduplicatesCaseInsensitively() = runTest {
        val todoId = homeRepository.createTodo("跑步", date)

        repository.setTodoTags(todoId, listOf("Health", "#health", "HEALTH"))

        assertEquals(listOf("Health"), repository.observeTodoTags(todoId).first().map { it.name })
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun settingTagsReplacesLinksButKeepsSharedTagCatalog() = runTest {
        val todoId = homeRepository.createTodo("跑步", date)
        repository.setTodoTags(todoId, listOf("健康", "成长"))

        repository.setTodoTags(todoId, listOf("成长"))

        assertEquals(listOf("成长"), repository.observeTodoTags(todoId).first().map { it.name })
        assertEquals(listOf("健康", "成长"), repository.observeAll().first().map { it.name })
    }

    @Test
    fun deletedTargetsCannotReceiveNewTagLinks() = runTest {
        val todoId = homeRepository.createTodo("删除我", date)
        homeRepository.deleteTodo(todoId)
        insertGoal("goal-a", deletedAt = now)

        assertFalse(repository.setTodoTags(todoId, listOf("无效")))
        assertFalse(repository.setLifeGoalTags("goal-a", listOf("无效")))
        assertEquals(0, repository.observeAll().first().size)
    }

    private suspend fun insertGoal(id: String, deletedAt: Instant? = null) {
        database.lifeGoalDao().insertGoal(
            LifeGoalEntity(
                id = id,
                title = id,
                note = null,
                type = LifeGoalType.ONE_TIME.name,
                position = 1_024L,
                deletedAt = deletedAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
