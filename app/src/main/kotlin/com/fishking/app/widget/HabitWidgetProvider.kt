package com.fishking.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.fishking.app.FishKingApplication
import com.fishking.app.MainActivity
import com.fishking.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HabitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        (context.applicationContext as? FishKingApplication)?.ensureHabitWidgetRuntime()
        ids.forEach { manager.updateAppWidget(it, widgetViews(context, it)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
            val kind = intent.getStringExtra(EXTRA_ITEM_KIND)?.let {
                runCatching { TodayWidgetItemKind.valueOf(it) }.getOrNull()
            } ?: return
            val pendingResult = goAsync()
            workerScope.launch {
                try {
                    val app = context.applicationContext as FishKingApplication
                    when (kind) {
                        TodayWidgetItemKind.TODO -> app.container.homeRepository.toggleCompletion(itemId)
                        TodayWidgetItemKind.HABIT -> app.container.habitRepository.toggleCheckIn(itemId, LocalDate.now())
                    }
                    refreshAll(context)
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (intent.action in refreshActions) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.fishking.app.widget.TOGGLE_TODAY_ITEM"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_ITEM_KIND = "item_kind"
        private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshActions = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        fun hasWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HabitWidgetProvider::class.java)
            return manager.getAppWidgetIds(component).isNotEmpty()
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HabitWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { manager.updateAppWidget(it, widgetViews(context, it)) }
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun widgetViews(context: Context, widgetId: Int): RemoteViews {
            val serviceIntent = Intent(context, HabitWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("fishking://today-widget/$widgetId")
            }
            val toggleIntent = Intent(context, HabitWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val template = PendingIntent.getBroadcast(
                context,
                widgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
            )
            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_habits).apply {
                setTextViewText(R.id.widget_title, "今日清单 · ${LocalDate.now().format(titleFormatter)}")
                setRemoteAdapter(R.id.widget_list, serviceIntent)
                setEmptyView(R.id.widget_list, R.id.widget_empty)
                setPendingIntentTemplate(R.id.widget_list, template)
                setOnClickPendingIntent(R.id.widget_title, openApp)
                setOnClickPendingIntent(R.id.widget_empty, openApp)
            }
        }

        private val titleFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE)
    }
}
