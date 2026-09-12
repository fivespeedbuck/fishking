package com.fishking.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fishking.core.ui.DaveContextHeader
import com.fishking.core.ui.DavePageFrame
import com.fishking.core.ui.DaveTodayButton
import com.fishking.core.ui.DaveHomeViewButton
import com.fishking.core.ui.FishKingSection
import com.fishking.core.ui.FishKingTheme
import com.fishking.core.ui.HabitWeekSkin
import com.fishking.core.ui.AppBackgroundSkin
import com.fishking.core.usecase.HomeRepository
import com.fishking.core.usecase.HabitRepository
import com.fishking.core.usecase.LifeRepository
import com.fishking.core.usecase.TagRepository
import com.fishking.core.usecase.JournalRepository
import com.fishking.core.usecase.DailyReviewRepository
import com.fishking.feature.habit.HabitScreen
import com.fishking.feature.home.HomeRoute
import com.fishking.feature.journal.JournalScreen
import com.fishking.feature.life.LifeScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import com.fishking.core.reminder.ReminderRuntime
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsPreferences by lazy {
        getSharedPreferences(SETTINGS_PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyHighRefreshRatePreference(settingsPreferences.getBoolean(HIGH_REFRESH_RATE_KEY, false))
        val container = (application as FishKingApplication).container
        setContent {
            FishKingTheme {
                FishKingApp(
                    homeRepository = container.homeRepository,
                    habitRepository = container.habitRepository,
                    lifeRepository = container.lifeRepository,
                    tagRepository = container.tagRepository,
                    journalRepository = container.journalRepository,
                    dailyReviewRepository = container.dailyReviewRepository,
                    mediaStore = (application as FishKingApplication).journalMediaStore,
                    audioRecorder = (application as FishKingApplication).journalAudioRecorder,
                    locationProvider = (application as FishKingApplication).journalLocationProvider,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyHighRefreshRatePreference(settingsPreferences.getBoolean(HIGH_REFRESH_RATE_KEY, false))
        lifecycleScope.launch { ReminderRuntime.reconcileNow() }
    }
}

@Composable
private fun FishKingApp(
    homeRepository: HomeRepository,
    habitRepository: HabitRepository,
    lifeRepository: LifeRepository,
    tagRepository: TagRepository,
    journalRepository: JournalRepository,
    dailyReviewRepository: DailyReviewRepository,
    mediaStore: com.fishking.core.media.JournalMediaStore,
    audioRecorder: com.fishking.core.media.JournalAudioRecorder,
    locationProvider: com.fishking.core.location.JournalLocationProvider,
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(FishKingSection.HOME.name) }
    val selectedSection = FishKingSection.valueOf(selectedSectionName)
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var homeWeekView by rememberSaveable { mutableStateOf(false) }
    var dateNavigationToken by remember { mutableStateOf(0) }
    var habitContinuous by rememberSaveable { mutableStateOf(false) }
    var journalEditing by remember { mutableStateOf(false) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val today = LocalDate.now()
    val context = LocalContext.current
    val backup = remember { FishKingBackup(context, (context.applicationContext as FishKingApplication).database) }
    var settingsVisible by remember { mutableStateOf(false) }
    val settingsPreferences = remember { context.getSharedPreferences(SETTINGS_PREFERENCES_NAME, android.content.Context.MODE_PRIVATE) }
    var habitWeekSkin by remember {
        mutableStateOf(HabitWeekSkin.fromPreference(settingsPreferences.getString("skin", null)))
    }
    var backgroundSkin by remember {
        mutableStateOf(AppBackgroundSkin.fromPreference(settingsPreferences.getString("background_skin", null)))
    }
    var presetTags by remember { mutableStateOf(settingsPreferences.getString("tags", "").orEmpty()) }
    var highRefreshRateEnabled by remember {
        mutableStateOf(settingsPreferences.getBoolean(HIGH_REFRESH_RATE_KEY, false))
    }
    val activity = context as? MainActivity
    val maximumRefreshRate = remember(activity) {
        activity?.maximumSupportedRefreshRate() ?: 60f
    }
    var dataEpoch by remember { mutableStateOf(0) }

    var calendarVisible by remember { mutableStateOf(false) }
    val journalDates by remember(journalRepository) { journalRepository.observeEntryDates() }.collectAsStateWithLifecycle(emptySet())
    val completedDates by remember(homeRepository) { homeRepository.observeCompletedDates() }.collectAsStateWithLifecycle(emptySet())
    val selectDate: () -> Unit = { calendarVisible = true }
    if (calendarVisible) com.fishking.core.ui.DaveCalendar(selectedDate, {
        selectedEpochDay = it.toEpochDay()
        dateNavigationToken++
        calendarVisible = false
    }, { calendarVisible = false }, if (selectedSection == FishKingSection.JOURNAL) journalDates else completedDates,
        if (selectedSection == FishKingSection.JOURNAL) "有日记" else "有已完成待办")
    if (settingsVisible) SettingsPanel(backup, habitWeekSkin, backgroundSkin, presetTags, highRefreshRateEnabled, maximumRefreshRate,
        { habitWeekSkin = it; settingsPreferences.edit().putString("skin", it.preferenceValue).apply() },
        { backgroundSkin = it; settingsPreferences.edit().putString("background_skin", it.preferenceValue).apply() },
        { presetTags = it.split(Regex("[\\s#,，]+")).filter(String::isNotBlank).distinct().joinToString(" "); settingsPreferences.edit().putString("tags", presetTags).apply() },
        { enabled ->
            highRefreshRateEnabled = enabled
            settingsPreferences.edit().putBoolean(HIGH_REFRESH_RATE_KEY, enabled).apply()
            activity?.applyHighRefreshRatePreference(enabled)
        },
        { settingsVisible = false }, onDataRestored = {
            (context as? androidx.activity.ComponentActivity)?.viewModelStore?.clear()
            habitWeekSkin = HabitWeekSkin.fromPreference(settingsPreferences.getString("skin", null))
            backgroundSkin = AppBackgroundSkin.fromPreference(settingsPreferences.getString("background_skin", null))
            presetTags = settingsPreferences.getString("tags", "").orEmpty()
            highRefreshRateEnabled = settingsPreferences.getBoolean(HIGH_REFRESH_RATE_KEY, false)
            activity?.applyHighRefreshRatePreference(highRefreshRateEnabled)
            dataEpoch++; settingsVisible = false; journalEditing = false
        })

    androidx.compose.runtime.key(dataEpoch) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.fishking.core.ui.LocalHabitWeekSkin provides habitWeekSkin,
        com.fishking.core.ui.LocalAppBackgroundSkin provides backgroundSkin,
        com.fishking.core.ui.LocalPresetTags provides presetTags.split(Regex("[\\s#,，]+" )).filter(String::isNotBlank),
    ) { DavePageFrame(
        selectedSection = selectedSection,
        onSectionSelected = { selectedSectionName = it.name; journalEditing = false
            if (it == FishKingSection.JOURNAL) selectedEpochDay = LocalDate.now().toEpochDay() },
        showNavigation = !journalEditing,
        header = {
            if (!journalEditing) {
            when (selectedSection) {
                FishKingSection.HOME -> DaveContextHeader(
                    title = if (homeWeekView) "${selectedDate.year}年${selectedDate.monthValue}月 · 周视图" else selectedDate.displayTitle(today),
                    calendarEnabled = true,
                    onCalendarClick = selectDate,
                    onCalendarLongPress = { settingsVisible = true },
                    trailingWidth = if (selectedDate != today) 86.dp else 43.dp,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (selectedDate != today) {
                                DaveTodayButton { selectedEpochDay = LocalDate.now().toEpochDay(); dateNavigationToken++ }
                            }
                            DaveHomeViewButton(weekView = homeWeekView) { homeWeekView = !homeWeekView }
                        }
                    },
                )
                FishKingSection.JOURNAL -> DaveContextHeader(
                    title = selectedDate.displayTitle(today),
                    calendarEnabled = true,
                    onCalendarClick = selectDate,
                    onCalendarLongPress = { settingsVisible = true },
                    trailing = if (selectedDate != today) {
                        { DaveTodayButton { selectedEpochDay = LocalDate.now().toEpochDay() } }
                    } else null,
                )
                FishKingSection.HABIT -> DaveContextHeader(title = if (habitContinuous) "习惯周轨迹" else "本周打卡",
                    trailing = { DaveHomeViewButton(weekView = habitContinuous) { habitContinuous = !habitContinuous } })
                FishKingSection.LIFE -> DaveContextHeader(title = "人生清单")
            }
            }
        },
    ) {
        AnimatedContent(
            targetState = selectedSection,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(durationMillis = 170, delayMillis = 55),
                ) togetherWith fadeOut(
                    animationSpec = tween(durationMillis = 110),
                )
            },
            label = "main-section-fade",
        ) { visibleSection ->
        when (visibleSection) {
            FishKingSection.HOME -> HomeRoute(
                repository = homeRepository,
                habitRepository = habitRepository,
                lifeRepository = lifeRepository,
                selectedDate = selectedDate,
                onDateChange = { selectedEpochDay = it.toEpochDay() },
                weekView = homeWeekView,
                navigationToken = dateNavigationToken,
            )
            FishKingSection.JOURNAL -> com.fishking.feature.journal.JournalTimelineRoute(
                repository = journalRepository, selectedDate = selectedDate,
                onEditingChanged = { journalEditing = it },
                onDateChange = { selectedEpochDay = it.toEpochDay() },
            ) { entryRepository, entryDate, entryId, closeEditor -> JournalScreen(
                selectedDate = entryDate,
                journalEntryKey = entryId,
                onClose = closeEditor,
                journalRepository = entryRepository,
                dailyReviewRepository = dailyReviewRepository,
                lifeRepository = lifeRepository,
                mediaStore = mediaStore,
                audioRecorder = audioRecorder,
                locationProvider = locationProvider,
            ) }
            FishKingSection.HABIT -> HabitScreen(
                repository = habitRepository,
                currentDate = today,
                continuous = habitContinuous,
            )
            FishKingSection.LIFE -> LifeScreen(
                lifeRepository = lifeRepository,
                tagRepository = tagRepository,
                today = today,
                onOpenJournal = { date ->
                    selectedEpochDay = date.toEpochDay()
                    selectedSectionName = FishKingSection.JOURNAL.name
                },
            )
        }
        }
    } }
    }
}

private val shortDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

private fun LocalDate.displayTitle(today: LocalDate): String {
    val date = format(shortDateFormatter)
    val weekday = dayOfWeek.chineseShortName()
    return if (this == today) "今天 · $date $weekday" else "$date $weekday"
}

private fun LocalDate.weekTitle(): String {
    val monday = minusDays(dayOfWeek.value.toLong() - 1L)
    val weekOfMonth = (monday.dayOfMonth - 1) / 7 + 1
    return "${monday.year}年${monday.monthValue}月 第${weekOfMonth}周"
}

private fun LocalDate.weekRangeTitle(): String {
    val monday = minusDays(dayOfWeek.value.toLong() - 1L)
    val sunday = monday.plusDays(6L)
    return if (monday.monthValue == sunday.monthValue) {
        "${monday.monthValue}月${monday.dayOfMonth}日–${sunday.dayOfMonth}日"
    } else {
        "${monday.monthValue}月${monday.dayOfMonth}日–${sunday.monthValue}月${sunday.dayOfMonth}日"
    }
}

private fun DayOfWeek.chineseShortName(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
