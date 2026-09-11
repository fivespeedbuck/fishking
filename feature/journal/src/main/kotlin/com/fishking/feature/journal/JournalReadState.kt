package com.fishking.feature.journal

import com.fishking.core.model.JournalDocument
import com.fishking.core.model.JournalTimelineItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal const val JOURNAL_READ_FAILURE_MESSAGE = "日记读取失败，原记录未改动"

internal sealed interface JournalTimelineLoadState {
    data object Loading : JournalTimelineLoadState
    data class Ready(val entries: List<JournalTimelineItem>) : JournalTimelineLoadState
    data object Failed : JournalTimelineLoadState
}

internal fun journalTimelineLoadStates(
    readTimeline: () -> Flow<List<JournalTimelineItem>>,
): Flow<JournalTimelineLoadState> = flow<JournalTimelineLoadState> {
    emit(JournalTimelineLoadState.Loading)
    // Construct inside the flow: synchronous DAO/repository failures need the
    // same recovery state as exceptions raised later during collection.
    emitAll(readTimeline().map { JournalTimelineLoadState.Ready(it) })
}.catch { error ->
    if (error is CancellationException || error !is Exception) throw error
    emit(JournalTimelineLoadState.Failed)
}

internal fun journalDocumentReadFlow(
    readDocument: () -> Flow<JournalDocument?>,
    onFailure: () -> Unit,
): Flow<JournalDocument?> = flow { emitAll(readDocument()) }.catch { error ->
    if (error is CancellationException || error !is Exception) throw error
    onFailure()
    emit(null)
}
