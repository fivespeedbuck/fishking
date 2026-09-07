package com.fishking.core.ui

import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPlanScope
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaveTodoPlanningTest {
    private val today = LocalDate.of(2026, 9, 7)

    @Test
    fun openPlanShowsCountdownAndCompletedPlanDoesNot() {
        val todo = TodoOccurrence(
            id = "plan", nominalDate = today, displayDate = today, title = "计划",
            position = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            planScope = TodoPlanScope.MONTH, planDeadline = LocalDate.of(2026, 9, 30),
        )
        assertEquals("月待办", todoTypeTag(todo))
        assertEquals("剩余23天", todoDeadlineTag(todo, today))
        assertEquals("今天到期", todoDeadlineTag(todo.copy(planDeadline = today), today))
        assertEquals("已超期2天", todoDeadlineTag(todo.copy(planDeadline = today.minusDays(2)), today))
        assertNull(todoDeadlineTag(todo.copy(status = com.fishking.core.model.TodoStatus.COMPLETED), today))
    }

    @Test
    fun deadlinePlanUsesItsDateTag() {
        val deadline = LocalDate.of(2026, 10, 20)
        val todo = TodoOccurrence(
            id = "deadline", nominalDate = today, displayDate = today, title = "计划",
            position = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            planScope = TodoPlanScope.DEADLINE, planDeadline = deadline,
        )
        assertEquals("10月20日前待办", todoTypeTag(todo))
    }
}
