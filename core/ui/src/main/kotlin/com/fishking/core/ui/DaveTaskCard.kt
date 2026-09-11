@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fishking.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.fishking.core.model.TodoOccurrence
import com.fishking.core.model.TodoPriority
import com.fishking.core.model.TodoStatus
import com.fishking.core.model.TodoChangeScope
import com.fishking.core.model.RecurrenceFrequency
import com.fishking.core.model.HabitPeriod
import java.time.LocalTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun DaveHabitCard(
    title: String,
    count: Int,
    targetCount: Int,
    period: HabitPeriod,
    color: Long,
    isBackfilled: Boolean,
    checkedOnDate: Boolean = count > 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    dragGroup: String = "habit-home",
    dragId: String = title,
    intervalDays: Int = 1,
    completionStampInitiallyVisible: Boolean = false,
    checkAnimationMillis: Int = 300,
    completionGestureProgress: Float? = null,
    compact: Boolean = false,
) {
    val clickSource = remember { MutableInteractionSource() }
    val progressColor = Color(color)
    val complete = if (period == HabitPeriod.DAILY) count >= targetCount else checkedOnDate
    val gestureProgress = completionGestureProgress?.coerceIn(-1f, 1f)
    val gestureVisual = daveCompletionGestureVisual(complete, gestureProgress)
    val visuallyComplete = gestureVisual.completion >= .999f
    val titleParts = remember(title) { splitDaveTaskText(title) }
    val lift = rememberDaveLiftModifier(
        key = dragId,
        enabled = !readOnly && onDragFinished != null,
        draw = { DaveHabitCard(title, count, targetCount, period, color, isBackfilled, checkedOnDate, {}, readOnly = true, dragId = dragId, intervalDays = intervalDays, checkAnimationMillis = checkAnimationMillis) },
        onPosition = onDragPosition,
        onDrop = onDragFinished,
        group = dragGroup,
    )
    Row(
        modifier = modifier.then(lift)
            .fillMaxWidth()
            .height(DaveTaskCardHeight)
            .background(DavePalette.Card, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .then(
                if (readOnly) Modifier else Modifier.clickable(
                    interactionSource = clickSource,
                    indication = null,
                    onClick = onClick,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(if (compact) 6.dp else 14.dp)
                .height(DaveTaskCardHeight)
                .background(progressColor, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)),
        )
        Box(
            modifier = Modifier
                .padding(start = if (compact) 8.dp else 17.dp)
                .size(if (compact) 22.dp else 34.dp)
                .background(progressColor.copy(alpha = gestureVisual.fill), CircleShape)
                .then(
                    Modifier.border(if (compact) 2.dp else 3.dp, progressColor, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (gestureProgress != null && gestureVisual.check > 0f) {
                DaveDrawnCheck(Color.White, gestureVisual.check, Modifier.size(if (compact) 18.dp else 28.dp))
            } else if (gestureProgress == null && complete) {
                DaveDrawnCheck(Color.White, 1f, Modifier.size(if (compact) 18.dp else 28.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (compact) 7.dp else 14.dp, end = if (compact) 6.dp else 8.dp)) {
            DaveCardTitle(
                text = titleParts.title,
                color = progressColor,
                compact = compact,
                lineThroughProgress = gestureVisual.completion,
            )
            if (compact) {
                CompactCardMetadata(
                    progress = "$count/$targetCount" + if (isBackfilled) " 补" else "",
                    tags = (listOf(habitPeriodTag(period, intervalDays)) + titleParts.tags).distinct().joinToString(" ") { "#$it" },
                    color = progressColor,
                    complete = visuallyComplete,
                    completionAlpha = gestureVisual.check,
                )
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$count/$targetCount", color = progressColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text((listOf(habitPeriodTag(period, intervalDays)) + titleParts.tags).distinct().joinToString(" ") { "#$it" }, color = progressColor, fontSize = 12.sp, maxLines = 1)
                if (isBackfilled) Text("补", color = DavePalette.Meta, fontSize = 10.sp)
            }
        }
        if (!compact) AnimatedVisibility(
            visibleState = rememberCompletionStamp(visuallyComplete, completionStampInitiallyVisible),
            enter = fadeIn(tween(CompletionTransitionMillis.toInt())) + scaleIn(tween(CompletionTransitionMillis.toInt()), initialScale = .55f),
        ) {
            Box(
                modifier = Modifier
                    .rotate(8f)
                    .border(2.dp, progressColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text("CLEAR", color = progressColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!compact) Spacer(Modifier.width(12.dp))
    }
}

/** Single-day home projection: right swipe checks in, left swipe reveals edit/skip/delete. */
@Composable
fun DaveSwipeHabitCard(
    title: String,
    count: Int,
    targetCount: Int,
    period: HabitPeriod,
    color: Long,
    isBackfilled: Boolean,
    checkedOnDate: Boolean = count > 0,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onDragFinished: ((Offset) -> Unit)? = null,
    dragGroup: String = "habit-home",
    dragId: String = title,
    intervalDays: Int = 1,
    onCompletionRequest: ((DaveTaskCompletionRequest) -> Unit)? = null,
    completionCrossesDivider: Boolean = false,
    interactionsEnabled: Boolean = true,
    completionStampInitiallyVisible: Boolean = false,
    checkAnimationMillis: Int = 300,
    compact: Boolean = false,
    clipCompletionToSlot: Boolean = false,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val leftActionsEnabled = onEdit != null || onDelete != null
    val actionWidth = with(density) { if (leftActionsEnabled) 112.dp.toPx() else 0f }
    val scope = rememberCoroutineScope()
    val currentClick by rememberUpdatedState(onClick)
    val currentCompletionRequest by rememberUpdatedState(onCompletionRequest)
    val currentlyComplete = if (period == HabitPeriod.DAILY) count >= targetCount else checkedOnDate
    // Ownership changes synchronously: a scene landing must never render one
    // frame with the previous finger offset/check progress before an effect runs.
    val swipeState = remember(dragId, currentlyComplete, interactionsEnabled) { DaveHabitSwipeState() }
    val offsetX = swipeState.offsetPx
    var completionFlight by remember(dragId, currentlyComplete, interactionsEnabled) { mutableStateOf(false) }
    var cardBounds by remember(dragId) { mutableStateOf(Rect.Zero) }
    var cardWidthPx by remember(dragId) { mutableStateOf(0f) }
    var settleJob by remember(dragId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val measuredWidth = cardWidthPx.takeIf { it > 0f } ?: with(density) { 360.dp.toPx() }
    val completionThreshold = measuredWidth * .30f
    val completionProgress = (offsetX / completionThreshold).coerceIn(0f, 1f)
    val actionCornerBridgePx = with(density) { 13.dp.toPx() }

    DisposableEffect(swipeState) {
        // The new owner starts at rest synchronously. An old return animation
        // must not reset its successor one frame after a completion lands.
        onDispose { settleJob?.cancel() }
    }

    fun settle(target: Float, after: (() -> Unit)? = null) {
        settleJob?.cancel()
        val settling = swipeState.beginSettle(target)
        settleJob = scope.launch {
            // A spring previously crossed zero, briefly turning a left return
            // into completion feathering (and a right return into an action sheet).
            animate(0f, 1f, animationSpec = tween(180)) { fraction, _ ->
                swipeState.settleFrame(settling, fraction)
            }
            if (swipeState.finishSettle(settling)) after?.invoke()
        }
    }

    fun completeFromSwipe() {
        if (completionFlight) return
        if (completionCrossesDivider && currentCompletionRequest != null && cardBounds.width > 0f) {
            settleJob?.cancel()
            swipeState.handOff()
            completionFlight = true
            currentCompletionRequest?.invoke(
                DaveTaskCompletionRequest(
                    bounds = cardBounds,
                    offsetX = swipeState.offsetPx,
                    gestureProgress = (swipeState.offsetPx / ((cardWidthPx.takeIf { it > 0f } ?: measuredWidth) * .30f)).coerceIn(0f, 1f),
                ),
            )
        } else {
            currentClick()
            settle(0f)
        }
    }

    fun toggleFromTap() {
        if (completionCrossesDivider && currentCompletionRequest != null && cardBounds.width > 0f) {
            currentCompletionRequest?.invoke(DaveTaskCompletionRequest(cardBounds, 0f, 0f))
        } else {
            currentClick()
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(DaveTaskCardHeight)
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .completionSlotClip(
                feather = interactionsEnabled && clipCompletionToSlot && offsetX > 0f,
                enabled = clipCompletionToSlot || offsetX < 0f,
                featherProgress = daveHabitSwipeFeather(offsetX, with(density) { 22.dp.toPx() }),
                stableCompositing = clipCompletionToSlot,
            )
            .then(
                if (!interactionsEnabled) Modifier else Modifier.pointerInput(swipeState, checkedOnDate, completionCrossesDivider, actionWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            swipeState.beginDrag()
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (!completionFlight) {
                                swipeState.dragBy(amount, actionWidth)
                            }
                        },
                        onDragEnd = {
                            if (!completionFlight) {
                                when {
                                    swipeState.direction == DaveHabitSwipeDirection.RIGHT && swipeState.offsetPx >= (cardWidthPx.takeIf { it > 0f } ?: measuredWidth) * .30f -> completeFromSwipe()
                                    leftActionsEnabled && swipeState.offsetPx <= -actionWidth * .42f -> settle(-actionWidth)
                                    else -> settle(0f)
                                }
                            }
                        },
                        onDragCancel = { if (!completionFlight) settle(0f) },
                    )
                },
            ),
    ) {
        if (interactionsEnabled && leftActionsEnabled && swipeState.direction == DaveHabitSwipeDirection.LEFT && !completionFlight) {
            // Start the backing layer one corner radius behind the translated card.
            // Otherwise its rounded trailing corners reveal two wedges of page colour.
            Box(Modifier.matchParentSize().drawWithContent {
                clipRect(
                    left = daveHabitActionRevealLeft(size.width, swipeState.offsetPx, actionCornerBridgePx),
                ) { this@drawWithContent.drawContent() }
            }) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(13.dp)).background(DavePalette.Card))
            DaveCardActionStrip(
                enabled = swipeState.phase == DaveHabitSwipePhase.ACTIONS,
                actions = buildList {
                    onEdit?.let { edit -> add(DaveCardAction(Icons.Outlined.Edit, "编辑习惯", DavePalette.HeaderGreen) { settle(0f, edit) }) }
                    onDelete?.let { delete -> add(DaveCardAction(Icons.Outlined.DeleteOutline, "删除习惯", DavePalette.Urgent) { settle(0f, delete) }) }
                },
                modifier = Modifier.align(Alignment.CenterEnd).width(112.dp).height(DaveTaskCardHeight),
            )
            }
        }
        DaveHabitCard(
            title = title,
            count = count,
            targetCount = targetCount,
            period = period,
            color = color,
            isBackfilled = isBackfilled,
            checkedOnDate = checkedOnDate,
            onClick = { if (swipeState.phase != DaveHabitSwipePhase.REST) settle(0f) else toggleFromTap() },
            readOnly = !interactionsEnabled,
            onDragFinished = onDragFinished,
            dragGroup = dragGroup,
            dragId = dragId,
            intervalDays = intervalDays,
            completionStampInitiallyVisible = completionStampInitiallyVisible,
            checkAnimationMillis = checkAnimationMillis,
            compact = compact,
            modifier = Modifier
                .zIndex(1f)
                .graphicsLayer { translationX = if (interactionsEnabled) offsetX else 0f; clip = false },
            completionGestureProgress = if (!interactionsEnabled || !completionCrossesDivider || completionProgress <= 0f) null
                else if (currentlyComplete) -completionProgress else completionProgress,
        )
    }
}

@Composable
fun DaveTaskCard(
    todo: TodoOccurrence,
    onToggleCompletion: () -> Unit,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    completionGestureProgress: Float? = null,
    completionStampInitiallyVisible: Boolean = false,
    checkAnimationMillis: Int = 300,
    compact: Boolean = false,
) {
    val clickSource = remember { MutableInteractionSource() }
    var dragOffset by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var cardBounds by remember(todo.id) { mutableStateOf(Rect.Zero) }
    var dragStartPointer by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragging by remember(todo.id) { mutableStateOf(false) }
    val dragLayer = LocalDaveDragController.current
    val currentDragPosition by rememberUpdatedState(onDragPosition)
    val currentDragFinished by rememberUpdatedState(onDragFinished)
    val completed = todo.isCompleted
    val gestureProgress = completionGestureProgress?.coerceIn(-1f, 1f)
    val gestureVisual = daveCompletionGestureVisual(completed, gestureProgress)
    val visuallyComplete = gestureVisual.completion >= .999f
    DisposableEffect(todo.id) { onDispose { if (dragging) dragLayer?.finish() } }
    val stripe = todo.accentColor?.let(::Color) ?: when {
        todo.fromLifeGoal -> DavePalette.Life
        todo.planScope != com.fishking.core.model.TodoPlanScope.DATE -> DavePalette.Plan
        todo.priority == TodoPriority.URGENT -> DavePalette.Urgent
        else -> DavePalette.Normal
    }
    val cardColor = DavePalette.Card
    val foreground = todo.accentColor?.let(::Color) ?: DavePalette.Ink
    val markColor = todo.accentColor?.let(::Color) ?: if (completed) DavePalette.Completed else Color.Black
    val completedMarkColor = todo.accentColor?.let(::Color) ?: DavePalette.Completed
    val titleParts = remember(todo.title) { splitDaveTaskText(todo.title) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DaveTodoCardHeight)
            .zIndex(if (dragging) 5f else 0f)
            .graphicsLayer {
                alpha = if (dragging && dragLayer != null) 0f else 1f
                translationX = if (dragLayer == null) dragOffset.x else 0f
                translationY = if (dragLayer == null) dragOffset.y else 0f
                shadowElevation = if (dragging) 12.dp.toPx() else 0f
            }
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .clip(RoundedCornerShape(13.dp))
            .background(cardColor, RoundedCornerShape(13.dp))
            .then(
                if (readOnly) Modifier else Modifier.clickable(
                    interactionSource = clickSource,
                    indication = null,
                    onClick = onToggleCompletion,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(if (compact) 6.dp else 14.dp)
                .height(DaveTodoCardHeight)
                .background(stripe, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
                .then(
                    if (!readOnly && !todo.isCompleted && onDragFinished != null) Modifier.pointerInput(todo.id, todo.isCompleted) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                dragging = true
                                dragOffset = Offset.Zero
                                dragStartPointer = cardBounds.topLeft + local
                                dragLayer?.start(cardBounds) { DaveTaskCard(todo, {}, readOnly = true) }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                dragLayer?.move(dragOffset)
                                currentDragPosition?.invoke(dragStartPointer + dragOffset)
                            },
                            onDragEnd = {
                                val dropPoint = dragStartPointer + dragOffset
                                dragging = false
                                dragOffset = Offset.Zero
                                dragLayer?.finish()
                                currentDragFinished?.invoke(dropPoint)
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = Offset.Zero
                                dragLayer?.finish()
                            },
                        )
                    } else Modifier,
                )
                .semantics { if (onDragFinished != null) contentDescription = "长按拖动待办" },
        )
        Box(
            modifier = Modifier
                .padding(start = if (compact) 8.dp else 17.dp)
                .size(if (compact) 22.dp else 34.dp)
                .background(completedMarkColor.copy(alpha = gestureVisual.fill), CircleShape)
                .border(if (compact) 2.dp else 3.dp, if (gestureVisual.fill > 0f) completedMarkColor else markColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (gestureProgress != null && gestureVisual.check > 0f) {
                DaveDrawnCheck(Color.White, gestureVisual.check, Modifier.size(if (compact) 18.dp else 28.dp))
            } else if (gestureProgress == null && completed) {
                DaveDrawnCheck(Color.White, 1f, Modifier.size(if (compact) 18.dp else 28.dp))
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = if (compact) 7.dp else 14.dp, end = if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            DaveCardTitle(
                text = titleParts.title,
                color = foreground,
                compact = compact,
                lineThroughProgress = gestureVisual.completion,
            )
            if (compact) {
                CompactCardMetadata(
                    progress = (if (completed) "1/1" else "0/1") + todoTimeLabel(todo).takeIf(String::isNotBlank)?.let { " $it" }.orEmpty(),
                    tags = (listOf(todoTypeTag(todo)) + listOfNotNull(todoDeadlineTag(todo)) + titleParts.tags).distinct().joinToString(" ") { "#$it" },
                    color = todo.accentColor?.let(::Color) ?: DavePalette.Meta,
                    complete = visuallyComplete,
                )
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (completed) "1/1" else "0/1", color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
                if (todoTimeLabel(todo).isNotEmpty()) DaveTodoTime(todo, {}, modifier = Modifier.alignByBaseline())
                Text(
                    (listOf(todoTypeTag(todo)) + listOfNotNull(todoDeadlineTag(todo)) + titleParts.tags)
                        .distinct().joinToString(" ") { "#$it" },
                    color = todo.accentColor?.let(::Color) ?: DavePalette.Meta,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
        }
        if (!compact) AnimatedVisibility(
            visibleState = rememberCompletionStamp(visuallyComplete, completionStampInitiallyVisible),
            enter = fadeIn(tween(430)) + scaleIn(tween(430), initialScale = .55f),
        ) {
            Box(
                modifier = Modifier
                    .rotate(8f)
                    .border(2.dp, completedMarkColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text("CLEAR", color = completedMarkColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!compact) Spacer(Modifier.width(12.dp))
    }
}

/** Exact hand-off from the stationary gesture surface to the home overlay. */
data class DaveTaskCompletionRequest(
    val bounds: Rect,
    val offsetX: Float,
    val gestureProgress: Float,
)

/** Half-width cards reserve their title width; CLEAR shares only the small progress row. */
@Composable
private fun CompactCardMetadata(
    progress: String,
    tags: String,
    color: Color,
    complete: Boolean,
    completionAlpha: Float? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(progress, color = color, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
            modifier = Modifier.weight(1f))
        // Habit undo previews keep one layout from the first pixel to rest.
        // Toggling this Text at .999 removed/reinserted its entire metadata slot.
        if (complete || completionAlpha != null) Text("CLEAR", color = color, fontSize = 8.sp, lineHeight = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier
                .graphicsLayer { alpha = completionAlpha ?: 1f }
                .then(if (completionAlpha != null && completionAlpha <= 0f) Modifier.clearAndSetSemantics { } else Modifier)
                .rotate(8f)
                .border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp))
    }
    // A half-width card owns its horizontal gesture across the whole face.
    // Nested horizontalScroll here consumed drags starting on count/TAG rows,
    // making both todo and habit cards appear impossible to left-swipe.
    Text(tags, color = color, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
        modifier = Modifier.fillMaxWidth())
}

/** Fade belongs to the stationary slot, so a left-column flight cannot cover its neighbour. */
private fun Modifier.completionSlotClip(
    feather: Boolean,
    enabled: Boolean = true,
    featherProgress: Float = 1f,
    stableCompositing: Boolean = false,
): Modifier =
    graphicsLayer {
        clip = enabled
        compositingStrategy = if (stableCompositing || feather) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
        .drawWithContent {
            drawContent()
            if (feather) {
                val edge = (1f - 22.dp.toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                drawRect(Brush.horizontalGradient(0f to Color.Black, edge to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - featherProgress.coerceIn(0f, 1f))), blendMode = BlendMode.DstIn)
            }
        }

internal data class DaveCompletionGestureVisual(
    val completion: Float,
    val fill: Float,
    val check: Float,
)

/** 20-25% of card width fills the circle; 25-30% draws the check. */
internal fun daveCompletionGestureVisual(
    completed: Boolean,
    gestureProgress: Float?,
): DaveCompletionGestureVisual {
    val completion = when {
        gestureProgress == null -> if (completed) 1f else 0f
        gestureProgress < 0f -> 1f - abs(gestureProgress.coerceIn(-1f, 0f))
        else -> gestureProgress.coerceIn(0f, 1f)
    }
    return DaveCompletionGestureVisual(
        completion = completion,
        fill = ((completion - 2f / 3f) * 6f).coerceIn(0f, 1f),
        check = ((completion - 5f / 6f) * 6f).coerceIn(0f, 1f),
    )
}

/** Pure rendering: the home scene owns the only flight clock and persistence. */
@Composable
fun DaveCompletionFlight(
    todo: TodoOccurrence,
    request: DaveTaskCompletionRequest,
    containerBounds: Rect,
    progress: Float,
    checkAnimationMillis: Int = 300,
    compact: Boolean = false,
    clipToSlot: Boolean = false,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val exitOffset = (if (clipToSlot) request.bounds.width else maxOf(request.bounds.width, containerBounds.right - request.bounds.left)) +
        with(density) { 24.dp.toPx() }
    val flightProgress = progress.coerceIn(0f, 1f)
    val horizontalOffset = request.offsetX +
        (exitOffset - request.offsetX).coerceAtLeast(0f) * flightProgress
    val checkProgress = request.gestureProgress + (1f - request.gestureProgress) * flightProgress
    Box(
        Modifier
            .offset {
                IntOffset(
                    (request.bounds.left - containerBounds.left).roundToInt(),
                    (request.bounds.top - containerBounds.top).roundToInt(),
                )
            }
            .width(with(density) { request.bounds.width.toDp() })
            .height(with(density) { request.bounds.height.toDp() })
            .then(if (clipToSlot) Modifier.completionSlotClip(horizontalOffset > 0f) else Modifier)
            .graphicsLayer { alpha = if (flightProgress >= 1f) 0f else 1f }
            .zIndex(20f),
    ) {
        DaveTaskCard(
            todo = todo,
            onToggleCompletion = {},
            readOnly = true,
            completionGestureProgress = if (todo.isCompleted) -checkProgress else checkProgress,
            // If the drag already crossed the completion threshold, CLEAR is
            // a continuation of the same pixels rather than a fresh reveal on
            // the proxy. A tap starts at zero and may reveal it during flight.
            completionStampInitiallyVisible = !todo.isCompleted && request.gestureProgress >= .999f,
            checkAnimationMillis = checkAnimationMillis,
            compact = compact,
            modifier = Modifier.graphicsLayer { translationX = horizontalOffset },
        )
    }
}

/** Home-only flight renderer for a habit card. The parent still owns the clock and commit. */
@Composable
fun DaveHabitCompletionFlight(
    title: String,
    count: Int,
    targetCount: Int,
    period: HabitPeriod,
    color: Long,
    isBackfilled: Boolean,
    checkedOnDate: Boolean,
    intervalDays: Int,
    request: DaveTaskCompletionRequest,
    containerBounds: Rect,
    progress: Float,
    completing: Boolean,
    checkAnimationMillis: Int = 300,
    compact: Boolean = false,
    clipToSlot: Boolean = false,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val exitOffset = (if (clipToSlot) request.bounds.width else maxOf(request.bounds.width, containerBounds.right - request.bounds.left)) +
        with(density) { 24.dp.toPx() }
    val flightProgress = progress.coerceIn(0f, 1f)
    val checkProgress = request.gestureProgress + (1f - request.gestureProgress) * flightProgress
    val horizontalOffset = request.offsetX +
        (exitOffset - request.offsetX).coerceAtLeast(0f) * flightProgress
    Box(
        Modifier
            .offset {
                IntOffset(
                    (request.bounds.left - containerBounds.left).roundToInt(),
                    (request.bounds.top - containerBounds.top).roundToInt(),
                )
            }
            .width(with(density) { request.bounds.width.toDp() })
            .height(with(density) { request.bounds.height.toDp() })
            .then(if (clipToSlot) Modifier.completionSlotClip(horizontalOffset > 0f) else Modifier)
            .graphicsLayer { alpha = if (flightProgress >= 1f) 0f else 1f }
            .zIndex(20f),
    ) {
        DaveHabitCard(
            title = title,
            count = count,
            targetCount = targetCount,
            period = period,
            color = color,
            isBackfilled = isBackfilled,
            checkedOnDate = checkedOnDate,
            onClick = {},
            readOnly = true,
            intervalDays = intervalDays,
            completionStampInitiallyVisible = completing && request.gestureProgress >= .999f,
            checkAnimationMillis = checkAnimationMillis,
            completionGestureProgress = if (completing) checkProgress else -checkProgress,
            compact = compact,
            modifier = Modifier.graphicsLayer { translationX = horizontalOffset },
        )
    }
}

@Composable
fun DaveHomeSwipeTaskCard(
    todo: TodoOccurrence,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    dragGroup: String = todo.displayDate.toString() + ":open",
    modifier: Modifier = Modifier,
    onSetAccentColor: ((Long?) -> Unit)? = null,
    onOpenArrange: (() -> Unit)? = null,
    onOpenTags: (() -> Unit)? = null,
    /** The single-day parent owns all animation and the single data commit. */
    onCompletionRequest: (DaveTaskCompletionRequest) -> Unit,
    interactionsEnabled: Boolean = true,
    completionStampInitiallyVisible: Boolean = false,
    checkAnimationMillis: Int = 300,
    compact: Boolean = false,
    clipCompletionToSlot: Boolean = false,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidth = with(density) { 112.dp.toPx() }
    val scope = rememberCoroutineScope()
    val currentCompletionRequest by rememberUpdatedState(onCompletionRequest)
    val currentToggleCompletion by rememberUpdatedState(onToggleCompletion)
    var offsetX by remember(todo.id, todo.isCompleted, interactionsEnabled) { mutableStateOf(0f) }
    var paletteVisible by remember(todo.id) { mutableStateOf(false) }
    var completionFlight by remember(todo.id, todo.isCompleted, interactionsEnabled) { mutableStateOf(false) }
    var cardBounds by remember(todo.id) { mutableStateOf(Rect.Zero) }
    var cardWidthPx by remember(todo.id) { mutableStateOf(0f) }
    var settleJob by remember(todo.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Direction is locked for one pointer gesture. Closing a left-hand action
    // sheet cannot accidentally become completion when the finger crosses zero.
    var swipeDirection by remember(todo.id) { mutableStateOf(0) }
    val measuredWidth = cardWidthPx.takeIf { it > 0f } ?: with(density) { 360.dp.toPx() }
    val completionThreshold = measuredWidth * .30f
    val completionProgress = (offsetX / completionThreshold).coerceIn(0f, 1f)

    LaunchedEffect(todo.id, todo.isCompleted, interactionsEnabled) {
        settleJob?.cancel()
        offsetX = 0f
        completionFlight = false
        paletteVisible = false
        swipeDirection = 0
    }

    fun settle(target: Float, after: (() -> Unit)? = null) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(offsetX, target, animationSpec = tween(180)) { value, _ -> offsetX = value }
            after?.invoke()
        }
    }

    fun completeFromSwipe() {
        if (completionFlight) return
        settleJob?.cancel()
        completionFlight = true
        paletteVisible = false
        val actualWidth = cardWidthPx.takeIf { it > 0f } ?: measuredWidth
        val request = DaveTaskCompletionRequest(
            cardBounds,
            offsetX,
            (offsetX / (actualWidth * .30f)).coerceIn(0f, 1f),
        )
        if (cardBounds.width > 0f) {
            // Synchronous transfer, with the finger's exact final offset. The
            // source composable may now disappear without cancelling the
            // scene's flight or firing a second completion callback.
            currentCompletionRequest(request)
        } else {
            completionFlight = false
            settle(0f)
        }
    }

    val lift = rememberDaveLiftModifier(
        todo.id,
        interactionsEnabled && !todo.isCompleted && !completionFlight,
        { DaveTaskCard(todo, {}, readOnly = true, compact = compact) },
        onDragPosition,
        onDragFinished,
        dragGroup,
        previewReorder = !clipCompletionToSlot,
    )
    // This is the STATIONARY pointer surface. The old code put pointerInput
    // after the card's offset, changing pointer coordinates while dragging and
    // making the card feel pinned part-way across the screen.
    val swipe = if (!interactionsEnabled) Modifier else Modifier.pointerInput(todo.id, todo.isCompleted) {
        detectHorizontalDragGestures(
            onDragStart = {
                settleJob?.cancel()
                swipeDirection = if (offsetX < -1f) -1 else 0
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                if (!completionFlight) {
                    if (swipeDirection == 0) swipeDirection = if (amount >= 0f) 1 else -1
                    offsetX = if (swipeDirection > 0) {
                        // There is deliberately no right-hand displacement
                        // clamp: the threshold only decides on finger-up.
                        (offsetX + amount).coerceAtLeast(0f)
                    } else {
                        (offsetX + amount).coerceIn(-actionWidth, 0f)
                    }
                }
            },
            onDragEnd = {
                if (!completionFlight) {
                    val threshold = (cardWidthPx.takeIf { it > 0f } ?: measuredWidth) * .30f
                    when {
                        swipeDirection > 0 && offsetX >= threshold -> completeFromSwipe()
                        offsetX <= -actionWidth * .48f -> settle(-actionWidth)
                        else -> settle(0f)
                    }
                }
            },
            onDragCancel = { if (!completionFlight) settle(0f) },
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(DaveTaskCardHeight)
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionInRoot()
                cardBounds = Rect(
                    topLeft.x,
                    topLeft.y,
                    topLeft.x + coordinates.size.width,
                    topLeft.y + coordinates.size.height,
                )
            }
            .then(lift)
            .completionSlotClip(
                feather = interactionsEnabled && clipCompletionToSlot && offsetX > 0f,
                enabled = clipCompletionToSlot || offsetX < 0f,
            )
            .then(swipe),
    ) {
        // No hidden coloured sheet is left under a right-moving card. Only
        // left swipes compose this sheet, within the same slot and clip.
        if (interactionsEnabled && offsetX < 0f && !completionFlight) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(13.dp)).background(DavePalette.Card))
            DaveCardActionStrip(
                actions = listOf(
                    DaveCardAction(Icons.Outlined.Edit, "编辑待办", DavePalette.HeaderGreen) { settle(0f, onEdit) },
                    DaveCardAction(Icons.Outlined.DeleteOutline, "删除待办", DavePalette.Urgent) { settle(0f, onDelete) },
                ),
                modifier = Modifier.align(Alignment.CenterEnd).width(112.dp).height(DaveTaskCardHeight),
            )
        }
        DaveTaskCard(
            todo = todo,
            onToggleCompletion = {
                if (!completionFlight) {
                    if (abs(offsetX) > 1f) settle(0f)
                    else if (cardBounds.width > 0f) currentCompletionRequest(
                        DaveTaskCompletionRequest(cardBounds, 0f, 0f),
                    ) else currentToggleCompletion()
                }
            },
            readOnly = !interactionsEnabled,
            completionStampInitiallyVisible = completionStampInitiallyVisible,
            checkAnimationMillis = checkAnimationMillis,
            compact = compact,
            modifier = Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX = if (interactionsEnabled) offsetX else 0f
                    // Left-reveal content stays inside its original slot;
                    // rightward flight is never clipped by the slot shape.
                    clip = false
                },
            completionGestureProgress = if (!interactionsEnabled) null else if (todo.isCompleted) {
                if (completionProgress > 0f) -completionProgress else null
            } else completionProgress.takeIf { it > 0f },
        )
    }
}

@Composable
internal fun DaveSwipeActionButton(
    color: Color,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = .13f).compositeOver(DavePalette.CardMuted), CircleShape)
            .border(1.5.dp, color.copy(alpha = .58f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = .92f), modifier = Modifier.size(22.dp))
    }
}

/**
 * Existing standalone swipe card, retained for week/other callers.  The
 * coordinated single-day experiment uses DaveHomeSwipeTaskCard instead.
 */
@Composable
fun DaveSwipeTaskCard(
    todo: TodoOccurrence,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: ((Offset) -> Unit)? = null,
    dragGroup: String = todo.displayDate.toString() + ":open",
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidth = with(density) { 112.dp.toPx() }
    val scope = rememberCoroutineScope()
    var offsetX by remember(todo.id) { mutableStateOf(0f) }
    var completionFlight by remember(todo.id) { mutableStateOf(false) }
    var cardWidthPx by remember(todo.id) { mutableStateOf(0f) }
    val measuredWidth = cardWidthPx.takeIf { it > 0f } ?: with(density) { 360.dp.toPx() }
    val completionThreshold = measuredWidth * .30f
    val completionProgress = (offsetX / completionThreshold).coerceIn(0f, 1f)
    LaunchedEffect(todo.id, todo.isCompleted) {
        offsetX = 0f
        completionFlight = false
    }

    fun settle(target: Float, after: (() -> Unit)? = null) {
        scope.launch {
            animate(offsetX, target, animationSpec = tween(180)) { value, _ -> offsetX = value }
            after?.invoke()
        }
    }

    fun completeFromSwipe() {
        if (completionFlight) return
        completionFlight = true
        scope.launch {
            val exitTarget = measuredWidth + with(density) { 32.dp.toPx() }
            animate(offsetX, exitTarget, animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = .82f,
                stiffness = 520f,
            )) { value, _ -> offsetX = value }
            onToggleCompletion()
        }
    }

    val lift = rememberDaveLiftModifier(todo.id, !todo.isCompleted,
        { DaveTaskCard(todo, {}, readOnly = true) }, onDragPosition, onDragFinished, dragGroup)
    Box(modifier = modifier.fillMaxWidth().height(DaveTodoCardHeight).then(lift).clip(RoundedCornerShape(13.dp))) {
        if (offsetX < -1f && !completionFlight) {
            Box(Modifier.matchParentSize().background(DavePalette.Card))
            DaveCardActionStrip(
                actions = listOf(
                    DaveCardAction(Icons.Outlined.Edit, "编辑待办", DavePalette.HeaderGreen) { settle(0f, onEdit) },
                    DaveCardAction(Icons.Outlined.DeleteOutline, "删除待办", DavePalette.Urgent) { settle(0f, onDelete) },
                ),
                modifier = Modifier.align(Alignment.CenterEnd).width(112.dp).height(DaveTodoCardHeight),
            )
        }
        DaveTaskCard(
            todo = todo,
            onToggleCompletion = {
                if (abs(offsetX) > 1f) settle(0f) else onToggleCompletion()
            },
            onDragPosition = null,
            onDragFinished = null,
            modifier = Modifier
                .zIndex(1f)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                .pointerInput(todo.id, todo.isCompleted, cardWidthPx.roundToInt()) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (completionFlight) return@detectHorizontalDragGestures
                            val maxRight = measuredWidth + with(density) { 32.dp.toPx() }
                            offsetX = (offsetX + amount).coerceIn(-actionWidth, maxRight)
                        },
                        onDragEnd = {
                            if (completionFlight) return@detectHorizontalDragGestures
                            when {
                                offsetX >= completionThreshold -> completeFromSwipe()
                                offsetX <= -actionWidth * .48f -> settle(-actionWidth)
                                else -> settle(0f)
                            }
                        },
                        onDragCancel = { if (!completionFlight) settle(0f) },
                    )
                },
            completionGestureProgress = if (todo.isCompleted) -completionProgress else completionProgress.takeIf { it > 0f },
        )
    }
}

