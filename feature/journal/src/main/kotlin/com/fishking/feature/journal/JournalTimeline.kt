package com.fishking.feature.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fishking.core.ui.DavePalette
import com.fishking.core.ui.DaveJournalTimelineThumbnails
import com.fishking.core.ui.DaveScreenFloatingAddAction
import com.fishking.core.ui.DaveListInlineAddAction
import com.fishking.core.ui.FishKingSection
import com.fishking.core.usecase.JournalRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch
import java.time.YearMonth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween

internal fun journalTimeLabel(time: LocalTime?): String = if (time == null) "时间未记录" else {
    val period = when (time.hour) { in 0..5 -> "凌晨"; in 6..11 -> "上午"; in 12..13 -> "中午"; in 14..17 -> "下午"; else -> "晚上" }
    "$period ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

@Composable
fun JournalTimelineRoute(
    repository: JournalRepository,
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onEditingChanged: (Boolean) -> Unit = {},
    content: @Composable (JournalRepository, LocalDate, String, () -> Unit) -> Unit,
) {
    var firstMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var lastMonth by remember { mutableStateOf(firstMonth) }
    var retryToken by remember { mutableIntStateOf(0) }
    val timeline by remember(repository, firstMonth, lastMonth, retryToken) {
        journalTimelineLoadStates {
            repository.observeTimelineRange(firstMonth.atDay(1), lastMonth.atEndOfMonth())
        }
    }.collectAsStateWithLifecycle(JournalTimelineLoadState.Loading)
    val entries = (timeline as? JournalTimelineLoadState.Ready)?.entries.orEmpty()
    val listState = rememberLazyListState()
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val deleteScope = rememberCoroutineScope()
    val editingChanged by rememberUpdatedState(onEditingChanged)
    LaunchedEffect(activeId) { editingChanged(activeId != null) }
    DisposableEffect(Unit) { onDispose { editingChanged(false) } }
    var activeDay by rememberSaveable { mutableStateOf(selectedDate.toEpochDay()) }
    var activeTime by rememberSaveable { mutableStateOf<String?>(null) }
    var initialPositioned by remember(selectedDate) { mutableStateOf(false) }
    LaunchedEffect(selectedDate) {
        if (activeId != null && activeDay != selectedDate.toEpochDay()) activeId = null
        if (activeId == null && (selectedDate.isBefore(firstMonth.atDay(1)) || selectedDate.isAfter(lastMonth.atEndOfMonth()))) {
            firstMonth = YearMonth.from(selectedDate); lastMonth = firstMonth
        }
    }
    LaunchedEffect(selectedDate, entries) {
        if (activeId == null && !initialPositioned && entries.isNotEmpty()) {
            val index = entries.indexOfFirst { !it.date.isAfter(selectedDate) }
            if (index >= 0) listState.scrollToItem(index)
            initialPositioned = true
        }
    }
    val density = LocalDensity.current
    val threshold = with(density) { 54.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val monthScroll = remember(listState, threshold) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if ((!listState.canScrollForward && available.y < 0) || (!listState.canScrollBackward && available.y > 0)) {
                pull = (pull + available.y * .45f).coerceIn(-threshold * 1.8f, threshold * 1.8f)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val released = pull
            if (released <= -threshold) firstMonth = firstMonth.minusMonths(1)
            if (released >= threshold) lastMonth = lastMonth.plusMonths(1)
            animate(released, 0f, animationSpec = tween(200)) { value, _ -> pull = value }
            return if (released != 0f) available else Velocity.Zero
        }
    } }
    fun open(id: String, date: LocalDate, time: LocalTime?) {
        activeDay = date.toEpochDay(); activeTime = time?.toString(); activeId = id; onDateChange(date)
    }
    fun startNewEntry() {
        open(
            UUID.randomUUID().toString(),
            selectedDate,
            LocalTime.now().withSecond(0).withNano(0),
        )
    }
    if (activeId != null) {
        val id = activeId!!
        val scoped = remember(repository, id) { repository.forEntry(id, activeTime?.let(LocalTime::parse)) }
        BackHandler { activeId = null }
        Box(Modifier.fillMaxSize()) {
            content(scoped, LocalDate.ofEpochDay(activeDay), id) { activeId = null }
        }
    } else Box(Modifier.fillMaxSize().navigationBarsPadding().clipToBounds()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(monthScroll).graphicsLayer { translationY = pull }, contentPadding = PaddingValues(bottom = 110.dp)) {
            when (timeline) {
                JournalTimelineLoadState.Loading -> item {
                    Text("正在读取日记…", color = DavePalette.Meta, modifier = Modifier.padding(24.dp))
                }
                JournalTimelineLoadState.Failed -> item {
                    Column(Modifier.padding(24.dp)) {
                        Text(JOURNAL_READ_FAILURE_MESSAGE, color = DavePalette.Urgent)
                        androidx.compose.material3.TextButton(onClick = { retryToken++ }) { Text("重试") }
                    }
                }
                is JournalTimelineLoadState.Ready -> if (entries.isEmpty()) item {
                    Text("这个月还没有日记，点击 + 记录今天", color = DavePalette.Ink, modifier = Modifier.padding(24.dp))
                }
            }
            itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp)) {
                    Column(Modifier.width(62.dp).padding(top = 14.dp)) {
                        if (index == 0 || entries[index - 1].date != entry.date) {
                        Text(entry.date.dayOfMonth.toString().padStart(2, '0'), color = DavePalette.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("${entry.date.monthValue}月", color = DavePalette.Meta, fontSize = 13.sp)
                        Text(entry.date.year.toString(), color = DavePalette.Meta, fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DavePalette.Meta.copy(alpha = .5f)))
                    com.fishking.core.ui.DaveSwipeDeleteContainer(onDelete = { deleteId = entry.id },
                        modifier = Modifier.weight(1f).padding(start = 12.dp, top = 7.dp, bottom = 7.dp),
                        cardStyle = true,
                        cardColor = DavePalette.Card) {
                    Column(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DavePalette.Card, RoundedCornerShape(12.dp)).clickable { open(entry.id, entry.date, entry.time) }
                        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(journalTimeLabel(entry.time), color = DavePalette.Meta, fontSize = 12.sp)
                        if (entry.title.isNotBlank()) Text(entry.title, color = DavePalette.Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        if (entry.excerpt.isNotBlank()) Text(entry.excerpt, color = DavePalette.Ink.copy(alpha = .65f), fontSize = 15.sp, maxLines = 3)
                        if (entry.thumbnailPaths.isNotEmpty()) {
                            DaveJournalTimelineThumbnails(entry.thumbnailPaths)
                        }
                        if (entry.mediaCount > 0) Text("附件 ${entry.mediaCount}", color = DavePalette.Meta, fontSize = 12.sp)
                        if (entry.tags.isNotEmpty()) {
                            Text(
                                entry.tags.joinToString("  ") { "#$it" },
                                color = DavePalette.Meta,
                                fontSize = 12.sp,
                                maxLines = 2,
                            )
                        }
                        entry.location?.takeIf { it.isNotBlank() }?.let { Text("⌖ $it", color = DavePalette.Meta, fontSize = 12.sp) }
                    }
                    }
                }
            }
            if (timeline is JournalTimelineLoadState.Ready) {
                item(key = "journal-inline-add") {
                    DaveListInlineAddAction(
                        contentDescription = "新建日记",
                        enabled = true,
                        onClick = ::startNewEntry,
                    )
                }
            }
            item { Text("继续向上拉，加载更早一个月", color = DavePalette.HeaderGreenDark, fontSize = 12.sp, modifier = Modifier.padding(24.dp)) }
        }
        DaveScreenFloatingAddAction(
            section = FishKingSection.JOURNAL,
            enabled = true,
            onClick = ::startNewEntry,
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
        )
    }
    deleteId?.let { id -> androidx.compose.material3.AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("删除这条日记？") }, text = { Text("只从列表移除这一条，不影响同日其他日记或人生目标。") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { deleteScope.launch { repository.deleteEntry(id) }; deleteId = null }) { Text("删除") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteId = null }) { Text("取消") } }) }
}
