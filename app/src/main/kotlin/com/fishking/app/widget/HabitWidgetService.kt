package com.fishking.app.widget

import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.fishking.app.FishKingApplication
import com.fishking.app.R
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitRules
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class HabitWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = HabitWidgetFactory(applicationContext)
}

private class HabitWidgetFactory(private val context: android.content.Context) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<TodayWidgetItem> = emptyList()
    private var today: LocalDate = LocalDate.now()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        today = LocalDate.now()
        val app = context.applicationContext as FishKingApplication
        items = runBlocking {
            val todos = app.container.homeRepository.observeTodos(today).first()
            val habits = app.container.habitRepository.observeWeek(HabitRules.weekStart(today)).first()
            buildTodayWidgetItems(today, todos, habits)
        }
    }

    override fun onDestroy() { items = emptyList() }
    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_habit_row).apply {
            setTextViewText(R.id.widget_habit_title, item.title)
            setTextViewText(R.id.widget_habit_progress, item.progress)
            setTextViewText(R.id.widget_habit_mark, if (item.completed) "✓" else "")
            setInt(R.id.widget_habit_stripe, "setBackgroundColor", item.stripeColor)
            setTextColor(R.id.widget_habit_progress, item.stripeColor)
            setTextColor(R.id.widget_habit_mark, if (item.completed) Color.WHITE else item.stripeColor)
            setContentDescription(R.id.widget_habit_row, item.accessibilityLabel)
            setOnClickFillInIntent(
                R.id.widget_habit_row,
                Intent()
                    .putExtra(HabitWidgetProvider.EXTRA_ITEM_ID, item.id)
                    .putExtra(HabitWidgetProvider.EXTRA_ITEM_KIND, item.kind.name),
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.let { "${it.kind}:${it.id}".hashCode().toLong() } ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}

internal fun widgetHabitCount(item: HabitWeekItem, date: LocalDate): Int = when (item.period) {
    HabitPeriod.DAILY -> item.countOn(date)
    HabitPeriod.WEEKLY -> item.weeklyEffectiveDayCount
    HabitPeriod.MONTHLY -> item.countOn(date)
}

internal enum class TodayWidgetItemKind { TODO, HABIT }

internal data class TodayWidgetItem(
    val kind: TodayWidgetItemKind,
    val id: String,
    val title: String,
    val progress: String,
    val stripeColor: Int,
    val completed: Boolean,
    val position: Long,
) {
    val accessibilityLabel: String
        get() = when (kind) {
            TodayWidgetItemKind.TODO -> "待办：$title，$progress，点击切换完成"
            TodayWidgetItemKind.HABIT -> "习惯：$title，$progress，点击打卡"
        }
}

internal fun buildTodayWidgetItems(
    date: LocalDate,
    todos: List<TodoOccurrence>,
    habits: List<HabitWeekItem>,
): List<TodayWidgetItem> {
    val todoItems = todos.map { todo ->
        TodayWidgetItem(
            kind = TodayWidgetItemKind.TODO,
            id = todo.id,
            title = todo.title,
            progress = if (todo.isCompleted) "1/1" else "0/1",
            stripeColor = when {
                todo.isCompleted -> WIDGET_COMPLETED
                todo.priority == TodoPriority.URGENT -> WIDGET_URGENT
                else -> WIDGET_NORMAL
            },
            completed = todo.isCompleted,
            position = todo.position,
        )
    }
    val habitItems = habits
        .filter { !date.isBefore(it.startDate) }
        .map { habit ->
            val count = widgetHabitCount(habit, date)
            val completed = count >= habit.targetCount
            TodayWidgetItem(
                kind = TodayWidgetItemKind.HABIT,
                id = habit.id,
                title = habit.title,
                progress = "$count/${habit.targetCount}",
                stripeColor = if (completed) WIDGET_COMPLETED else WIDGET_HABIT,
                completed = completed,
                position = habit.position,
            )
        }

    return (todoItems + habitItems).sortedWith(
        compareBy<TodayWidgetItem> { item ->
            when {
                !item.completed && item.kind == TodayWidgetItemKind.TODO && item.stripeColor == WIDGET_URGENT -> 0
                !item.completed && item.kind == TodayWidgetItemKind.TODO -> 1
                !item.completed -> 2
                item.kind == TodayWidgetItemKind.TODO && item.stripeColor == WIDGET_COMPLETED &&
                    todos.firstOrNull { it.id == item.id }?.priority == TodoPriority.URGENT -> 3
                item.kind == TodayWidgetItemKind.TODO -> 4
                else -> 5
            }
        }.thenBy { it.position }.thenBy { it.id },
    )
}

private const val WIDGET_NORMAL = 0xFFFFCA18.toInt()
private const val WIDGET_URGENT = 0xFFE84B40.toInt()
private const val WIDGET_HABIT = 0xFF36A2CF.toInt()
private const val WIDGET_COMPLETED = 0xFF45B867.toInt()
