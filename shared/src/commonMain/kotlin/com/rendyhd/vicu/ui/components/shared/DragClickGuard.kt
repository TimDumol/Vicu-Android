package com.rendyhd.vicu.ui.components.shared

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Keeps a press that became a drag from also counting as a click, for rows a long press picks up
 * (the task rows, the drawer, the Projects tab of Settings). The row's click handler sees the
 * release before the drag handle around it does, so a long press let go without moving would
 * otherwise open the row as well.
 */
internal class DragClickGuard {
    private var dragged = false

    /** A new press began: whatever the last one did no longer matters. */
    fun pressStarted() {
        dragged = false
    }

    /** The press was held long enough to pick the row up. */
    fun dragStarted() {
        dragged = true
    }

    /** Whether the click at the end of this press should go through. One click is dropped at most. */
    fun allowClick(): Boolean {
        val allowed = !dragged
        dragged = false
        return allowed
    }

    /** [onClick], unless it ends a press that picked the row up. */
    fun guarded(onClick: () -> Unit): () -> Unit = { if (allowClick()) onClick() }
}

/**
 * Tells [guard] about every press on this element. Put it on the row's outer element, also when
 * the row cannot drag (any more): a row that stops being draggable once picked up must still take
 * the next tap.
 */
internal fun Modifier.dragClickGuardPresses(guard: DragClickGuard): Modifier =
    pointerInput(guard) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            guard.pressStarted()
        }
    }
