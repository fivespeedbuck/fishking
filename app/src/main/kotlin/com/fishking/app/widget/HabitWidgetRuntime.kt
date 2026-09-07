package com.fishking.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.fishking.core.usecase.HabitRepository
import com.fishking.core.usecase.HomeRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

object HabitWidgetRuntime {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun install(
        context: Context,
        homeRepository: HomeRepository,
        habitRepository: HabitRepository,
        scope: CoroutineScope,
    ) {
        val applicationContext = context.applicationContext
        val observedDate = MutableStateFlow(LocalDate.now())
        val dateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action in DATE_CHANGE_ACTIONS) {
                    observedDate.value = LocalDate.now()
                }
            }
        }
        ContextCompat.registerReceiver(
            applicationContext,
            dateReceiver,
            IntentFilter().apply {
                DATE_CHANGE_ACTIONS.forEach(::addAction)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            observedDate
                .flatMapLatest { date ->
                    combine(
                        homeRepository.observeTodos(date),
                        habitRepository.observeWeek(com.fishking.core.model.HabitRules.weekStart(date)),
                    ) { _, _ -> Unit }
                }
                .collect {
                    HabitWidgetProvider.refreshAll(applicationContext)
                }
        }.invokeOnCompletion {
            runCatching { applicationContext.unregisterReceiver(dateReceiver) }
        }
    }

    private val DATE_CHANGE_ACTIONS = setOf(
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )
}
