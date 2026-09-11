package com.fishking.feature.life

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishking.core.ui.DaveScreenFloatingAddAction
import com.fishking.core.ui.FishKingSection
import com.fishking.core.ui.DaveInlineDraftCard
import com.fishking.core.ui.DaveLifeGoalCard
import com.fishking.core.ui.DaveLifeGoalEditor
import com.fishking.core.ui.DaveLifeTimeline
import com.fishking.core.ui.DaveLifeTimelineEntry
import com.fishking.core.ui.DavePalette
import com.fishking.core.usecase.LifeRepository
import com.fishking.core.usecase.TagRepository
import java.time.LocalDate

@Composable
fun LifeScreen(
    lifeRepository: LifeRepository,
    tagRepository: TagRepository,
    today: LocalDate,
    onOpenJournal: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LifeViewModel = viewModel(factory = LifeViewModel.factory(lifeRepository, tagRepository)),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val draftVisible by viewModel.draftVisible.collectAsStateWithLifecycle()
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val editingTitle by viewModel.editingTitle.collectAsStateWithLifecycle()
    val editingNote by viewModel.editingNote.collectAsStateWithLifecycle()
    val editingTags by viewModel.editingTags.collectAsStateWithLifecycle()
    val editingType by viewModel.editingType.collectAsStateWithLifecycle()
    val editingAccentColor by viewModel.editingAccentColor.collectAsStateWithLifecycle()
    val timeline = items.flatMap { item ->
        item.value.events.map { event ->
            DaveLifeTimelineEntry(
                event = event,
                goalTitle = item.value.goal.title,
                hasLinkedJournal = event.occurredOn in item.value.linkedJournalDates,
            )
        }
    }.sortedWith(compareBy({ it.event.occurredOn }, { it.event.createdAt }, { it.event.position }))

    val listState = rememberLazyListState()

    // The draft is always the first lazy item. Returning to index zero is
    // necessary when it was opened from the floating action button after the
    // user had browsed existing goals or the timeline.
    LaunchedEffect(draftVisible) {
        if (draftVisible) listState.animateScrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (draftVisible) {
                item(key = "life-draft") {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        DaveInlineDraftCard(
                            value = draftTitle,
                            onValueChange = viewModel::updateDraft,
                            onConfirm = viewModel::confirmDraft,
                            onCancelEmpty = viewModel::cancelDraft,
                        )
                        Text(
                            "标题后可直接输入 #标签，默认建立一次性目标",
                            color = DavePalette.Ink.copy(alpha = .5f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 18.dp, top = 5.dp),
                        )
                    }
                }
            }

            val groupedItems = items.filter { it.value.currentResult != com.fishking.core.model.LifeGoalResult.CHECK } +
                items.filter { it.value.currentResult == com.fishking.core.model.LifeGoalResult.CHECK }
            itemsIndexed(groupedItems, key = { _, it -> it.value.goal.id }) { index, item ->
                if (item.value.currentResult == com.fishking.core.model.LifeGoalResult.CHECK &&
                    (index == 0 || groupedItems[index - 1].value.currentResult != com.fishking.core.model.LifeGoalResult.CHECK))
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = DavePalette.Divider)
                if (editingId == item.value.goal.id) {
                    DaveLifeGoalEditor(
                        title = editingTitle,
                        note = editingNote,
                        tags = editingTags,
                        type = editingType,
                        accentColor = editingAccentColor,
                        onAccentColorChange = viewModel::setEditingAccentColor,
                        events = item.value.events,
                        onTitleChange = viewModel::updateEditingTitle,
                        onNoteChange = viewModel::updateEditingNote,
                        onTagsChange = viewModel::updateEditingTags,
                        onTypeChange = viewModel::setEditingType,
                        onDeleteManualEvent = viewModel::deleteManualEvent,
                        onConfirm = viewModel::confirmEditing,
                        onCancelEmpty = viewModel::cancelEditing,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                } else {
                    DaveLifeGoalCard(
                        value = item.value,
                        tags = item.tags.map { it.name },
                        onToggleResult = { viewModel.toggleResult(item.value.goal.id, today) },
                        onAddToToday = { viewModel.addToToday(item.value.goal.id, today) },
                        onEdit = { viewModel.startEditing(item) },
                        onDelete = { viewModel.deleteGoal(item.value.goal.id) },
                        onMovePreview = { direction -> viewModel.previewMove(item.value.goal.id, direction) },
                        onMoveCommit = viewModel::commitMove,
                        onMoveCancel = viewModel::cancelMove,
                        onOpenJournal = onOpenJournal,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }

            item(key = "life-new-area") {
                NewGoalArea(
                    visible = !draftVisible && editingId == null,
                    isEmpty = items.isEmpty(),
                    onClick = viewModel::startDraft,
                )
            }

            item(key = "life-timeline") {
                DaveLifeTimeline(
                    entries = timeline,
                    onOpenJournal = onOpenJournal,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(112.dp)) }
        }

        DaveScreenFloatingAddAction(
            section = FishKingSection.LIFE,
            enabled = !draftVisible && editingId == null,
            onClick = viewModel::startDraft,
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
        )
    }
}

@Composable
private fun NewGoalArea(
    visible: Boolean,
    isEmpty: Boolean,
    onClick: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isEmpty) 112.dp else 64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isEmpty) "点这里，直接写第一条人生清单" else "+",
            color = DavePalette.Ink.copy(alpha = .56f),
            fontSize = if (isEmpty) 15.sp else 28.sp,
            textAlign = TextAlign.Center,
        )
    }
}
