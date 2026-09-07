package com.fishking.core.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodoPlanningRulesTest {
    @Test
    fun calendarRangesKeepTheOriginalPeriodEndInsteadOfRollingForward() {
        val anchor = LocalDate.of(2026, 8, 31)
        assertNull(TodoPlanningRules.endDate(TodoPlanScope.DATE, anchor))
        assertEquals(LocalDate.of(2026, 9, 6), TodoPlanningRules.endDate(TodoPlanScope.WEEK, anchor))
        assertEquals(LocalDate.of(2026, 8, 31), TodoPlanningRules.endDate(TodoPlanScope.MONTH, anchor))
        assertEquals(LocalDate.of(2026, 9, 30), TodoPlanningRules.endDate(TodoPlanScope.THREE_MONTHS, anchor))
        assertEquals(LocalDate.of(2026, 12, 31), TodoPlanningRules.endDate(TodoPlanScope.HALF_YEAR, anchor))
        assertEquals(LocalDate.of(2026, 12, 31), TodoPlanningRules.endDate(TodoPlanScope.YEAR, anchor))
    }

    @Test
    fun explicitDeadlineIsKeptExactly() {
        val anchor = LocalDate.of(2026, 9, 7)
        val deadline = LocalDate.of(2026, 10, 20)
        assertEquals(deadline, TodoPlanningRules.endDate(TodoPlanScope.DEADLINE, anchor, deadline))
    }
}
