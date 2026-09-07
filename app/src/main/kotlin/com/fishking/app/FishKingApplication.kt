package com.fishking.app

import android.app.Application
import com.fishking.core.database.FishKingDatabase
import com.fishking.core.usecase.FishKingContainer
import com.fishking.core.media.PrivateJournalMediaStore
import com.fishking.core.media.PrivateJournalAudioRecorder
import com.fishking.core.location.AndroidJournalLocationProvider
import com.fishking.core.reminder.ReminderRuntime
import com.fishking.app.widget.HabitWidgetRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FishKingApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: FishKingDatabase by lazy { FishKingDatabase.create(this) }
    val container: FishKingContainer by lazy { FishKingContainer(database) }
    val journalMediaStore by lazy { PrivateJournalMediaStore(this) }
    val journalAudioRecorder by lazy { PrivateJournalAudioRecorder(this) }
    val journalLocationProvider by lazy { AndroidJournalLocationProvider(this) }

    override fun onCreate() {
        super.onCreate()
        ReminderRuntime.install(
            context = this,
            database = database,
            ensureOccurrences = container.homeRepository::ensureOccurrenceWindow,
            scope = applicationScope,
        )
        HabitWidgetRuntime.install(
            this,
            container.homeRepository,
            container.habitRepository,
            applicationScope,
        )
    }
}
