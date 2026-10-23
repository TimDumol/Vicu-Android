package com.rendyhd.vicu.ui.components.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A long press on a draggable row selects it (or drags it) and never also opens it. */
class DragClickGuardTest {

    @Test
    fun tapOpens() {
        val guard = DragClickGuard()
        guard.pressStarted()
        assertTrue(guard.allowClick())
    }

    @Test
    fun longPressLetGoWithoutMovingDoesNotOpen() {
        val guard = DragClickGuard()
        guard.pressStarted()
        guard.dragStarted()
        assertFalse(guard.allowClick())
    }

    @Test
    fun nextTapAfterADragOpensAgain() {
        val guard = DragClickGuard()
        guard.pressStarted()
        guard.dragStarted()
        // A drag that moved the row ends without a click; the next press starts clean.
        guard.pressStarted()
        assertTrue(guard.allowClick())
    }

    @Test
    fun onlyOneClickIsDropped() {
        val guard = DragClickGuard()
        guard.pressStarted()
        guard.dragStarted()
        assertFalse(guard.allowClick())
        // A click without a press (a screen reader's) after the dropped one goes through.
        assertTrue(guard.allowClick())
    }

    @Test
    fun guardedClickRunsOnlyForAPlainTap() {
        val guard = DragClickGuard()
        var opened = 0
        val onClick = guard.guarded { opened++ }
        guard.pressStarted()
        guard.dragStarted()
        onClick()
        assertEquals(0, opened)
        guard.pressStarted()
        onClick()
        assertEquals(1, opened)
    }
}
