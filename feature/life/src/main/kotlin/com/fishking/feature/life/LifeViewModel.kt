package com.fishking.feature.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fishking.core.model.LifeGoalEvent
import com.fishking.core.model.LifeGoalType
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.model.Tag
import com.fishking.core.usecase.LifeRepository
import com.fishking.core.usecase.TagRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class LifeGoalUiItem(
    val value: LifeGoalWithEvents,
    val tags: List<Tag>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LifeViewModel(
    private val lifeRepository: LifeRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val repositoryItems = lifeRepository.observeGoals()
        .flatMapLatest(::withTags)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val previewOrder = MutableStateFlow<List<String>?>(null)
    private var moveCommitJob: Job? = null
    val items: StateFlow<List<LifeGoalUiItem>> = combine(repositoryItems, previewOrder) { values, order ->
        if (order == null) values else {
            val byId = values.associateBy { it.value.goal.id }
            order.mapNotNull(byId::get) + values.filterNot { it.value.goal.id in order }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draftVisible = MutableStateFlow(false)
    val draftTitle = MutableStateFlow("")
    val editingId = MutableStateFlow<String?>(null)
    val editingTitle = MutableStateFlow("")
    val editingNote = MutableStateFlow("")
    val editingTags = MutableStateFlow("")
    val editingType = MutableStateFlow(LifeGoalType.ONE_TIME)

    private fun withTags(goals: List<LifeGoalWithEvents>): Flow<List<LifeGoalUiItem>> {
        if (goals.isEmpty()) return flowOf(emptyList())
        val itemFlows = goals.map { value ->
            tagRepository.observeLifeGoalTags(value.goal.id).map { tags ->
                LifeGoalUiItem(value, tags)
            }
        }
        return combine(itemFlows) { values -> values.toList() }
    }

    fun startDraft() {
        cancelEditing()
        draftTitle.value = ""
        draftVisible.value = true
    }

    fun updateDraft(value: String) {
        draftTitle.value = value.replace('\n', ' ')
    }

    fun cancelDraft() {
        draftVisible.value = false
        draftTitle.value = ""
    }

    fun cancelDraftIfBlank() {
        if (draftTitle.value.isBlank()) cancelDraft()
    }

    fun confirmDraft() {
        val input = parseInput(draftTitle.value)
        if (input.title.isEmpty()) return
        viewModelScope.launch {
            val id = lifeRepository.createGoal(input.title)
            if (input.tags.isNotEmpty()) tagRepository.setLifeGoalTags(id, input.tags)
            cancelDraft()
        }
    }

    fun startEditing(item: LifeGoalUiItem) {
        cancelDraft()
        editingId.value = item.value.goal.id
        editingTitle.value = item.value.goal.title
        editingNote.value = item.value.goal.note.orEmpty()
        editingTags.value = item.tags.joinToString(" ") { "#${it.name}" }
        editingType.value = item.value.goal.type
    }

    fun updateEditingTitle(value: String) {
        editingTitle.value = value.replace('\n', ' ')
    }

    fun updateEditingNote(value: String) {
        editingNote.value = value.replace('\n', ' ')
    }

    fun updateEditingTags(value: String) {
        editingTags.value = value.replace('\n', ' ')
    }

    fun setEditingType(value: LifeGoalType) {
        editingType.value = value
    }

    fun cancelEditing() {
        editingId.value = null
        editingTitle.value = ""
        editingNote.value = ""
        editingTags.value = ""
        editingType.value = LifeGoalType.ONE_TIME
    }

    fun confirmEditing() {
        val goalId = editingId.value ?: return
        val titleInput = parseInput(editingTitle.value)
        if (titleInput.title.isEmpty()) return
        val tagInput = parseInput(editingTags.value)
        val tags = (titleInput.tags + tagInput.tags).distinctBy(String::lowercase)
        viewModelScope.launch {
            lifeRepository.updateGoal(
                goalId = goalId,
                title = titleInput.title,
                note = editingNote.value,
                type = editingType.value,
            )
            tagRepository.setLifeGoalTags(goalId, tags)
            cancelEditing()
        }
    }

    fun toggleResult(goalId: String, date: LocalDate) {
        viewModelScope.launch { lifeRepository.toggleManualResult(goalId, date) }
    }

    fun deleteManualEvent(event: LifeGoalEvent) {
        viewModelScope.launch { lifeRepository.deleteManualEvent(event.id) }
    }

    fun addToToday(goalId: String, today: LocalDate) {
        viewModelScope.launch { lifeRepository.addToDate(goalId, today) }
    }

    fun deleteGoal(goalId: String) {
        if (editingId.value == goalId) cancelEditing()
        viewModelScope.launch { lifeRepository.deleteGoal(goalId) }
    }

    fun previewMove(goalId: String, direction: Int) {
        val current = items.value.map { it.value.goal.id }.toMutableList()
        val oldIndex = current.indexOf(goalId)
        if (oldIndex < 0) return
        val newIndex = (oldIndex + direction).coerceIn(current.indices)
        if (newIndex == oldIndex) return
        current.removeAt(oldIndex)
        current.add(newIndex, goalId)
        previewOrder.value = current
    }

    fun commitMove() {
        val order = previewOrder.value ?: return
        moveCommitJob?.cancel()
        moveCommitJob = viewModelScope.launch {
            try {
                lifeRepository.reorderGoals(order)
            } finally {
                previewOrder.value = null
            }
        }
    }

    fun cancelMove() {
        previewOrder.value = null
    }

    private data class ParsedInput(val title: String, val tags: List<String>)

    private fun parseInput(raw: String): ParsedInput {
        val tagMatches = TAG_PATTERN.findAll(raw).toList()
        val tags = tagMatches.map { it.groupValues[1].trimEnd(',', '，', '.', '。') }
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
        val title = tagMatches.fold(raw) { text, match -> text.replace(match.value, " ") }
            .trim()
            .replace(WHITESPACE_PATTERN, " ")
        return ParsedInput(title, tags)
    }

    companion object {
        private val TAG_PATTERN = Regex("(?<!\\S)#([^\\s#]+)")
        private val WHITESPACE_PATTERN = Regex("\\s+")

        fun factory(
            lifeRepository: LifeRepository,
            tagRepository: TagRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LifeViewModel(lifeRepository, tagRepository) }
        }
    }
}
