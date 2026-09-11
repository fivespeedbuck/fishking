package com.fishking.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DaveCompletionGestureVisualTest {
    @Test
    fun completionStagesMapToTwentyTwentyFiveAndThirtyPercentOfCardWidth() {
        val atTwenty = daveCompletionGestureVisual(completed = false, gestureProgress = 2f / 3f)
        val atTwentyFive = daveCompletionGestureVisual(completed = false, gestureProgress = 5f / 6f)
        val atThirty = daveCompletionGestureVisual(completed = false, gestureProgress = 1f)

        assertEquals(0f, atTwenty.fill, .001f)
        assertEquals(1f, atTwentyFive.fill, .001f)
        assertEquals(0f, atTwentyFive.check, .001f)
        assertEquals(1f, atThirty.check, .001f)
    }

    @Test
    fun undoIsTheExactReverseOfCompletion() {
        val completion = daveCompletionGestureVisual(completed = false, gestureProgress = .4f)
        val undo = daveCompletionGestureVisual(completed = true, gestureProgress = -.6f)

        assertEquals(completion.completion, undo.completion, .001f)
        assertEquals(completion.fill, undo.fill, .001f)
        assertEquals(completion.check, undo.check, .001f)
    }

    @Test
    fun databaseAndLandedCompletionAreFullyResolvedWithoutAGestureClock() {
        val completed = daveCompletionGestureVisual(completed = true, gestureProgress = null)
        assertEquals(1f, completed.completion)
        assertEquals(1f, completed.fill)
        assertEquals(1f, completed.check)
        val open = daveCompletionGestureVisual(completed = false, gestureProgress = null)
        assertEquals(0f, open.completion)
        assertEquals(0f, open.fill)
        assertEquals(0f, open.check)
    }
}