@Composable
fun DaveTodoQuickOptions(
    recurrence: RecurrenceFrequency,
    reminderTimes: List<LocalTime>,
    onRecurrenceSelected: (RecurrenceFrequency) -> Unit,
    onReminderAdd: () -> Unit,
    onReminderEdit: (LocalTime) -> Unit,
    onReminderRemove: (LocalTime) -> Unit,
    selectedAccent: Long? = null,
    onAccentSelected: ((Long?) -> Unit)? = null,
    onReminderToggled: ((LocalTime) -> Unit)? = null,
    editingTitle: String? = null,
    onEditingTitleChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var tagsExpanded by remember { mutableStateOf(false) }
    var planningExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onAccentSelected != null) {
            DaveMacaronPalette(
                selected = selectedAccent,
                onSelected = onAccentSelected,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickIconOption(
                icon = Icons.Outlined.AddAlarm,
                description = "添加通知提醒日期和时间",
                selected = reminderTimes.isNotEmpty(),
                onClick = onReminderAdd,
            )
            DavePlanningOption(planningExpanded) {
                planningExpanded = it
                if (it) tagsExpanded = false
            }
            if (editingTitle != null && onEditingTitleChange != null) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .clip(CircleShape)
                        .background(if (tagsExpanded) DavePalette.HeaderGreen else Color.White.copy(alpha = .5f), CircleShape)
                        .border(1.dp, DavePalette.Divider, CircleShape)
                        .clickable {
                            tagsExpanded = !tagsExpanded
                        }
                        .semantics { contentDescription = "编辑待办TAG" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#", color = if (tagsExpanded) Color.White else DavePalette.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (planningExpanded) DavePlanningPanel { planningExpanded = false }
        if (tagsExpanded && editingTitle != null && onEditingTitleChange != null) {
            DaveTagEditorPanel(editingTitle, onEditingTitleChange, { tagsExpanded = false })
        }
        if (reminderTimes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reminderTimes.sorted().forEach { time ->
                    ReminderTimeChip(
                        time = time,
                        onEdit = { onReminderEdit(time) },
                        onRemove = { onReminderRemove(time) },
                    )
                }
            }
        }
    }
}

