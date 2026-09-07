package com.fishking.core.usecase

import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.database.LifeGoalEventEntity
import com.fishking.core.database.TodoOccurrenceEntity
import com.fishking.core.database.TodoReminderEntity
import com.fishking.core.database.TodoSeriesEntity
import com.fishking.core.database.TodoSeriesVersionEntity
import com.fishking.core.database.TodoSeriesReminderEntity
import com.fishking.core.database.TodoLifeGoalCrossRef
import com.fishking.core.database.toModel
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoStatus
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.RecurrenceRule
import com.fishking.core.model.TodoReminderSpec
import com.fishking.core.model.TodoReminder
import com.fishking.core.model.RecurrenceRules
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RoomHomeRepository(
    private val database: FishKingDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : HomeRepository {
    private val todoDao = database.todoDao()
    private val goalDao = database.lifeGoalDao()
    private val habitDao = database.habitDao()

    override fun observeTodos(date: LocalDate) =
        kotlinx.coroutines.flow.combine(todoDao.observeForDate(date), todoDao.observeRemindersForDate(date), todoDao.observeGoalTodoIds()) { rows, reminders, goalTodoIds ->
            val byTodo = reminders.filter { it.isEnabled }.groupBy { it.occurrenceId }
            rows.map { it.toModel().copy(fromLifeGoal = it.id in goalTodoIds, displayReminders = byTodo[it.id].orEmpty().map { reminder -> reminder.toModel() }) }
        }

    override fun observeCompletedDates() = todoDao.observeCompleted().map { rows -> rows.map { row ->
        if (row.planScope == "DATE") row.displayDate else row.completedAt?.atZone(clock.zone)?.toLocalDate() ?: row.displayDate
    }.toSet() }
    override fun observePlanned() = todoDao.observePlanned().map { rows -> rows.map { it.toModel() } }
    override fun observeRecurringRange(start: LocalDate, end: LocalDate) = todoDao.observeRecurringRange(start, end).map { rows -> rows.map { it.toModel() } }
    override suspend fun setPlanScope(id: String, scope: com.fishking.core.model.TodoPlanScope, deadline: LocalDate?) {
        database.withTransaction {
            val row = todoDao.findOccurrence(id) ?: return@withTransaction
            require(row.seriesId == null || scope == com.fishking.core.model.TodoPlanScope.DATE) { "循环待办不能设置一次性安排范围" }
            require(scope != com.fishking.core.model.TodoPlanScope.DEADLINE || deadline != null) { "请先选择截止日期" }
            if (row.deletedAt == null) todoDao.updateOccurrence(row.copy(planScope = scope.name,
                planDeadline = com.fishking.core.model.TodoPlanningRules.endDate(scope, row.displayDate, deadline), updatedAt = clock.instant()))
        }
    }

    override fun observeLinkedGoalIds(occurrenceId: String) =
        todoDao.observeActiveLinkedGoalIds(occurrenceId)

    override suspend fun remindersFor(occurrenceId: String): List<TodoReminder> =
        todoDao.remindersForOccurrence(occurrenceId).map { it.toModel() }

    override suspend fun recurrenceFor(occurrenceId: String): RecurrenceRule? {
        val occurrence = todoDao.findOccurrence(occurrenceId) ?: return null
        val versionId = occurrence.seriesVersionId ?: return null
        return todoDao.findSeriesVersion(versionId)?.toModel()?.recurrence
    }

    override suspend fun createTodo(
        title: String,
        date: LocalDate,
        recurrence: RecurrenceRule,
        reminders: List<TodoReminderSpec>,
        linkedGoalIds: List<String>,
        planScope: com.fishking.core.model.TodoPlanScope,
        planDeadline: LocalDate?,
    ): String {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Todo title cannot be blank" }
        require(recurrence.frequency == RecurrenceFrequency.ONCE || planScope == com.fishking.core.model.TodoPlanScope.DATE) {
            "循环待办不能设置一次性安排范围"
        }
        val fixedPlanDeadline = com.fishking.core.model.TodoPlanningRules.endDate(planScope, date, planDeadline)
        val now = clock.instant()
        return database.withTransaction {
            if (recurrence.frequency == RecurrenceFrequency.ONCE) {
                val id = newId()
                insertOccurrence(
                    id = id,
                    seriesId = null,
                    seriesVersionId = null,
                    nominalDate = date,
                    displayDate = date,
                    title = cleanTitle,
                    priority = TodoPriority.NORMAL.name,
                    accentColor = null,
                    status = TodoStatus.OPEN.name,
                    completedAt = null,
                    position = (todoDao.minimumPosition(date) ?: 0L) - POSITION_STEP,
                    isSeriesException = false,
                    createdAt = now,
                    planScope = planScope,
                    planDeadline = fixedPlanDeadline,
                )
                attachReminders(id, reminders, now)
                linkedGoalIds.distinct().forEach { goalId ->
                    todoDao.linkGoal(TodoLifeGoalCrossRef(id, goalId))
                }
                id
            } else {
                val seriesId = newId()
                val versionId = newId()
                todoDao.insertSeries(
                    TodoSeriesEntity(
                        id = seriesId,
                        activeUntilExclusive = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                todoDao.insertSeriesVersion(
                    TodoSeriesVersionEntity(
                        id = versionId,
                        seriesId = seriesId,
                        effectiveFromNominalDate = date,
                        effectiveUntilExclusive = null,
                        title = cleanTitle,
                        recurrenceFrequency = recurrence.frequency.name,
                        monthlyOverflowPolicy = recurrence.monthlyOverflowPolicy.name,
                        priority = TodoPriority.NORMAL.name,
                        accentColor = null,
                        createdAt = now,
                    ),
                )
                attachSeriesReminderTemplate(versionId, reminders, now)
                val occurrences = RecurrenceRules.datesBetween(
                    anchor = date,
                    fromInclusive = date,
                    toInclusive = date.plusDays(MATERIALIZATION_DAYS),
                    rule = recurrence,
                ).mapIndexed { index, occurrenceDate ->
                    val id = newId()
                    TodoOccurrenceEntity(
                        id = id,
                        seriesId = seriesId,
                        seriesVersionId = versionId,
                        nominalDate = occurrenceDate,
                        displayDate = occurrenceDate,
                        title = cleanTitle,
                        priority = TodoPriority.NORMAL.name,
                        accentColor = null,
                        status = TodoStatus.OPEN.name,
                        completedAt = null,
                        position = (todoDao.minimumPosition(occurrenceDate) ?: 0L) - POSITION_STEP - index,
                        isSeriesException = false,
                        deletedAt = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                todoDao.insertOccurrences(occurrences)
                occurrences.forEach { attachReminders(it.id, reminders, now) }
                occurrences.forEach { occurrence ->
                    linkedGoalIds.distinct().forEach { goalId ->
                        todoDao.linkGoal(TodoLifeGoalCrossRef(occurrence.id, goalId))
                    }
                }
                occurrences.first { it.nominalDate == date }.id
            }
        }
    }

    override suspend fun ensureOccurrences(date: LocalDate) =
        ensureOccurrenceWindow(date, date)

    override suspend fun ensureOccurrenceWindow(fromInclusive: LocalDate, toInclusive: LocalDate) {
        require(!toInclusive.isBefore(fromInclusive)) { "Todo occurrence window is inverted" }
        database.withTransaction {
            val now = clock.instant()
            for (series in todoDao.allSeries()) {
                val linkedGoalIds = todoDao.linkedGoalTemplateForSeries(series.id)
                for (version in todoDao.versionsForSeries(series.id)) {
                    val rangeStart = maxOf(fromInclusive, version.effectiveFromNominalDate)
                    var rangeEnd = toInclusive
                    version.effectiveUntilExclusive?.let { exclusive ->
                        rangeEnd = minOf(rangeEnd, exclusive.minusDays(1))
                    }
                    series.activeUntilExclusive?.let { exclusive ->
                        rangeEnd = minOf(rangeEnd, exclusive.minusDays(1))
                    }
                    if (rangeEnd.isBefore(rangeStart)) continue
                    val reminderTemplate = todoDao.seriesRemindersForVersion(version.id)
                    val occurrenceDates = RecurrenceRules.datesBetween(
                        anchor = version.effectiveFromNominalDate,
                        fromInclusive = rangeStart,
                        toInclusive = rangeEnd,
                        rule = version.toModel().recurrence,
                    )
                    for (date in occurrenceDates) {
                        if (todoDao.findOccurrence(series.id, date) != null) continue
                        val id = newId()
                        insertOccurrence(
                            id = id,
                            seriesId = series.id,
                            seriesVersionId = version.id,
                            nominalDate = date,
                            displayDate = date,
                            title = version.title,
                            priority = version.priority,
                            accentColor = version.accentColor,
                            status = TodoStatus.OPEN.name,
                            completedAt = null,
                            position = (todoDao.minimumPosition(date) ?: 0L) - POSITION_STEP,
                            isSeriesException = false,
                            createdAt = now,
                        )
                        attachReminders(
                            occurrenceId = id,
                            reminders = reminderTemplate.map {
                                TodoReminderSpec(
                                    dayOffset = it.dayOffset,
                                    localTime = it.localTime,
                                    position = it.position,
                                    isEnabled = it.isEnabled,
                                )
                            },
                            createdAt = now,
                        )
                        linkedGoalIds.forEach { goalId ->
                            todoDao.linkGoal(TodoLifeGoalCrossRef(id, goalId))
                        }
                    }
                }
            }
        }
    }

    override suspend fun toggleCompletion(occurrenceId: String) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            val now = clock.instant()
            if (occurrence.status == TodoStatus.COMPLETED.name) {
                todoDao.updateCompletion(
                    id = occurrenceId,
                    status = TodoStatus.OPEN.name,
                    completedAt = null,
                    updatedAt = now,
                )
                goalDao.deleteAutoEventsForOccurrence(occurrenceId)
            } else {
                todoDao.updateCompletion(
                    id = occurrenceId,
                    status = TodoStatus.COMPLETED.name,
                    completedAt = now,
                    updatedAt = now,
                )
                val events = todoDao.activeLinkedGoalIds(occurrenceId).map { goalId ->
                    LifeGoalEventEntity(
                        id = newId(),
                        goalId = goalId,
                        occurredOn = occurrence.displayDate,
                        result = LifeGoalResult.CHECK.name,
                        source = LifeGoalEventSource.TODO.name,
                        sourceTodoOccurrenceId = occurrenceId,
                        position = (goalDao.maximumEventPosition(goalId) ?: 0L) + POSITION_STEP,
                        createdAt = now,
                    )
                }
                if (events.isNotEmpty()) goalDao.insertEvents(events)
            }
        }
    }

    override suspend fun togglePriority(occurrenceId: String) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null || occurrence.status == TodoStatus.COMPLETED.name) {
                return@withTransaction
            }
            todoDao.updateOccurrence(
                occurrence.copy(
                    priority = if (occurrence.priority == TodoPriority.URGENT.name) {
                        TodoPriority.NORMAL.name
                    } else {
                        TodoPriority.URGENT.name
                    },
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun toggleGoalLink(occurrenceId: String, goalId: String): Boolean =
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction false
            if (occurrence.deletedAt != null) return@withTransaction false
            val goal = goalDao.findGoal(goalId) ?: return@withTransaction false
            if (goal.deletedAt != null) return@withTransaction false
            if (goalId in todoDao.activeLinkedGoalIds(occurrenceId)) {
                todoDao.unlinkGoal(occurrenceId, goalId)
                false
            } else {
                todoDao.linkGoal(TodoLifeGoalCrossRef(occurrenceId, goalId))
                true
            }
        }

    override suspend fun updateTitle(
        occurrenceId: String,
        title: String,
        scope: TodoChangeScope,
    ) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Todo title cannot be blank" }
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            if (scope == TodoChangeScope.THIS_AND_FUTURE && occurrence.seriesId != null) {
                rewriteSeriesFromOccurrence(occurrence, title = cleanTitle)
            } else {
                todoDao.updateOccurrence(
                    occurrence.copy(
                        title = cleanTitle,
                        isSeriesException = occurrence.seriesId != null || occurrence.isSeriesException,
                        updatedAt = clock.instant(),
                    ),
                )
            }
        }
    }

    override suspend fun setAccentColor(
        occurrenceId: String,
        accentColor: Long?,
        scope: TodoChangeScope,
    ) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            if (scope == TodoChangeScope.THIS_AND_FUTURE && occurrence.seriesId != null) {
                rewriteSeriesFromOccurrence(occurrence, accentColor = accentColor)
            } else {
                todoDao.updateOccurrence(
                    occurrence.copy(
                        accentColor = accentColor,
                        isSeriesException = occurrence.seriesId != null || occurrence.isSeriesException,
                        updatedAt = clock.instant(),
                    ),
                )
            }
        }
    }

    override suspend fun setReminders(
        occurrenceId: String,
        reminders: List<TodoReminderSpec>,
        scope: TodoChangeScope,
    ) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            if (scope == TodoChangeScope.THIS_AND_FUTURE && occurrence.seriesId != null) {
                rewriteSeriesFromOccurrence(occurrence, reminders = reminders)
            } else {
                val now = clock.instant()
                todoDao.deleteRemindersForOccurrence(occurrenceId)
                attachReminders(occurrenceId, reminders, now)
                if (occurrence.seriesId != null && !occurrence.isSeriesException) {
                    todoDao.updateOccurrence(occurrence.copy(isSeriesException = true, updatedAt = now))
                }
            }
        }
    }

    override suspend fun updateRecurrence(occurrenceId: String, recurrence: RecurrenceRule) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            if (recurrence.frequency == RecurrenceFrequency.ONCE) {
                stopRecurrenceInTransaction(occurrence)
                return@withTransaction
            }
            rewriteSeriesFromOccurrence(occurrence, recurrence = recurrence)
        }
    }

    override suspend fun updateRecurringTodo(
        occurrenceId: String,
        title: String,
        accentColor: Long?,
        recurrence: RecurrenceRule,
        targetDate: LocalDate?,
    ) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Todo title cannot be blank" }
        require(recurrence.frequency != RecurrenceFrequency.ONCE) { "Habit-page recurring todo must stay recurring" }
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null || occurrence.seriesId == null) return@withTransaction
            rewriteSeriesFromOccurrence(
                occurrence = occurrence,
                title = cleanTitle,
                accentColor = accentColor,
                recurrence = recurrence,
                targetDate = targetDate ?: occurrence.displayDate,
            )
        }
    }

    override suspend fun moveTodo(
        occurrenceId: String,
        targetDate: LocalDate,
        scope: TodoChangeScope,
    ): Boolean =
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction false
            if (occurrence.deletedAt != null || occurrence.status == TodoStatus.COMPLETED.name) {
                return@withTransaction false
            }
            if (scope == TodoChangeScope.THIS_AND_FUTURE && occurrence.seriesId != null) {
                rewriteSeriesFromOccurrence(occurrence, targetDate = targetDate)
                return@withTransaction true
            }
            val position = (todoDao.minimumPosition(targetDate) ?: 0L) - POSITION_STEP
            todoDao.updateOccurrence(
                occurrence.copy(
                    displayDate = targetDate,
                    position = position,
                    isSeriesException = occurrence.seriesId != null || occurrence.isSeriesException,
                    updatedAt = clock.instant(),
                ),
            )
            true
        }

    override suspend fun reorderTodos(date: LocalDate, orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        require(orderedIds.distinct().size == orderedIds.size) {
            "Todo order cannot contain duplicate ids"
        }
        database.withTransaction {
            val first = todoDao.findOccurrence(orderedIds.first())
                ?: error("Missing todo ${orderedIds.first()}")
            require(first.displayDate == date && first.deletedAt == null) {
                "Todo order must belong to the requested date"
            }
            val group = todoDao.occurrencesInGroup(date, first.status, first.priority)
            require(group.map { it.id }.toSet() == orderedIds.toSet()) {
                "Todo order must contain exactly one status/priority group"
            }
            val now = clock.instant()
            orderedIds.forEachIndexed { index, id ->
                check(todoDao.updatePosition(id, index.toLong() * POSITION_STEP, now) == 1) {
                    "Todo $id disappeared while reordering"
                }
            }
        }
    }

    override suspend fun reorderHomeItems(date: LocalDate, orderedKeys: List<String>) {
        if (orderedKeys.isEmpty()) return
        require(orderedKeys.distinct().size == orderedKeys.size) { "Home order cannot contain duplicate keys" }
        database.withTransaction {
            val now = clock.instant()
            orderedKeys.forEachIndexed { index, key ->
                val position = index.toLong() * POSITION_STEP
                when {
                    key.startsWith("todo-") -> {
                        val id = key.removePrefix("todo-")
                        val row = todoDao.findOccurrence(id) ?: error("Missing todo $id")
                        require(row.displayDate == date && row.deletedAt == null && row.status != TodoStatus.COMPLETED.name)
                        check(todoDao.updatePosition(id, position, now) == 1)
                    }
                    key.startsWith("habit-") -> {
                        val id = key.removePrefix("habit-")
                        check(habitDao.updatePosition(id, position, now) == 1) { "Missing habit $id" }
                    }
                    else -> error("Unknown home order key $key")
                }
            }
        }
    }

    override suspend fun moveTodoRelative(
        occurrenceId: String,
        targetOccurrenceId: String,
        placeAfterTarget: Boolean,
    ): Boolean = database.withTransaction {
        if (occurrenceId == targetOccurrenceId) return@withTransaction false
        val source = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction false
        val target = todoDao.findOccurrence(targetOccurrenceId) ?: return@withTransaction false
        if (source.deletedAt != null || target.deletedAt != null ||
            source.status == TodoStatus.COMPLETED.name || target.status != source.status || target.priority != source.priority
        ) return@withTransaction false

        val now = clock.instant()
        if (source.displayDate != target.displayDate) {
            todoDao.updateOccurrence(
                source.copy(
                    displayDate = target.displayDate,
                    isSeriesException = source.seriesId != null || source.isSeriesException,
                    updatedAt = now,
                ),
            )
        }
        val ordered = todoDao.occurrencesInGroup(target.displayDate, source.status, source.priority)
            .map { it.id }
            .toMutableList()
        if (!ordered.remove(occurrenceId)) return@withTransaction false
        val targetIndex = ordered.indexOf(targetOccurrenceId)
        if (targetIndex < 0) return@withTransaction false
        ordered.add((targetIndex + if (placeAfterTarget) 1 else 0).coerceAtMost(ordered.size), occurrenceId)
        ordered.forEachIndexed { index, id ->
            check(todoDao.updatePosition(id, index.toLong() * POSITION_STEP, now) == 1) {
                "Todo $id disappeared while moving"
            }
        }
        true
    }

    override suspend fun deleteTodo(occurrenceId: String, scope: TodoChangeScope) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            val seriesId = occurrence.seriesId
            if (scope == TodoChangeScope.ONLY_THIS || seriesId == null) {
                todoDao.softDeleteOccurrence(occurrenceId, clock.instant())
                return@withTransaction
            }

            val now = clock.instant()
            val series = todoDao.findSeries(seriesId) ?: return@withTransaction
            todoDao.updateSeries(
                series.copy(
                    activeUntilExclusive = occurrence.nominalDate,
                    updatedAt = now,
                ),
            )
            todoDao.softDeleteSeriesOccurrencesFrom(seriesId, occurrence.nominalDate, now)
        }
    }

    override suspend fun stopRecurrence(occurrenceId: String) {
        database.withTransaction {
            val occurrence = todoDao.findOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.deletedAt != null) return@withTransaction
            stopRecurrenceInTransaction(occurrence)
        }
    }

    private suspend fun stopRecurrenceInTransaction(occurrence: TodoOccurrenceEntity) {
        val seriesId = occurrence.seriesId ?: return
        val series = todoDao.findSeries(seriesId) ?: return
        val now = clock.instant()
        todoDao.updateSeries(
            series.copy(
                activeUntilExclusive = occurrence.nominalDate,
                updatedAt = now,
            ),
        )
        todoDao.softDeleteSeriesOccurrencesAfter(seriesId, occurrence.nominalDate, now)
        todoDao.updateOccurrence(
            occurrence.copy(
                seriesId = null,
                seriesVersionId = null,
                nominalDate = occurrence.displayDate,
                isSeriesException = false,
                updatedAt = now,
            ),
        )
    }

    private suspend fun rewriteSeriesFromOccurrence(
        occurrence: TodoOccurrenceEntity,
        targetDate: LocalDate = occurrence.displayDate,
        title: String = occurrence.title,
        accentColor: Long? = occurrence.accentColor,
        recurrence: RecurrenceRule? = null,
        reminders: List<TodoReminderSpec>? = null,
    ) {
        val now = clock.instant()
        val previousSeriesId = occurrence.seriesId
        val previousVersion = previousSeriesId?.let {
            todoDao.versionFor(it, occurrence.nominalDate)
        }
        val nextRule = recurrence ?: previousVersion?.toModel()?.recurrence ?: RecurrenceRule()
        require(nextRule.frequency != RecurrenceFrequency.ONCE) {
            "Use stopRecurrence when changing a todo to a one-time occurrence"
        }
        val nextReminders = reminders ?: todoDao.remindersForOccurrence(occurrence.id).map {
            TodoReminderSpec(
                dayOffset = it.dayOffset,
                localTime = it.localTime,
                position = it.position,
                isEnabled = it.isEnabled,
            )
        }
        val linkedGoalIds = todoDao.activeLinkedGoalIds(occurrence.id)

        if (previousSeriesId != null) {
            val previousSeries = todoDao.findSeries(previousSeriesId)
                ?: error("Missing series for occurrence ${occurrence.id}")
            todoDao.updateSeries(
                previousSeries.copy(
                    activeUntilExclusive = occurrence.nominalDate,
                    updatedAt = now,
                ),
            )
            todoDao.softDeleteSeriesOccurrencesAfter(previousSeriesId, occurrence.nominalDate, now)
        }

        val nextSeriesId = newId()
        val nextVersionId = newId()
        todoDao.insertSeries(
            TodoSeriesEntity(
                id = nextSeriesId,
                activeUntilExclusive = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        todoDao.insertSeriesVersion(
            TodoSeriesVersionEntity(
                id = nextVersionId,
                seriesId = nextSeriesId,
                effectiveFromNominalDate = targetDate,
                effectiveUntilExclusive = null,
                title = title,
                recurrenceFrequency = nextRule.frequency.name,
                monthlyOverflowPolicy = nextRule.monthlyOverflowPolicy.name,
                priority = occurrence.priority,
                accentColor = accentColor,
                createdAt = now,
            ),
        )
        attachSeriesReminderTemplate(nextVersionId, nextReminders, now)

        val position = if (targetDate == occurrence.displayDate) {
            occurrence.position
        } else {
            (todoDao.minimumPosition(targetDate) ?: 0L) - POSITION_STEP
        }
        todoDao.updateOccurrence(
            occurrence.copy(
                seriesId = nextSeriesId,
                seriesVersionId = nextVersionId,
                nominalDate = targetDate,
                displayDate = targetDate,
                title = title,
                accentColor = accentColor,
                position = position,
                isSeriesException = false,
                updatedAt = now,
            ),
        )
        todoDao.deleteRemindersForOccurrence(occurrence.id)
        attachReminders(occurrence.id, nextReminders, now)

        val futureOccurrences = RecurrenceRules.datesBetween(
            anchor = targetDate,
            fromInclusive = targetDate,
            toInclusive = targetDate.plusDays(MATERIALIZATION_DAYS),
            rule = nextRule,
        ).drop(1).mapIndexed { index, occurrenceDate ->
            TodoOccurrenceEntity(
                id = newId(),
                seriesId = nextSeriesId,
                seriesVersionId = nextVersionId,
                nominalDate = occurrenceDate,
                displayDate = occurrenceDate,
                title = title,
                priority = occurrence.priority,
                accentColor = accentColor,
                status = TodoStatus.OPEN.name,
                completedAt = null,
                position = (todoDao.minimumPosition(occurrenceDate) ?: 0L) - POSITION_STEP - index,
                isSeriesException = false,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        todoDao.insertOccurrences(futureOccurrences)
        futureOccurrences.forEach { future ->
            attachReminders(future.id, nextReminders, now)
            linkedGoalIds.forEach { goalId ->
                todoDao.linkGoal(TodoLifeGoalCrossRef(future.id, goalId))
            }
        }
    }

    private suspend fun insertOccurrence(
        id: String,
        seriesId: String?,
        seriesVersionId: String?,
        nominalDate: LocalDate,
        displayDate: LocalDate,
        title: String,
        priority: String,
        accentColor: Long?,
        status: String,
        completedAt: Instant?,
        position: Long,
        isSeriesException: Boolean,
        createdAt: Instant,
        planScope: com.fishking.core.model.TodoPlanScope = com.fishking.core.model.TodoPlanScope.DATE,
        planDeadline: LocalDate? = null,
    ) {
        todoDao.insertOccurrence(
            TodoOccurrenceEntity(
                id = id,
                seriesId = seriesId,
                seriesVersionId = seriesVersionId,
                nominalDate = nominalDate,
                displayDate = displayDate,
                title = title,
                priority = priority,
                accentColor = accentColor,
                status = status,
                completedAt = completedAt,
                position = position,
                isSeriesException = isSeriesException,
                deletedAt = null,
                createdAt = createdAt,
                updatedAt = createdAt,
                planScope = planScope.name,
                planDeadline = planDeadline,
            ),
        )
    }

    private suspend fun attachReminders(
        occurrenceId: String,
        reminders: List<TodoReminderSpec>,
        createdAt: Instant,
    ) {
        if (reminders.isEmpty()) return
        todoDao.insertReminders(
            reminders.mapIndexed { index, reminder ->
                TodoReminderEntity(
                    id = newId(),
                    occurrenceId = occurrenceId,
                    dayOffset = reminder.dayOffset,
                    localTime = reminder.localTime,
                    position = reminder.position.takeIf { it != 0L } ?: index.toLong(),
                    isEnabled = reminder.isEnabled,
                    createdAt = createdAt,
                )
            },
        )
    }

    private suspend fun attachSeriesReminderTemplate(
        seriesVersionId: String,
        reminders: List<TodoReminderSpec>,
        createdAt: Instant,
    ) {
        if (reminders.isEmpty()) return
        todoDao.insertSeriesReminders(
            reminders.mapIndexed { index, reminder ->
                TodoSeriesReminderEntity(
                    id = newId(),
                    seriesVersionId = seriesVersionId,
                    dayOffset = reminder.dayOffset,
                    localTime = reminder.localTime,
                    position = reminder.position.takeIf { it != 0L } ?: index.toLong(),
                    isEnabled = reminder.isEnabled,
                    createdAt = createdAt,
                )
            },
        )
    }

    private companion object {
        const val POSITION_STEP = 1_024L
        const val MATERIALIZATION_DAYS = 90L
    }
}
