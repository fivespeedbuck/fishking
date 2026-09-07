package com.fishking.core.usecase

import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.RecurrenceRule
import com.fishking.core.model.TodoReminderSpec
import com.fishking.core.model.TodoReminder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HomeRepository {
    fun observeCompletedDates(): Flow<Set<LocalDate>> = kotlinx.coroutines.flow.flowOf(emptySet())
    fun observePlanned(): Flow<List<TodoOccurrence>> = kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeRecurringRange(start: LocalDate, end: LocalDate): Flow<List<TodoOccurrence>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun setPlanScope(id: String, scope: com.fishking.core.model.TodoPlanScope, deadline: LocalDate? = null) = Unit
    fun observeTodos(date: LocalDate): Flow<List<TodoOccurrence>>

    fun observeLinkedGoalIds(occurrenceId: String): Flow<List<String>>

    suspend fun createTodo(
        title: String,
        date: LocalDate,
        recurrence: RecurrenceRule = RecurrenceRule(),
        reminders: List<TodoReminderSpec> = emptyList(),
        linkedGoalIds: List<String> = emptyList(),
        planScope: com.fishking.core.model.TodoPlanScope = com.fishking.core.model.TodoPlanScope.DATE,
        planDeadline: LocalDate? = null,
    ): String

    suspend fun ensureOccurrences(date: LocalDate)

    suspend fun ensureOccurrenceWindow(fromInclusive: LocalDate, toInclusive: LocalDate) {
        require(!toInclusive.isBefore(fromInclusive)) { "Todo occurrence window is inverted" }
        var date = fromInclusive
        while (!date.isAfter(toInclusive)) {
            ensureOccurrences(date)
            date = date.plusDays(1)
        }
    }

    suspend fun toggleCompletion(occurrenceId: String)

    suspend fun togglePriority(occurrenceId: String)

    /** Toggles one active life-goal link without synthesizing completion history. */
    suspend fun toggleGoalLink(occurrenceId: String, goalId: String): Boolean

    suspend fun updateTitle(
        occurrenceId: String,
        title: String,
        scope: TodoChangeScope = TodoChangeScope.ONLY_THIS,
    )

    suspend fun setAccentColor(
        occurrenceId: String,
        accentColor: Long?,
        scope: TodoChangeScope = TodoChangeScope.ONLY_THIS,
    )

    suspend fun setReminders(
        occurrenceId: String,
        reminders: List<TodoReminderSpec>,
        scope: TodoChangeScope = TodoChangeScope.ONLY_THIS,
    )

    /** Returns the current reminder projection for an occurrence, for editing UI only. */
    suspend fun remindersFor(occurrenceId: String): List<TodoReminder> = emptyList()

    /** Returns the current recurrence rule, or null for a one-time occurrence. */
    suspend fun recurrenceFor(occurrenceId: String): RecurrenceRule? = null

    suspend fun updateRecurrence(occurrenceId: String, recurrence: RecurrenceRule)

    /** Updates the habit-page projection of one recurring todo from this occurrence onward. */
    suspend fun updateRecurringTodo(
        occurrenceId: String,
        title: String,
        accentColor: Long?,
        recurrence: RecurrenceRule,
        targetDate: LocalDate? = null,
    ) {
        updateTitle(occurrenceId, title, TodoChangeScope.THIS_AND_FUTURE)
        setAccentColor(occurrenceId, accentColor, TodoChangeScope.THIS_AND_FUTURE)
        updateRecurrence(occurrenceId, recurrence)
        if (targetDate != null) moveTodo(occurrenceId, targetDate, TodoChangeScope.THIS_AND_FUTURE)
    }

    suspend fun moveTodo(
        occurrenceId: String,
        targetDate: LocalDate,
        scope: TodoChangeScope = TodoChangeScope.ONLY_THIS,
    ): Boolean

    /** Persists the complete order of one date/status/priority todo group. */
    suspend fun reorderTodos(date: LocalDate, orderedIds: List<String>)

    /** Persists the visible open order shared by todos and habit projections on one day. */
    suspend fun reorderHomeItems(date: LocalDate, orderedKeys: List<String>) = Unit

    /** Moves an open todo to the target todo's date and inserts it at that exact visual slot. */
    suspend fun moveTodoRelative(occurrenceId: String, targetOccurrenceId: String, placeAfterTarget: Boolean): Boolean = false

    suspend fun deleteTodo(
        occurrenceId: String,
        scope: TodoChangeScope = TodoChangeScope.ONLY_THIS,
    )

    suspend fun stopRecurrence(occurrenceId: String)
}
