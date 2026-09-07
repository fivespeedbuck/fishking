package com.fishking.core.model

import java.time.LocalDate

object TodoPlanningRules {
    /** Fixed to the chosen calendar period; an unfinished plan never silently rolls forward. */
    fun endDate(scope: TodoPlanScope, anchor: LocalDate, explicit: LocalDate? = null): LocalDate? = when (scope) {
        TodoPlanScope.DATE -> null
        TodoPlanScope.DEADLINE -> requireNotNull(explicit) { "请选择截止日期" }
        TodoPlanScope.WEEK -> anchor.plusDays((7 - anchor.dayOfWeek.value).toLong())
        TodoPlanScope.MONTH -> anchor.withDayOfMonth(anchor.lengthOfMonth())
        TodoPlanScope.THREE_MONTHS -> LocalDate.of(anchor.year, ((anchor.monthValue - 1) / 3 + 1) * 3, 1).let { it.withDayOfMonth(it.lengthOfMonth()) }
        TodoPlanScope.HALF_YEAR -> LocalDate.of(anchor.year, if (anchor.monthValue <= 6) 6 else 12, 1).let { it.withDayOfMonth(it.lengthOfMonth()) }
        TodoPlanScope.YEAR -> LocalDate.of(anchor.year, 12, 31)
    }
}
