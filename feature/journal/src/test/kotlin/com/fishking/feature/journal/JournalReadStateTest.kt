package com.fishking.feature.journal

import com.fishking.core.model.JournalDocument
import com.fishking.core.model.JournalTimelineItem
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalReadStateTest {
    @Test fun normalEmptyResultIsDifferentFromReadFailure() = runTest {
        assertEquals(
            listOf(JournalTimelineLoadState.Loading, JournalTimelineLoadState.Ready(emptyList())),
            journalTimelineLoadStates { flowOf(emptyList()) }.toList(),
        )
        assertEquals(
            listOf(JournalTimelineLoadState.Loading, JournalTimelineLoadState.Failed),
            journalTimelineLoadStates { flow { error("Cannot read timeline") } }.toList(),
        )
    }

    @Test fun retryRecollectsAfterFailureAndKeepsTheOriginalRecords() = runTest {
        var shouldFail = true
        var reads = 0
        val entries = listOf(JournalTimelineItem("existing", LocalDate.of(2026, 9, 11), null, "标题", "原正文", 0))
        val states = journalTimelineLoadStates {
            reads++
            if (shouldFail) error("Synchronous repository failure")
            flowOf(entries)
        }
        assertEquals(JournalTimelineLoadState.Failed, states.toList().last())
        shouldFail = false
        assertEquals(JournalTimelineLoadState.Ready(entries), states.toList().last())
        assertEquals(2, reads)
    }

    @Test fun timelineCancellationAndFatalErrorsAreNotPresentedAsReadFailures() = runTest {
        for (failure in listOf(CancellationException("Stopped"), AssertionError("Broken invariant"))) {
            val result = runCatching {
                journalTimelineLoadStates { flow { throw failure } }.toList()
            }
            assertSame(failure, result.exceptionOrNull())
        }
    }

    @Test fun failedDocumentReadNotifiesErrorAndCanBeRetriedWithoutWriting() = runTest {
        var errors = 0
        var shouldFail = true
        var reads = 0
        val documents = journalDocumentReadFlow(
            readDocument = {
                reads++
                if (shouldFail) error("Cannot read document")
                flowOf<JournalDocument?>(null)
            },
            onFailure = { errors++ },
        )
        assertEquals(listOf<JournalDocument?>(null), documents.toList())
        assertEquals(1, errors)
        shouldFail = false
        assertEquals(listOf<JournalDocument?>(null), documents.toList())
        assertEquals(1, errors)
        assertEquals(2, reads)
    }

    @Test fun documentCancellationDoesNotCallErrorHandler() = runTest {
        var notified = false
        val result = runCatching {
            journalDocumentReadFlow(
                readDocument = { flow { throw CancellationException("Stopped") } },
                onFailure = { notified = true },
            ).toList()
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(false, notified)
    }
}