@Composable
fun DaveTodoEditScope(
    scope: TodoChangeScope,
    onScopeSelected: (TodoChangeScope) -> Unit,
    onStopRecurrence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("循环修改范围", color = DavePalette.Meta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        TodoScopeOption(
            label = "仅本次",
            selected = scope == TodoChangeScope.ONLY_THIS,
            onClick = { onScopeSelected(TodoChangeScope.ONLY_THIS) },
        )
        TodoScopeOption(
            label = "本次及以后",
            selected = scope == TodoChangeScope.THIS_AND_FUTURE,
            onClick = { onScopeSelected(TodoChangeScope.THIS_AND_FUTURE) },
        )
        TodoScopeOption(
            label = "停止循环",
            selected = false,
            onClick = onStopRecurrence,
            destructive = true,
        )
    }
}

@Composable
private fun TodoScopeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val foreground = if (destructive) DavePalette.Urgent else DavePalette.HeaderGreenDark
    Text(
        text = label,
        color = foreground,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DavePalette.CurrentWeek else DavePalette.CardMuted.copy(alpha = .72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun ReminderTimeChip(
    time: LocalTime,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(DavePalette.CardMuted.copy(alpha = .82f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d:%02d".format(time.hour, time.minute),
            color = DavePalette.Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(start = 8.dp, top = 5.dp, bottom = 5.dp),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "删除 ${time} 提醒",
                tint = DavePalette.Meta,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
fun DaveHabitQuickOptions(
    period: HabitPeriod,
    targetCount: Int,
    intervalDays: Int = 3,
    onPeriodSelected: (HabitPeriod) -> Unit,
    onTargetCountChanged: (Int) -> Unit,
    onIntervalDaysChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val intervalMode = period == HabitPeriod.EVERY_N_DAYS || period == HabitPeriod.AFTER_COMPLETION_N_DAYS
    val stepperValue = if (intervalMode) intervalDays else targetCount
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickIconOption(
            icon = Icons.Outlined.Today,
            description = "每日目标",
            modifier = Modifier.size(44.dp),
            selected = period == HabitPeriod.DAILY,
            onClick = { onPeriodSelected(HabitPeriod.DAILY) },
        )
        QuickIconOption(
            icon = Icons.Outlined.DateRange,
            description = "每周目标",
            modifier = Modifier.size(44.dp),
            selected = period == HabitPeriod.WEEKLY,
            onClick = { onPeriodSelected(HabitPeriod.WEEKLY) },
        )
        QuickIconOption(
            icon = Icons.Outlined.CalendarMonth,
            description = "每月指定日期",
            modifier = Modifier.size(44.dp),
            selected = period == HabitPeriod.MONTHLY,
            onClick = { onPeriodSelected(HabitPeriod.MONTHLY) },
        )
        QuickIconOption(
            icon = Icons.Outlined.Remove,
            modifier = Modifier.size(44.dp),
            description = if (intervalMode) "减少间隔天数" else "减少目标次数",
            selected = false,
            onClick = {
                if (stepperValue > 1) {
                    if (intervalMode) onIntervalDaysChanged(stepperValue - 1)
                    else onTargetCountChanged(stepperValue - 1)
                }
            },
        )
        Text(
            text = stepperValue.toString(),
            color = DavePalette.Ink,
            fontSize = if (stepperValue >= 100) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp),
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        QuickIconOption(
            icon = Icons.Outlined.Add,
            description = if (intervalMode) "增加间隔天数" else "增加目标次数",
            modifier = Modifier.size(44.dp),
            selected = false,
            onClick = {
                if (intervalMode) onIntervalDaysChanged(stepperValue + 1)
                else onTargetCountChanged(stepperValue + 1)
            },
        )
    }
}

/**
 * The two interval modes live beside the TAG button instead of competing with the
 * target stepper. The parent owns the TAG button so these controls intentionally
 * wrap content rather than claiming a full row.
 */
@Composable
fun DaveHabitCadenceOptions(
    period: HabitPeriod,
    onPeriodSelected: (HabitPeriod) -> Unit,
    modifier: Modifier = Modifier,
    fillRow: Boolean = false,
) {
    Row(
        modifier = if (fillRow) modifier.fillMaxWidth() else modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickIconOption(
            icon = Icons.Outlined.EventRepeat,
            description = "每N天一次",
            modifier = if (fillRow) Modifier.weight(1f) else Modifier,
            selected = period == HabitPeriod.EVERY_N_DAYS,
            onClick = { onPeriodSelected(HabitPeriod.EVERY_N_DAYS) },
        )
        QuickIconOption(
            icon = Icons.Outlined.Timer,
            description = "完成后隔N天",
            modifier = if (fillRow) Modifier.weight(1f) else Modifier,
            selected = period == HabitPeriod.AFTER_COMPLETION_N_DAYS,
            onClick = { onPeriodSelected(HabitPeriod.AFTER_COMPLETION_N_DAYS) },
        )
    }
}

@Composable
internal fun QuickIconOption(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) DavePalette.HeaderGreen else Color.White.copy(alpha = .5f), RoundedCornerShape(50))
            .border(1.dp, if (selected) DavePalette.HeaderGreenDark else DavePalette.Divider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else DavePalette.Ink,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun DaveAccentPalette(selected: Long?, onSelected: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).clip(CircleShape).background(selected?.let(::Color) ?: DavePalette.Ink, CircleShape)
            .clickable { open = true }.semantics { contentDescription = "打开拾色器" })
        Text("自选颜色", color = DavePalette.Ink, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(10.dp))
    }
    if (open) DaveColorPicker(selected, onSelected, { open = false })
}

@Composable
fun DaveInlineDraftCard(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    placeholder: String = "输入今天要做的事…",
    showActionButtons: Boolean = true,
    accentColor: Color? = null,
) {
    val foreground = accentColor ?: DavePalette.Ink
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        bringIntoViewRequester.bringIntoView()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DaveTaskCardHeight)
            .background(DavePalette.Card, RoundedCornerShape(13.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(DaveTaskCardHeight)
                .background(accentColor ?: DavePalette.Normal, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)),
        )
        Box(
            modifier = Modifier
                .padding(start = 17.dp)
                .size(34.dp)
                .border(3.dp, accentColor ?: Color.Black.copy(alpha = .35f), CircleShape),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp)
                .focusRequester(focusRequester)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) hadFocus = true
                    if (hadFocus && !state.isFocused && value.isBlank()) onCancelEmpty()
                },
            textStyle = TextStyle(color = foreground, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(accentColor ?: DavePalette.HeaderGreen),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (value.isNotBlank()) {
                    onConfirm()
                    focusManager.clearFocus()
                }
            }),
            decorationBox = { input ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = foreground.copy(alpha = .48f), fontSize = 17.sp)
                    input()
                }
            },
        )
        if (showActionButtons) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor ?: DavePalette.Completed, CircleShape)
                    .clickable(enabled = value.isNotBlank(), onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .padding(start = 7.dp, end = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DavePalette.CardMuted, CircleShape)
                    .clickable {
                        focusManager.clearFocus()
                        onCancelEmpty()
                    }
                    .semantics { contentDescription = "取消新建" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

private val DaveTaskCardHeight = 72.dp
private val DaveTodoCardHeight = DaveTaskCardHeight

@Composable
private fun rememberCompletionStamp(completed: Boolean, initiallyVisible: Boolean = false): MutableTransitionState<Boolean> {
    // Mounting a database-complete card (or landing proxy) is static. Only a
    // transition observed by this same owner can animate its stamp.
    val transition = remember(initiallyVisible && completed) { MutableTransitionState(completed) }
    transition.targetState = completed
    return transition
}

private const val CompletionTransitionMillis = 430L
