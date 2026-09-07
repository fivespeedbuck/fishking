package com.fishking.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.fishking.core.database.ActiveTodoReminder
import java.time.Clock
import java.time.ZoneId

internal class AlarmReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun reconcile(reminders: List<ActiveTodoReminder>) {
        val previous = preferences.getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty().toSet()
        val plan = planReminders(reminders, previous, clock.instant(), zoneId())
        plan.toCancel.forEach(::cancel)
        plan.toSchedule.forEach(::schedule)
        preferences.edit().putStringSet(
            KEY_SCHEDULED_IDS,
            plan.toSchedule.mapTo(mutableSetOf(), ScheduledTodoReminder::reminderId),
        ).apply()
    }

    fun forget(reminderId: String) {
        cancel(reminderId)
        val next = preferences.getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty().toMutableSet()
        if (next.remove(reminderId)) preferences.edit().putStringSet(KEY_SCHEDULED_IDS, next).apply()
    }

    private fun schedule(reminder: ScheduledTodoReminder) {
        val operation = operation(reminder.reminderId, reminder.occurrenceId, reminder.title)
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
            } catch (error: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
        }
    }

    private fun cancel(reminderId: String) {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            data = Uri.Builder().scheme("fishking").authority("reminder").appendPath(reminderId).build()
        }
        val operation = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun operation(reminderId: String, occurrenceId: String, title: String): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            data = Uri.Builder().scheme("fishking").authority("reminder").appendPath(reminderId).build()
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "reminder_projection"
        const val KEY_SCHEDULED_IDS = "scheduled_ids"
    }
}

internal const val EXTRA_REMINDER_ID = "reminder_id"
internal const val EXTRA_OCCURRENCE_ID = "occurrence_id"
internal const val EXTRA_TITLE = "title"
