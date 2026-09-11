package com.fishking.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishking.core.model.LifeGoalEvent
import com.fishking.core.model.LifeGoalEventSource
import com.fishking.core.model.LifeGoalResult
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DaveLifeGoalCard(
    value: LifeGoalWithEvents,
    tags: List<String>,
    onToggleResult: () -> Unit,
    onAddToToday: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMovePreview: (Int) -> Unit,
    onMoveCommit: () -> Unit,
    onMoveCancel: () -> Unit,
    onOpenJournal: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val actionWidth = with(density) { 132.dp.toPx() }
    val moveThreshold = with(density) { 44.dp.toPx() }
    val scope = rememberCoroutineScope()
    var offsetX by remember(value.goal.id) { mutableFloatStateOf(0f) }
    var dragY by remember(value.goal.id) { mutableFloatStateOf(0f) }
    var journalDatesExpanded by remember(value.goal.id) { androidx.compose.runtime.mutableStateOf(false) }
    val accent = value.goal.accentColor?.let(::Color) ?: DavePalette.Life

    fun settle(target: Float, after: (() -> Unit)? = null) {
        scope.launch {
            animate(offsetX, target, animationSpec = tween(180)) { current, _ -> offsetX = current }
            after?.invoke()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(13.dp))
            .background(DavePalette.Card),
    ) {
        if (offsetX < -1f) DaveCardActionStrip(
            actions = listOf(
                DaveCardAction(Icons.Outlined.AddTask, "加入今日待办", accent) { settle(0f, onAddToToday) },
                DaveCardAction(Icons.Outlined.Edit, "编辑人生目标", accent) { settle(0f, onEdit) },
                DaveCardAction(Icons.Outlined.DeleteOutline, "删除人生目标", DavePalette.Urgent) { settle(0f, onDelete) },
            ),
            modifier = Modifier.align(Alignment.CenterEnd).width(132.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .offsetX(offsetX)
                .background(DavePalette.Card, RoundedCornerShape(13.dp))
                .pointerInput(value.goal.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            offsetX = (offsetX + amount).coerceIn(-actionWidth, 0f)
                        },
                        onDragEnd = {
                            settle(if (offsetX <= -actionWidth * .42f) -actionWidth else 0f)
                        },
                        onDragCancel = { settle(0f) },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
                    .pointerInput(value.goal.id) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, amount ->
                                change.consume()
                                dragY += amount.y
                                if (dragY >= moveThreshold) {
                                    onMovePreview(1)
                                    dragY = 0f
                                } else if (dragY <= -moveThreshold) {
                                    onMovePreview(-1)
                                    dragY = 0f
                                }
                            },
                            onDragEnd = {
                                dragY = 0f
                                onMoveCommit()
                            },
                            onDragCancel = {
                                dragY = 0f
                                onMoveCancel()
                            },
                        )
                    }
                    .semantics { contentDescription = "长按色带拖动排序" },
            )
            Box(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(
                        3.dp,
                        accent,
                        CircleShape,
                    )
                    .clickable(onClick = onToggleResult)
                    .semantics { contentDescription = "完成或取消人生清单" },
                contentAlignment = Alignment.Center,
            ) {
                if (value.currentResult == LifeGoalResult.CHECK) {
                    DaveDrawnCheck(accent, 1f, Modifier.size(28.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DaveTitle(
                        text = value.goal.title,
                        modifier = Modifier.weight(1f, fill = false),
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (value.currentResult == LifeGoalResult.CHECK) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    )
                    if (value.linkedJournalDates.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(start = 3.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (value.linkedJournalDates.size == 1) {
                                        onOpenJournal(value.linkedJournalDates.single())
                                    } else {
                                        journalDatesExpanded = !journalDatesExpanded
                                    }
                                }
                                .semantics {
                                    contentDescription = if (value.linkedJournalDates.size == 1) {
                                        "打开关联日记"
                                    } else {
                                        "选择关联日记日期"
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    if (value.goal.type == LifeGoalType.ONGOING && value.events.isNotEmpty()) {
                        Text(
                            lifeHistoryText(value.events, accent),
                            modifier = Modifier.widthIn(max = 104.dp).padding(start = 4.dp),
                            color = accent.copy(alpha = .7f),
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        )
                    }
                }
                value.goal.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = accent.copy(alpha = .72f), fontSize = 13.sp, maxLines = 1)
                }
                run {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (value.currentResult == LifeGoalResult.CHECK) "1/1" else "0/1",
                        modifier = Modifier.padding(end = 7.dp),
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        (listOf("人生清单") + tags).distinct().joinToString("  ") { "#$it" },
                        modifier = Modifier.weight(1f),
                        color = accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    }
                }
                if (journalDatesExpanded && value.linkedJournalDates.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        value.linkedJournalDates.forEach { date ->
                            JournalDateChip(date = date, onClick = {
                                journalDatesExpanded = false
                                onOpenJournal(date)
                            })
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = value.currentResult == LifeGoalResult.CHECK,
                enter = fadeIn(tween(430)) + scaleIn(tween(430), initialScale = .55f),
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Box(
                    modifier = Modifier.rotate(8f).border(2.dp, accent, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) { Text("CLEAR", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun JournalDateChip(date: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = .72f))
            .border(1.dp, DavePalette.Divider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = DavePalette.Meta, modifier = Modifier.size(13.dp))
        Text(
            date.format(compactEventDateFormatter),
            color = DavePalette.Ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun DaveLifeGoalEditor(
    title: String,
    note: String,
    tags: String,
    type: LifeGoalType,
    events: List<LifeGoalEvent>,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onTypeChange: (LifeGoalType) -> Unit,
    onDeleteManualEvent: (LifeGoalEvent) -> Unit,
    onConfirm: () -> Unit,
    onCancelEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Long? = null,
    onAccentColorChange: (Long?) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .28f), RoundedCornerShape(14.dp))
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        DaveInlineDraftCard(
            value = title,
            autoFocus = false,
            accentColor = accentColor?.let(::Color) ?: DavePalette.Life,
            onValueChange = onTitleChange,
            onConfirm = onConfirm,
            onCancelEmpty = onCancelEmpty,
        )
        DaveLifeLineField(
            value = note,
            color = accentColor?.let(::Color) ?: DavePalette.Life,
            onValueChange = onNoteChange,
            hint = "一行备注（可不填）",
        )
        DaveLifeLineField(
            value = tags,
            color = accentColor?.let(::Color) ?: DavePalette.Life,
            onValueChange = onTagsChange,
            hint = "#旅行  #健康",
        )
        DaveMacaronPalette(accentColor, onAccentColorChange, Modifier.padding(horizontal = 14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LifeTypeButton(
                icon = Icons.Outlined.Flag,
                description = "一次性目标",
                selected = type == LifeGoalType.ONE_TIME,
            ) { onTypeChange(LifeGoalType.ONE_TIME) }
            LifeTypeButton(
                icon = Icons.Outlined.AllInclusive,
                description = "持续性目标",
                selected = type == LifeGoalType.ONGOING,
            ) { onTypeChange(LifeGoalType.ONGOING) }
        }
        if (events.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                events.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = .38f), RoundedCornerShape(7.dp))
                            .padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (event.result == LifeGoalResult.CHECK) "✓" else "×",
                            color = if (event.result == LifeGoalResult.CHECK) DavePalette.Completed else DavePalette.Ink.copy(alpha = .52f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            event.occurredOn.format(eventDateFormatter),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            color = DavePalette.Ink.copy(alpha = .68f),
                            fontSize = 12.sp,
                        )
                        if (event.source == LifeGoalEventSource.MANUAL) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "删除这条手动结果",
                                tint = DavePalette.Urgent,
                                modifier = Modifier.size(29.dp).clip(CircleShape).clickable { onDeleteManualEvent(event) }.padding(4.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = "由关联待办自动记录",
                                tint = DavePalette.Meta,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaveLifeLineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    color: Color = DavePalette.Ink,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(DavePalette.Card, RoundedCornerShape(9.dp))
            .border(1.dp, DavePalette.Divider, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        singleLine = true,
        textStyle = TextStyle(color = color, fontSize = 15.sp),
        cursorBrush = SolidColor(color),
        decorationBox = { input ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text(hint, color = color.copy(alpha = .48f), fontSize = 14.sp)
                input()
            }
        },
    )
}

@Composable
private fun LifeTypeButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) DavePalette.HeaderGreen else DavePalette.Card, CircleShape)
            .border(1.dp, if (selected) DavePalette.HeaderGreenDark else DavePalette.Divider, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else DavePalette.Ink, modifier = Modifier.size(22.dp))
    }
}

data class DaveLifeTimelineEntry(
    val event: LifeGoalEvent,
    val goalTitle: String,
    val hasLinkedJournal: Boolean,
)

@Composable
fun DaveLifeTimeline(
    entries: List<DaveLifeTimelineEntry>,
    onOpenJournal: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DavePalette.Card.copy(alpha = .9f), RoundedCornerShape(14.dp))
            .padding(vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Timeline, contentDescription = null, tint = DavePalette.HeaderGreenDark, modifier = Modifier.size(20.dp))
            Text(
                "人生轨迹",
                color = DavePalette.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
        if (entries.isEmpty()) {
            Text(
                "点一次左侧圆圈，第一条真实记录就会出现在这里",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                color = DavePalette.Ink.copy(alpha = .48f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
            ) {
                entries.forEach { entry -> TimelineBubble(entry, onOpenJournal) }
            }
        }
    }
}

@Composable
private fun TimelineBubble(
    entry: DaveLifeTimelineEntry,
    onOpenJournal: (LocalDate) -> Unit,
) {
    val checked = entry.event.result == LifeGoalResult.CHECK
    val color = if (checked) DavePalette.Completed else DavePalette.Ink.copy(alpha = .42f)
    Column(
        modifier = Modifier.width(94.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                drawLine(
                    color = DavePalette.Divider,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height * .84f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height * .84f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .border(1.dp, color.copy(alpha = .45f), RoundedCornerShape(10.dp))
                        .then(
                            if (entry.hasLinkedJournal) {
                                Modifier
                                    .clickable { onOpenJournal(entry.event.occurredOn) }
                                    .semantics { contentDescription = "打开 ${entry.event.occurredOn} 的关联日记" }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.goalTitle,
                            modifier = Modifier.weight(1f, fill = false),
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.hasLinkedJournal) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = DavePalette.Meta,
                                modifier = Modifier.padding(start = 3.dp).size(13.dp),
                            )
                        }
                        Text(
                            text = if (checked) "✓" else "×",
                            modifier = Modifier.padding(start = 3.dp),
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Canvas(Modifier.size(width = 10.dp, height = 7.dp)) {
                    val tail = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    }
                    drawPath(tail, Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(color, CircleShape),
                )
            }
        }
        Text(
            entry.event.occurredOn.format(compactEventDateFormatter),
            color = DavePalette.Ink.copy(alpha = .65f),
            fontSize = 10.sp,
        )
    }
}

@Composable
fun DaveFloatingAddButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(DavePalette.Completed, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "新建" },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color.White, fontSize = 44.sp)
    }
}

private fun Modifier.offsetX(offset: Float): Modifier = offset {
    IntOffset(offset.roundToInt(), 0)
}

private fun lifeHistoryText(events: List<LifeGoalEvent>, accent: Color): AnnotatedString = buildAnnotatedString {
    events.forEachIndexed { index, event ->
        if (index > 0) append(' ')
        withStyle(
            SpanStyle(
                color = if (event.result == LifeGoalResult.CHECK) {
                    accent
                } else {
                    accent.copy(alpha = .48f)
                },
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(if (event.result == LifeGoalResult.CHECK) "✓" else "×")
        }
    }
}

private val eventDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)
private val compactEventDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.SIMPLIFIED_CHINESE)
