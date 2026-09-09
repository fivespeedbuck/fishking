package com.fishking.core.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.HabitDayRecord
import com.fishking.core.model.HabitWeekItem
import com.fishking.core.model.HabitWeekSnapshot
import com.fishking.core.model.LifeGoal
import com.fishking.core.model.LifeGoalEvent
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoReminder
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DaveInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null

    private fun content(body: @Composable () -> Unit) {
        compose.runOnUiThread {
            activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            activity!!.get().setContent { body() }
        }
        compose.waitForIdle()
    }

    @After fun closeActivity() {
        compose.mainClock.autoAdvance = true
        val controller = activity
        activity = null
        compose.runOnUiThread { controller?.get()?.setContent { } }
        compose.waitForIdle()
        compose.runOnUiThread { controller?.pause()?.stop()?.destroy() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun taskCompletionDoesNotWaitForAnimationClock() {
        var completed = 0
        content { DaveTaskCard(todo(), { completed++ }, modifier = Modifier.testTag("task")) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("task").performClick()
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun longPressTaskBodyLiftsAndDropsWithoutCompleting() {
        var drops = 0
        var positions = 0
        var completed = 0
        content { DaveDragLayer {
            DaveSwipeTaskCard(todo(), { completed++ }, {}, {}, {},
                onDragPosition = { positions++ }, onDragFinished = { drops++ }, modifier = Modifier.testTag("lift-card"))
        } }
        compose.onNodeWithTag("lift-card").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("lift-card").performTouchInput { moveBy(Offset(0f, 120f)); up() }
        compose.runOnIdle { assertTrue(positions > 0); assertEquals(1, drops); assertEquals(0, completed) }
    }

    @Test fun longPressCompactTaskBodyLiftsAndDrops() {
        var drops = 0
        var completed = 0
        content { DaveDragLayer {
            DaveCompactTaskCard(todo(), { completed++ }, { drops++ }, modifier = Modifier.testTag("compact-lift"))
        } }
        compose.onNodeWithTag("compact-lift").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("compact-lift").performTouchInput { moveBy(Offset(0f, 120f)); up() }
        compose.runOnIdle { assertEquals(1, drops); assertEquals(0, completed) }
    }

    @Test fun deleteActionMatchesExpandedTextHeight() {
        content { DaveJournalLine(androidx.compose.ui.text.input.TextFieldValue("较高的正文"), null,
            com.fishking.core.model.JournalTextSize.BODY, emptyList(), false, 0L, {}, {}, {}, Modifier.testTag("tall-line"), minHeight = 180.dp) }
        compose.onNodeWithTag("tall-line").performTouchInput { swipeLeft() }
        val lineBounds = compose.onNodeWithTag("tall-line").getUnclippedBoundsInRoot()
        val deleteBounds = compose.onNodeWithContentDescription("删除这一项").getUnclippedBoundsInRoot()
        assertEquals(lineBounds.bottom - lineBounds.top, deleteBounds.bottom - deleteBounds.top)
    }

    @Test fun liftedTaskUsesEdgeDropHandlerWithoutCompletingOrFallingBack() {
        val date = LocalDate.of(2026, 9, 6)
        var movedId: String? = null
        var completed = 0
        var lastPosition: Offset? = null
        var fallback: Offset? = null
        content { DaveDragLayer {
            DaveDateDropArea(date, { _, _ -> }, onDrop = { id, _ -> movedId = id; true }) {
                Column {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(120.dp))
                    DaveSwipeTaskCard(todo(), { completed++ }, {}, {}, {},
                        onDragPosition = { lastPosition = it }, onDragFinished = { fallback = it }, modifier = Modifier.testTag("date-drop-card"))
                }
            }
        } }
        compose.onNodeWithTag("date-drop-card").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("date-drop-card").performTouchInput { moveBy(Offset(0f, 120f)); up() }
        compose.runOnIdle {
            assertEquals("test", movedId)
            assertTrue(lastPosition != null)
            assertEquals(null, fallback)
            assertEquals(0, completed)
        }
    }

    @Test fun motionPhotoButtonRequestsPlaybackAndVideoHasExplicitFullscreenControl() {
        var played = 0
        var fullscreen = 0
        content { Column {
            DaveMotionPhotoButton({ played++ })
            DaveVideoControls(false, 2000, 10000, false, {}, {}, { fullscreen++ })
        } }
        compose.onNodeWithContentDescription("播放动态图").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("视频进度").assertIsDisplayed()
        compose.onNodeWithContentDescription("全屏播放").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, played); assertEquals(1, fullscreen) }
    }

    @Test fun taskTimeClickDoesNotCompleteTask() {
        var completed = 0
        var edits = 0
        content { androidx.compose.runtime.CompositionLocalProvider(LocalTodoTimeEditor provides { edits++ }) {
            DaveSwipeTaskCard(todo().copy(displayReminders = listOf(
                TodoReminder("reminder-1", "test", 0, LocalTime.of(9, 30), 0L, true, Instant.EPOCH),
            )), { completed++ }, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("设置待办提醒时间").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(0, completed) }
    }

    @Test fun editingDraftDoesNotTakeFocus() {
        content { DaveInlineDraftCard("已有待办", {}, {}, {}, autoFocus = false) }
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test fun tagButtonAppliesInputToEditingDraft() {
        var edited = ""
        content { DaveTodoQuickOptions(
            recurrence = com.fishking.core.model.RecurrenceFrequency.ONCE, reminderTimes = emptyList(),
            onRecurrenceSelected = {}, onReminderAdd = {}, onReminderEdit = {}, onReminderRemove = {},
            editingTitle = "买牛奶 #旧标签", onEditingTitleChange = { edited = it },
        ) }
        compose.onNodeWithContentDescription("输入自定义TAG").assertDoesNotExist()
        compose.onNodeWithContentDescription("编辑待办TAG").performClick()
        compose.onNodeWithContentDescription("输入自定义TAG").performTextReplacement("生活 采购")
        compose.onNodeWithText("应用").performClick()
        compose.runOnIdle { assertEquals("买牛奶 #生活 #采购", edited) }
        compose.onNodeWithContentDescription("输入自定义TAG").assertDoesNotExist()
    }

    @Test fun journalDeleteAppearsOnlyAfterLeftSwipe() {
        var deleted = false
        content { DaveJournalLine(androidx.compose.ui.text.input.TextFieldValue("记录"), null,
            com.fishking.core.model.JournalTextSize.BODY, emptyList(), false, 0L, {}, {},
            { deleted = true }, Modifier.testTag("line")) }
        compose.onNodeWithContentDescription("删除这一项").assertDoesNotExist()
        compose.onNodeWithTag("line").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("删除这一项").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test fun focusedJournalTextStillRevealsDeleteWithoutChangingText() {
        var changed = false
        content { DaveJournalLine(androidx.compose.ui.text.input.TextFieldValue("已聚焦正文"), null,
            com.fishking.core.model.JournalTextSize.BODY, emptyList(), true, 0L, {}, { if (it.text != "已聚焦正文") changed = true }, {}, Modifier.testTag("line")) }
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNodeWithTag("line").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("删除这一项").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!changed) }
        compose.onNodeWithTag("line").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("删除这一项").assertDoesNotExist()
    }

    @Test fun journalToolbarShowsCompactActionsWithoutExpansion() {
        var links = 0
        var tags = 0
        content {
            DaveJournalActionBar({}, {}, {}, { links++ }, { tags++ }, importingMedia = false)
        }
        compose.onNodeWithContentDescription("图片/视频").assertIsDisplayed()
        compose.onNodeWithContentDescription("录音").assertIsDisplayed()
        compose.onNodeWithContentDescription("地点").assertIsDisplayed()
        compose.onNodeWithContentDescription("关联").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("编辑日记TAG").assertIsDisplayed().performClick()
        compose.onNodeWithText("图片/视频").assertDoesNotExist()
        compose.onNodeWithText("录音").assertDoesNotExist()
        compose.onNodeWithText("地点").assertDoesNotExist()
        compose.onNodeWithText("关联").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, links)
            assertEquals(1, tags)
        }
    }

    @Test fun ongoingLifeGoalUsesLeadingCircleAndCompactTitleHistory() {
        var toggles = 0
        val goal = lifeGoal().copy(
            events = listOf(
                LifeGoalEvent(
                    id = "event-check",
                    goalId = "goal-1",
                    occurredOn = LocalDate.of(2026, 9, 5),
                    result = LifeGoalResult.CHECK,
                    source = LifeGoalEventSource.MANUAL,
                    position = 0,
                    createdAt = Instant.EPOCH,
                ),
                LifeGoalEvent(
                    id = "event-cross",
                    goalId = "goal-1",
                    occurredOn = LocalDate.of(2026, 9, 6),
                    result = LifeGoalResult.CROSS,
                    source = LifeGoalEventSource.MANUAL,
                    position = 1,
                    createdAt = Instant.EPOCH,
                ),
            ),
            linkedJournalDates = listOf(LocalDate.of(2026, 9, 6)),
        )
        content {
            DaveLifeGoalCard(
                value = goal,
                tags = emptyList(),
                onToggleResult = { toggles++ },
                onAddToToday = {},
                onEdit = {},
                onDelete = {},
                onMovePreview = {},
                onMoveCommit = {},
                onMoveCancel = {},
                onOpenJournal = {},
            )
        }

        compose.onNodeWithContentDescription("完成或取消人生清单").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("打开关联日记").assertIsDisplayed()
        compose.onNodeWithText("✓ ×").assertIsDisplayed()
        compose.onNodeWithContentDescription("记录今天成功").assertDoesNotExist()
        compose.onNodeWithContentDescription("记录今天失败").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, toggles) }
    }

    @Test fun calendarDisplaysDiaryDotForMarkedDate() {
        val date = LocalDate.of(2026, 9, 6)
        content { DaveCalendar(date, {}, {}, setOf(date)) }
        compose.onNodeWithContentDescription("2026-09-06 有日记").assertIsDisplayed()
        compose.onNodeWithContentDescription("2026-09-05 有日记").assertDoesNotExist()
    }

    @Test fun journalTimelineThumbnailStripKeepsAVisibleImageSlot() {
        content { DaveJournalTimelineThumbnails(listOf("missing-preview.jpg")) }
        compose.onNodeWithContentDescription("日记图片缩略图加载中 1").assertIsDisplayed()
    }

    @Test fun weeklyCheckShowsClearBeforeWeeklyQuota() {
        content { DaveHabitCard("运动", 1, 2, HabitPeriod.WEEKLY, 0xFF355C52, false,
            checkedOnDate = true, onClick = {}) }
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("CLEAR").assertIsDisplayed()
        compose.onNodeWithText("#周常").assertIsDisplayed()
    }

    @Test fun homeHabitTagsFollowPeriodAndCompactCardsHideCustomTags() {
        content {
            Column {
                DaveHabitCard("刷牙", 0, 1, HabitPeriod.DAILY, 0xFF8FA7E4, false, onClick = {})
                DaveHabitCard("月度整理 #家务", 0, 1, HabitPeriod.MONTHLY, 0xFF8FA7E4, false, onClick = {})
                DaveCompactHabitCard("运动 #自定义周", 0, 4, HabitPeriod.WEEKLY, 0xFF8FA7E4, onClick = {})
                DaveCompactHabitCard("月检 #自定义月", 0, 1, HabitPeriod.MONTHLY, 0xFF8FA7E4, onClick = {})
            }
        }
        compose.onNodeWithText("#日常").assertIsDisplayed()
        compose.onNodeWithText("#月常 #家务").assertIsDisplayed()
        compose.onNodeWithText("#周常").assertIsDisplayed()
        compose.onNodeWithText("#月常").assertIsDisplayed()
        compose.onNodeWithText("#自定义周", substring = true).assertDoesNotExist()
        compose.onNodeWithText("#自定义月", substring = true).assertDoesNotExist()
    }

    @Test fun intervalHabitUsesExistingStepperAndShowsIconOnlyCadenceChoices() {
        var interval = 3
        content {
            Column {
                DaveHabitQuickOptions(
                    period = HabitPeriod.EVERY_N_DAYS,
                    targetCount = 1,
                    intervalDays = interval,
                    onPeriodSelected = {},
                    onTargetCountChanged = {},
                    onIntervalDaysChanged = { interval = it },
                )
                DaveHabitCadenceOptions(HabitPeriod.EVERY_N_DAYS, {})
                DaveHabitCard(
                    "洗床单", 0, 1, HabitPeriod.EVERY_N_DAYS, 0xFF8FA7E4,
                    false, onClick = {}, intervalDays = 3,
                )
            }
        }
        compose.onNodeWithContentDescription("每N天一次").assertIsDisplayed()
        compose.onNodeWithContentDescription("完成后隔N天").assertIsDisplayed()
        compose.onNodeWithContentDescription("增加间隔天数").performClick()
        compose.runOnIdle { assertEquals(4, interval) }
        compose.onNodeWithText("#每3天").assertIsDisplayed()
    }

    @Test fun habitWeekProgressShowsPeriodSuffixWithoutCustomTags() {
        val week = LocalDate.of(2026, 9, 7)
        content {
            DaveHabitWeekPanel(
                snapshot = HabitWeekSnapshot(week, listOf(
                    habitItem("运动 #健康", week, HabitPeriod.WEEKLY, 4),
                    habitItem("整理 #家务", week, HabitPeriod.MONTHLY, 1),
                )),
                today = week, onToggle = { _, _ -> }, onEdit = {},
                onToggleSkip = { _, _ -> }, onEndFromWeek = { _, _ -> },
            )
        }
        compose.onNodeWithText("0/4·周").assertIsDisplayed()
        compose.onNodeWithText("0/1·月").assertIsDisplayed()
        compose.onNodeWithText("#健康", substring = true).assertDoesNotExist()
        compose.onNodeWithText("#家务", substring = true).assertDoesNotExist()
    }

    @Test fun habitWeekMarksTodayAndKeepsOffPlanDaysVisible() {
        val week = LocalDate.of(2026, 9, 7)
        content {
            DaveHabitWeekPanel(
                snapshot = HabitWeekSnapshot(
                    week,
                    listOf(habitItem("刷酸", week, HabitPeriod.WEEKLY, 1).copy(scheduleDays = setOf(3))),
                ),
                today = week,
                onToggle = { _, _ -> },
                onEdit = {},
                onToggleSkip = { _, _ -> },
                onEndFromWeek = { _, _ -> },
            )
        }
        compose.onNodeWithText("今").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("空集", substring = true).assertCountEquals(6)
    }

    @Test fun completedWeeklyAndMonthlyTargetsReplaceRemainingEmptyCirclesWithEmptySetMarks() {
        val week = LocalDate.of(2026, 9, 7)
        val completed = HabitDayRecord("done", week, 1, false, Instant.EPOCH)
        content {
            DaveHabitWeekPanel(
                snapshot = HabitWeekSnapshot(
                    week,
                    listOf(
                        habitItem("周目标", week, HabitPeriod.WEEKLY, 1)
                            .copy(records = listOf(completed.copy(habitId = "周目标"))),
                        habitItem("月目标", week, HabitPeriod.MONTHLY, 1)
                            .copy(records = listOf(completed.copy(habitId = "月目标"))),
                    ),
                ),
                today = week.plusDays(2),
                onToggle = { _, _ -> },
                onEdit = {},
                onToggleSkip = { _, _ -> },
                onEndFromWeek = { _, _ -> },
            )
        }
        compose.onAllNodesWithContentDescription("空集", substring = true).assertCountEquals(12)
    }

    @Test fun removedGlobalSkinsMigrateToUnifiedHabitSkin() {
        assertEquals(HabitWeekSkin.UNIFIED_CARD, HabitWeekSkin.fromPreference(null))
        assertEquals(HabitWeekSkin.UNIFIED_CARD, HabitWeekSkin.fromPreference("paper"))
        assertEquals(HabitWeekSkin.UNIFIED_CARD, HabitWeekSkin.fromPreference("dave"))
        assertEquals(HabitWeekSkin.SPACED_CARDS, HabitWeekSkin.fromPreference("habit_spaced"))
    }

    @Test fun habitEditorReplacesSelectedRowBetweenNeighboursAndCancelRestoresIt() {
        val week = LocalDate.of(2026, 9, 7)
        val editing = androidx.compose.runtime.mutableStateOf<String?>("运动")
        content {
            DaveHabitWeekPanel(
                snapshot = HabitWeekSnapshot(week, listOf(
                    habitItem("读书", week), habitItem("运动", week), habitItem("练琴", week),
                )),
                today = week, onToggle = { _, _ -> }, onEdit = {},
                onToggleSkip = { _, _ -> }, onEndFromWeek = { _, _ -> },
                editingHabitId = editing.value,
                habitEditor = { androidx.compose.material3.Text("编辑运动") },
            )
        }
        compose.onNodeWithText("运动").assertDoesNotExist()
        compose.onAllNodesWithText("编辑运动").assertCountEquals(1)
        val before = compose.onNodeWithText("读书").getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithText("编辑运动").getUnclippedBoundsInRoot()
        val after = compose.onNodeWithText("练琴").getUnclippedBoundsInRoot()
        assertTrue("editor must occupy the selected row, not the footer", before.bottom <= editor.top && editor.bottom <= after.top)
        compose.runOnIdle { editing.value = null }
        compose.onNodeWithText("编辑运动").assertDoesNotExist()
        compose.onNodeWithText("运动").assertIsDisplayed()
    }

    @Test fun currentHabitEditorDoesNotReplaceHistoricalRowWithSameId() {
        val week = LocalDate.of(2026, 9, 7)
        content {
            DaveHabitWeekPanel(
                snapshot = HabitWeekSnapshot(week.minusWeeks(1), listOf(habitItem("运动", week.minusWeeks(1)))),
                today = week, onToggle = { _, _ -> }, onEdit = {},
                onToggleSkip = { _, _ -> }, onEndFromWeek = { _, _ -> },
                editingHabitId = "运动",
                habitEditor = { androidx.compose.material3.Text("编辑运动") },
            )
        }
        compose.onNodeWithText("编辑运动").assertDoesNotExist()
        compose.onNodeWithText("运动").assertIsDisplayed()
    }

    private fun habitItem(title: String, week: LocalDate, period: HabitPeriod = HabitPeriod.DAILY, target: Int = 1) = HabitWeekItem(
        id = title, title = title, color = 0xFF8FA7E4, startDate = week, position = 0,
        weekStart = week, versionId = "$title-version", period = period, targetCount = target,
        isSkipped = false, records = emptyList(),
    )

    @Test fun habitCompletionDoesNotWaitForAnimationClock() {
        var completed = 0
        content {
            DaveHabitCard("刷牙", 1, 2, HabitPeriod.DAILY, 0xFF8FA7E4, false,
                onClick = { completed++ }, modifier = Modifier.testTag("habit"))
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("habit").performClick()
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun readOnlyCardsExposeNoCompletionAction() {
        content {
            Column {
                DaveTaskCard(todo(), {}, modifier = Modifier.testTag("review"), readOnly = true)
            }
        }
        compose.onNodeWithTag("review").assert(androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick))
    }

    @Test fun habitAndLifeCardsRevealTrailingActionsAfterLeftSwipe() {
        val weekStart = LocalDate.of(2026, 9, 7)
        var habitEditCount = 0
        var lifeAddCount = 0
        content {
            Column {
                DaveHabitWeekPanel(
                    snapshot = HabitWeekSnapshot(
                        weekStart = weekStart,
                        items = listOf(
                            HabitWeekItem(
                                id = "habit-1",
                                title = "读书",
                                color = 0xFF8FA7E4,
                                startDate = weekStart,
                                position = 0,
                                weekStart = weekStart,
                                versionId = "habit-version-1",
                                period = HabitPeriod.DAILY,
                                targetCount = 1,
                                isSkipped = false,
                                records = emptyList(),
                            ),
                        ),
                    ),
                    today = weekStart,
                    onToggle = { _, _ -> },
                    onEdit = { habitEditCount++ },
                    onToggleSkip = { _, _ -> },
                    onEndFromWeek = { _, _ -> },
                    modifier = Modifier.testTag("habit-panel"),
                )
                DaveLifeGoalCard(
                    value = lifeGoal(),
                    tags = listOf("长期"),
                    onToggleResult = {},
                    onAddToToday = { lifeAddCount++ },
                    onEdit = {},
                    onDelete = {},
                    onMovePreview = {},
                    onMoveCommit = {},
                    onMoveCancel = {},
                    onOpenJournal = {},
                    modifier = Modifier.testTag("life-card"),
                )
            }
        }

        val habitPanelBounds = compose.onNodeWithTag("habit-panel").getUnclippedBoundsInRoot()
        val panelPixels = compose.onNodeWithTag("habit-panel").fetchSemanticsNode().boundsInRoot
        val habitTitlePixels = compose.onNodeWithText("读书").fetchSemanticsNode().boundsInRoot
        val habitRowY = habitTitlePixels.center.y - panelPixels.top
        compose.onNodeWithTag("habit-panel").performTouchInput {
            swipe(Offset(width * .85f, habitRowY), Offset(width * .15f, habitRowY), durationMillis = 500)
        }
        compose.waitForIdle()
        val habitEdit = compose.onNodeWithContentDescription("编辑打卡项目")
        habitEdit.assertIsDisplayed()
        val habitEditBounds = habitEdit.getUnclippedBoundsInRoot()
        assertTrue("habit edit action should remain inside the panel", habitEditBounds.left >= habitPanelBounds.left)
        assertTrue("habit edit action should be visible at the panel edge", habitEditBounds.right <= habitPanelBounds.right + 1.dp)
        habitEdit.performTouchInput { click() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("habit edit touch must reach the revealed button", 1, habitEditCount) }

        val lifeCardBounds = compose.onNodeWithTag("life-card").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("life-card").performTouchInput { swipeLeft(durationMillis = 500) }
        compose.waitForIdle()
        val lifeAdd = compose.onNodeWithContentDescription("加入今日待办")
        lifeAdd.assertIsDisplayed()
        compose.onNodeWithContentDescription("编辑人生目标").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除人生目标").assertIsDisplayed()
        val lifeAddBounds = lifeAdd.getUnclippedBoundsInRoot()
        assertTrue("life action should move into the card's trailing edge", lifeAddBounds.left >= lifeCardBounds.left)
        assertTrue("life action should not extend beyond the card", lifeAddBounds.right <= lifeCardBounds.right + 1.dp)

        lifeAdd.performTouchInput { click() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("life add touch must reach the revealed button", 1, lifeAddCount) }
    }

    @Test fun nonEmptyDraftCloseButtonCancelsWithoutConfirming() {
        var confirmed = 0
        var cancelled = 0
        content {
            DaveInlineDraftCard(
                value = "有内容的草稿",
                onValueChange = {},
                onConfirm = { confirmed++ },
                onCancelEmpty = { cancelled++ },
                modifier = Modifier.testTag("draft"),
            )
        }

        val draftBounds = compose.onNodeWithTag("draft").getUnclippedBoundsInRoot()
        val close = compose.onNodeWithContentDescription("取消新建")
        close.assertIsDisplayed()
        val closeBounds = close.getUnclippedBoundsInRoot()
        val draftCenterX = (draftBounds.left.value + draftBounds.right.value) / 2f
        assertTrue("draft close button should be on the trailing side", closeBounds.left.value > draftCenterX)
        assertTrue("draft close button should stay inside the draft card", closeBounds.right <= draftBounds.right + 1.dp)
        compose.onNodeWithText("有内容的草稿").assertIsDisplayed()
        close.performClick()

        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(0, confirmed)
        }
    }

    @Test fun dragControllerMovesFloatingOverlayBoundsAndClearsOnFinish() {
        val controller = DaveDragController()
        val origin = Rect(left = 10f, top = 20f, right = 110f, bottom = 92f)
        val draw = @Composable { DaveTaskCard(todo(), {}) }

        controller.start(origin, draw)
        assertEquals(origin, controller.bounds)
        assertTrue("starting a drag should provide overlay content", controller.content != null)

        controller.move(Offset(90f, 36f))
        val moved = controller.bounds
            ?: throw AssertionError("moving a drag should keep overlay bounds visible")
        assertEquals(Rect(left = 100f, top = 56f, right = 200f, bottom = 128f), moved)
        assertTrue("floating overlay should move horizontally", moved.left > origin.left + 50f)
        assertTrue("floating overlay should move vertically", moved.top > origin.top + 15f)

        controller.finish()
        assertEquals(null, controller.bounds)
        assertEquals(null, controller.content)
    }

    @Test fun dragControllerSlidesNeighbourIntoTheVacatedSlot() {
        val controller = DaveDragController()
        controller.register("a", "day-open-normal", Rect(0f, 0f, 100f, 80f))
        controller.register("b", "day-open-normal", Rect(0f, 90f, 100f, 170f))
        controller.register("c", "day-open-normal", Rect(0f, 180f, 100f, 260f))

        controller.beginItem("a", Offset(50f, 40f), 1L, null) {}
        controller.point(Offset(50f, 172f))

        assertEquals("b" to true, controller.reorderTarget())
        assertEquals(Offset(0f, -90f), controller.previewOffset("b"))
        assertEquals(Offset.Zero, controller.previewOffset("c"))
    }

    @Test fun dragControllerSlidesDestinationGroupWhenCrossingWeekDays() {
        val controller = DaveDragController()
        controller.register("source", "home-week|2026-09-07", Rect(0f, 0f, 100f, 80f))
        controller.register("target-a", "home-week|2026-09-08", Rect(0f, 100f, 100f, 180f))
        controller.register("target-b", "home-week|2026-09-08", Rect(106f, 100f, 206f, 180f))

        controller.beginItem("source", Offset(50f, 40f), 1L, null) {}
        controller.point(Offset(30f, 120f))

        assertEquals("target-a" to false, controller.reorderTarget())
        assertEquals(Offset(106f, 0f), controller.previewOffset("target-a"))
        assertTrue(controller.previewOffset("target-b").y > 0f)
    }

    private fun todo() = TodoOccurrence(
        id = "test", title = "任务 #标签", nominalDate = LocalDate.of(2026, 9, 5),
        displayDate = LocalDate.of(2026, 9, 5), priority = TodoPriority.NORMAL,
        status = TodoStatus.OPEN, position = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun lifeGoal() = LifeGoalWithEvents(
        goal = LifeGoal(
            id = "goal-1",
            title = "学会 Kotlin",
            type = LifeGoalType.ONGOING,
            position = 0,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        events = emptyList(),
    )
}
