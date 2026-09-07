package com.fishking.core.reminder

import android.content.Context
import com.fishking.core.database.FishKingDatabase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ReminderRuntime {
    @Volatile
    private var service: ReminderService? = null

    fun install(
        context: Context,
        database: FishKingDatabase,
        ensureOccurrences: suspend (LocalDate, LocalDate) -> Unit,
        scope: CoroutineScope,
    ) {
        if (service != null) return
        synchronized(this) {
            if (service != null) return
            ReminderService(context.applicationContext, database, ensureOccurrences).also { value ->
                service = value
                scope.launch {
                    database.todoDao().observeActiveReminders().collectLatest(value.scheduler::reconcile)
                }
                scope.launch { value.reconcileNow() }
            }
        }
    }

    suspend fun reconcileNow() {
        service?.reconcileNow()
    }

    suspend fun deliver(reminderId: String) {
        service?.deliver(reminderId)
    }
}

private class ReminderService(
    context: Context,
    private val database: FishKingDatabase,
    private val ensureOccurrences: suspend (LocalDate, LocalDate) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    val scheduler = AlarmReminderScheduler(context, clock, zoneId)
    private val notifier = TodoReminderNotifier(context)

    suspend fun reconcileNow() {
        ensureRollingWindow()
        scheduler.reconcile(database.todoDao().observeActiveReminders().firstValue())
    }

    suspend fun deliver(reminderId: String) {
        val reminder = database.todoDao().activeReminder(reminderId)
        if (reminder == null) {
            scheduler.forget(reminderId)
            return
        }
        val plan = planReminders(listOf(reminder), emptySet(), Clock.systemUTC().instant(), ZoneId.systemDefault())
        if (plan.toSchedule.isNotEmpty()) {
            scheduler.reconcile(database.todoDao().observeActiveReminders().firstValue())
            return
        }
        notifier.notify(reminder)
        scheduler.forget(reminderId)
        ensureRollingWindow()
        scheduler.reconcile(database.todoDao().observeActiveReminders().firstValue())
    }

    suspend fun ensureRollingWindow() {
        val today = LocalDate.now(clock.withZone(zoneId()))
        ensureOccurrences(today, today.plusDays(ROLLING_WINDOW_DAYS))
    }

    private companion object {
        const val ROLLING_WINDOW_DAYS = 90L
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.firstValue(): List<T> =
    first()
