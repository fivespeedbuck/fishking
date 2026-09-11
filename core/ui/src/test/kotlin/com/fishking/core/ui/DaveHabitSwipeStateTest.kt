package com.fishking.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaveHabitSwipeStateTest {
    @Test fun actionBackingStartsBehindTheTrailingCornerInsteadOfAtTheCardEdge() {
        assertEquals(235f, daveHabitActionRevealLeft(widthPx = 300f, offsetPx = -52f, cornerRadiusPx = 13f))
        assertEquals(0f, daveHabitActionRevealLeft(widthPx = 40f, offsetPx = -80f, cornerRadiusPx = 13f))
        assertEquals(300f, daveHabitActionRevealLeft(widthPx = 300f, offsetPx = 20f, cornerRadiusPx = 13f))
    }

    @Test fun cancelledLeftSwipeCannotBecomeCompletionEvenWithOvershootingFrames() {
        val state = DaveHabitSwipeState()
        state.beginDrag()
        state.dragBy(-18f, 112f)
        val settling = state.beginSettle(0f)
        listOf(0f, .25f, .75f, .999f, 1f, 1.008f, 1f).forEach { frame ->
            state.settleFrame(settling, frame)
            assertTrue(state.offsetPx in -18f..0f)
            assertEquals(DaveHabitSwipeDirection.LEFT, state.direction)
            assertEquals(0f, daveHabitSwipeFeather(state.offsetPx, 22f))
        }
        assertTrue(state.finishSettle(settling))
        assertEquals(DaveHabitSwipePhase.REST, state.phase)
    }

    @Test fun cancelledRightSwipeNeverRevealsLeftActionsAndFadeReturnsContinuously() {
        val state = DaveHabitSwipeState()
        state.beginDrag()
        state.dragBy(12f, 112f)
        val settling = state.beginSettle(0f)
        var previous = state.offsetPx
        (0..100).forEach { frame ->
            state.settleFrame(settling, frame / 100f)
            assertTrue(state.offsetPx in 0f..previous)
            assertEquals(DaveHabitSwipeDirection.RIGHT, state.direction)
            previous = state.offsetPx
        }
        state.settleFrame(settling, 1.02f)
        assertEquals(0f, state.offsetPx)
        assertEquals(0f, daveHabitSwipeFeather(state.offsetPx, 22f))
        assertTrue(daveHabitSwipeFeather(.01f, 22f) < .001f)
        assertTrue(state.finishSettle(settling))
    }

    @Test fun interruptedSettleCannotWriteOverNewPointerOrFinishItsAction() {
        val state = DaveHabitSwipeState()
        state.beginDrag()
        state.dragBy(-35f, 112f)
        val old = state.beginSettle(0f)
        state.settleFrame(old, .4f)
        val grabbedAt = state.offsetPx
        state.beginDrag()
        state.dragBy(-10f, 112f)
        state.settleFrame(old, .9f)
        assertEquals(grabbedAt - 10f, state.offsetPx)
        assertFalse(state.finishSettle(old))
        assertEquals(DaveHabitSwipePhase.DRAGGING, state.phase)
    }

    @Test fun openedActionsCloseWithoutCrossingIntoRightSwipe() {
        val state = DaveHabitSwipeState()
        state.beginDrag()
        state.dragBy(-80f, 112f)
        val opening = state.beginSettle(-112f)
        state.settleFrame(opening, 1f)
        assertTrue(state.finishSettle(opening))
        assertEquals(DaveHabitSwipePhase.ACTIONS, state.phase)
        state.beginDrag()
        state.dragBy(200f, 112f)
        assertEquals(0f, state.offsetPx)
        assertEquals(DaveHabitSwipeDirection.LEFT, state.direction)
    }

    @Test fun completionHandoffInvalidatesAnEarlierSettle() {
        val state = DaveHabitSwipeState()
        state.beginDrag()
        state.dragBy(48f, 112f)
        val old = state.beginSettle(0f)
        state.handOff()
        state.settleFrame(old, 1f)
        assertFalse(state.finishSettle(old))
        assertEquals(48f, state.offsetPx)
        assertEquals(DaveHabitSwipePhase.HANDOFF, state.phase)
    }
}
