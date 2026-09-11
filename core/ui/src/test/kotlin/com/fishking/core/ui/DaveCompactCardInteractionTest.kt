package com.fishking.core.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.fishking.core.model.HabitPeriod
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoStatus
import com.fishking.core.model.JournalListStyle
import com.fishking.core.model.JournalTextSize
import java.time.Instant
import java.time.LocalDate
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DaveCompactCardInteractionTest {
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
        compose.runOnUiThread { controller?.get()?.setContent { } }
        compose.waitForIdle()
        compose.runOnUiThread { controller?.pause()?.stop()?.destroy() }
    }

    @Test fun openTodoSwipesFromMetadataDespiteItsLongPressHandler() = leftSwipe(habit = false, complete = false)
    @Test fun completedTodoSwipesFromMetadata() = leftSwipe(habit = false, complete = true)
    @Test fun openHabitSwipesFromMetadata() = leftSwipe(habit = true, complete = false)
    @Test fun completedHabitSwipesFromMetadata() = leftSwipe(habit = true, complete = true)

    private fun leftSwipe(habit: Boolean, complete: Boolean) {
        var edits = 0
        var deletes = 0
        var completions = 0
        var drops = 0
        val type = if (habit) "习惯" else "待办"
        content {
            DaveDragLayer {
                Box(Modifier.width(156.dp)) {
                    if (habit) DaveSwipeHabitCard(
                        title = "测试标题 #足够长的标签", count = if (complete) 1 else 0, targetCount = 1,
                        period = HabitPeriod.DAILY, color = 0xFF8FA7E4L, isBackfilled = false,
                        onClick = { completions++ }, onEdit = { edits++ }, onDelete = { deletes++ },
                        onCompletionRequest = { completions++ }, completionCrossesDivider = true,
                        compact = true, clipCompletionToSlot = true, modifier = Modifier.testTag("tile"),
                    ) else DaveHomeSwipeTaskCard(
                        todo = TodoOccurrence(
                            id = "tile", nominalDate = LocalDate.of(2026, 9, 10), displayDate = LocalDate.of(2026, 9, 10),
                            title = "测试标题 #足够长的标签", status = if (complete) TodoStatus.COMPLETED else TodoStatus.OPEN,
                            position = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
                        ),
                        onToggleCompletion = { completions++ }, onCompletionRequest = { completions++ },
                        onEdit = { edits++ }, onDelete = { deletes++ }, onDragFinished = { drops++ },
                        compact = true, clipCompletionToSlot = true, modifier = Modifier.testTag("tile"),
                    )
                }
            }
        }
        fun reveal() {
            compose.onNodeWithTag("tile").performTouchInput {
                // The lower text rows used to own nested horizontalScroll.
                swipe(Offset(width - 8f, height * .80f), Offset(6f, height * .80f), 200L)
            }
        }
        reveal()
        val owner = compose.onNodeWithTag("tile").getUnclippedBoundsInRoot()
        listOf("编辑$type", "删除$type").forEach { description ->
            val action = compose.onNodeWithContentDescription(description).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(action.left >= owner.left && action.right <= owner.right)
            assertTrue(action.top >= owner.top && action.bottom <= owner.bottom)
        }
        compose.onNodeWithContentDescription("编辑$type").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(0, completions); assertEquals(0, drops) }
        reveal()
        compose.onNodeWithContentDescription("删除$type").performClick()
        compose.runOnIdle { assertEquals(1, deletes); assertEquals(0, completions) }
    }

    @Test fun editorTextImmediatelyUsesNewSelectedColour() {
        val selected = mutableStateOf(Color(0xFF8FA7E4))
        content { DaveInlineDraftCard("已输入标题", {}, {}, {}, autoFocus = false, accentColor = selected.value) }
        compose.runOnIdle { selected.value = Color(0xFF32775D) }
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(action(layouts))
            assertEquals(Color(0xFF32775D), layouts.single().layoutInput.style.color)
        }
    }

    @Test fun habitEditorKeepsEveryRoundActionSquareInsideAWeekWidth() {
        content {
            Box(Modifier.width(320.dp)) {
                DaveHabitEditor(
                    title = "测试",
                    period = HabitPeriod.DAILY,
                    target = 3,
                    intervalDays = 3,
                    scheduleStartDate = LocalDate.of(2026, 9, 11),
                    scheduleDays = emptySet(),
                    color = 0xFF8FA7E4L,
                    onTitleChange = {},
                    onPeriodChange = {},
                    onTargetChange = {},
                    onIntervalDaysChange = {},
                    onScheduleStartDateChange = {},
                    onScheduleDayToggle = {},
                    onColorChange = {},
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
        listOf(
            "每日目标",
            "每周目标",
            "每月指定日期",
            "减少目标次数",
            "增加目标次数",
            "固定节奏：每N天一次",
            "间隔节奏：完成后隔N天",
            "编辑习惯TAG",
        ).forEach { description ->
            val bounds = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
            assertEquals(44.dp, bounds.right - bounds.left)
            assertEquals(44.dp, bounds.bottom - bounds.top)
        }
    }

    @Test fun habitTagPanelHasBreathingRoomBelowItsButtonRow() {
        content {
            Box(Modifier.width(320.dp)) {
                DaveHabitEditor(
                    title = "测试",
                    period = HabitPeriod.DAILY,
                    target = 1,
                    intervalDays = 3,
                    scheduleStartDate = LocalDate.of(2026, 9, 11),
                    scheduleDays = emptySet(),
                    color = 0xFF8FA7E4L,
                    onTitleChange = {},
                    onPeriodChange = {},
                    onTargetChange = {},
                    onIntervalDaysChange = {},
                    onScheduleStartDateChange = {},
                    onScheduleDayToggle = {},
                    onColorChange = {},
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }

        val button = compose.onNodeWithContentDescription("编辑习惯TAG")
        button.performClick()
        val buttonBounds = button.getUnclippedBoundsInRoot()
        val panelTitleBounds = compose.onNodeWithText("自定义 TAG · 多个标签用空格分隔").getUnclippedBoundsInRoot()
        assertTrue(panelTitleBounds.top - buttonBounds.bottom >= 10.dp)
    }

    @Test fun unusedWeekHalfSlotCreatesButOccupiedCardKeepsTapAndSwipe() {
        var drafts = 0
        var completions = 0
        var edits = 0
        content {
            Box(Modifier.width(320.dp)) {
                DaveWeekDayPanel("周四", true, onBlankClick = { drafts++ }) {
                    Row(Modifier.fillMaxWidth()) {
                        DaveSwipeHabitCard("测试", 0, 1, HabitPeriod.DAILY, 0xFF769BCA, false,
                            onClick = { completions++ }, onEdit = { edits++ }, onDelete = {},
                            compact = true, clipCompletionToSlot = true,
                            modifier = Modifier.weight(1f).testTag("occupied"))
                        Box(Modifier.weight(1f).height(72.dp).testTag("vacant"))
                    }
                }
            }
        }
        compose.onNodeWithTag("vacant", useUnmergedTree = true).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, drafts) }
        compose.onNodeWithTag("occupied", useUnmergedTree = true).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, completions); assertEquals(1, drafts) }
        compose.onNodeWithTag("occupied", useUnmergedTree = true).performTouchInput { swipe(Offset(width - 8f, centerY), Offset(6f, centerY), 200) }
        compose.onNodeWithContentDescription("编辑习惯").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(1, drafts); assertEquals(1, completions) }
    }

    @Test fun cardTitlesFitInTwoMeasuredLinesWithoutGrowingTheCard() {
        val title = "这是需要完整显示的较长任务标题"
        content {
            Row {
                Box(Modifier.width(140.dp).height(72.dp).testTag("day")) {
                    DaveCardTitle(title, DavePalette.Ink, compact = false, lineThroughProgress = 0f)
                }
            }
        }
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(action(layouts))
            assertTrue(layouts.single().lineCount <= 2)
            assertTrue(!layouts.single().hasVisualOverflow)
        }
        val card = compose.onNodeWithTag("day").getUnclippedBoundsInRoot()
        assertEquals(72.dp, card.bottom - card.top)
    }

    @Test fun checklistCheckboxTogglesWithoutChangingTextOrRequestingKeyboardFocus() {
        val checked = mutableStateOf(false)
        var textChanges = 0
        content {
            DaveJournalLine(TextFieldValue("买菜"), null, JournalTextSize.BODY, emptyList(), false, 0L,
                {}, { textChanges++ }, {}, cardStyle = false,
                listStyle = JournalListStyle.CHECKLIST, isChecked = checked.value,
                onToggleChecked = { checked.value = !checked.value })
        }
        compose.onNodeWithContentDescription("勾选清单项 买菜").performClick().assertIsOn()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.runOnIdle { assertTrue(checked.value); assertEquals(0, textChanges) }
    }

    @Test fun compactLandingKeepsClearVisibleWhenPointerOwnershipReturns() {
        val enabled = mutableStateOf(false)
        content {
            Box(Modifier.width(156.dp)) {
                DaveSwipeHabitCard("落地", 1, 1, HabitPeriod.DAILY, 0xFF769BCA, false,
                    onClick = {}, onEdit = {}, onDelete = {},
                    interactionsEnabled = enabled.value, completionStampInitiallyVisible = true,
                    compact = true, clipCompletionToSlot = true)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("CLEAR", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnUiThread { enabled.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("CLEAR", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun aSmallLeftSwipeAndReturnKeepCompletedHabitMetadataInTheSameLayout() {
        var completions = 0
        content {
            Box(Modifier.width(156.dp)) {
                DaveSwipeHabitCard("轻滑回弹", 1, 1, HabitPeriod.DAILY, 0xFF769BCA, false,
                    onClick = { completions++ }, onEdit = {}, onDelete = {},
                    onCompletionRequest = { completions++ }, completionCrossesDivider = true,
                    compact = true, clipCompletionToSlot = true, modifier = Modifier.testTag("habit"))
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("habit").performTouchInput {
            // Above touch slop but well below the left-action opening threshold.
            swipe(Offset(width * .60f, centerY), Offset(width * .45f, centerY), 160)
        }
        repeat(18) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("CLEAR", useUnmergedTree = true).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("编辑习惯").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, completions) }
    }

    @Test fun nearRestUndoPreviewKeepsTheSameClearMetadataSlot() {
        val progress = mutableStateOf<Float?>(null)
        content {
            Box(Modifier.width(156.dp)) {
                DaveHabitCard("归位边界", 1, 1, HabitPeriod.DAILY, 0xFF769BCA, false,
                    onClick = {}, compact = true, completionGestureProgress = progress.value)
            }
        }
        val restingBounds = compose.onNodeWithText("CLEAR", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // This used to cross visuallyComplete's .999 gate and remove the node.
        compose.runOnIdle { progress.value = -.005f }
        val returning = compose.onNodeWithText("CLEAR", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(restingBounds, returning.getUnclippedBoundsInRoot())
        compose.runOnIdle { progress.value = null }
        assertEquals(restingBounds, compose.onNodeWithText("CLEAR", useUnmergedTree = true).getUnclippedBoundsInRoot())
    }
}
