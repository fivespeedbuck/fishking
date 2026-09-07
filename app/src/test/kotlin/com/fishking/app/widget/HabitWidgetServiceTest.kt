package com.fishking.app.widget

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.fishking.app.FishKingApplication
import com.fishking.core.model.HabitDayRecord
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitWidgetServiceTest {
    private val monday = LocalDate.of(2026, 8, 31)

    @Test
    fun dailyWidgetUsesTheSelectedDaysActualCount() {
        val item = item(
            period = HabitPeriod.DAILY,
            records = listOf(record(monday, 2), record(monday.plusDays(1), 1)),
        )

        assertEquals(2, widgetHabitCount(item, monday))
        assertEquals(1, widgetHabitCount(item, monday.plusDays(1)))
        assertEquals(0, widgetHabitCount(item, monday.plusDays(2)))
    }

    @Test
    fun weeklyWidgetUsesDistinctRealCheckInDays() {
        val item = item(
            period = HabitPeriod.WEEKLY,
            records = listOf(record(monday, 1), record(monday.plusDays(2), 1)),
        )

        assertEquals(2, widgetHabitCount(item, monday.plusDays(4)))
    }

    @Test
    fun todayWidgetOrdersOpenUrgentNormalHabitThenCompletedGroups() {
        val items = buildTodayWidgetItems(
            monday,
            todos = listOf(
                todo("normal", TodoPriority.NORMAL, TodoStatus.OPEN),
                todo("urgent", TodoPriority.URGENT, TodoStatus.OPEN),
                todo("done-normal", TodoPriority.NORMAL, TodoStatus.COMPLETED),
                todo("done-urgent", TodoPriority.URGENT, TodoStatus.COMPLETED),
            ),
            habits = listOf(
                item(HabitPeriod.DAILY, listOf(record(monday, 1))).copy(id = "open-habit", targetCount = 2),
                item(HabitPeriod.DAILY, listOf(record(monday, 2))).copy(id = "done-habit", targetCount = 2),
            ),
        )

        assertEquals(
            listOf("urgent", "normal", "open-habit", "done-urgent", "done-normal", "done-habit"),
            items.map { it.id },
        )
    }

    @Test
    fun widgetProviderAndCollectionServiceAreDeclared() {
        val context = ApplicationProvider.getApplicationContext<FishKingApplication>()
        val provider = context.packageManager.getReceiverInfo(
            ComponentName(context, HabitWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, HabitWidgetService::class.java),
            PackageManager.GET_META_DATA,
        )

        assertEquals(true, provider.exported)
        assertEquals(false, service.exported)
        assertEquals("android.permission.BIND_REMOTEVIEWS", service.permission)
    }

    private fun item(period: HabitPeriod, records: List<HabitDayRecord>) = HabitWeekItem(
        id = "habit",
        title = "锻炼",
        color = 0xFF8FA7E4,
        startDate = monday,
        position = 0L,
        weekStart = monday,
        versionId = "version",
        period = period,
        targetCount = if (period == HabitPeriod.DAILY) 2 else 4,
        isSkipped = false,
        records = records,
    )

    private fun record(date: LocalDate, count: Int) = HabitDayRecord(
        habitId = "habit",
        date = date,
        count = count,
        isBackfilled = false,
        updatedAt = Instant.EPOCH,
    )

    private fun todo(id: String, priority: TodoPriority, status: TodoStatus) = TodoOccurrence(
        id = id,
        nominalDate = monday,
        displayDate = monday,
        title = id,
        priority = priority,
        status = status,
        position = 0L,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
