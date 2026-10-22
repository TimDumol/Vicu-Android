package com.rendyhd.vicu.ui.components.fab

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.voice.HoldToTalk
import com.rendyhd.vicu.voice.TaskDefaults
import com.rendyhd.vicu.voice.VoiceBegin

/**
 * What the + button does besides its tap: holding it to say a task ([holdToTalk]) and dragging it
 * onto a header ([dropTargets]). Made once by the app and handed to [AppFab]; the app draws the
 * voice card, the dragged + and the hint from its state ([FabOverlays]).
 */
@Stable
class FabController(
    val holdToTalk: HoldToTalk,
    val hint: FabHint,
    val dropTargets: FabDropTargets = FabDropTargets(),
) {
    /** Where the dragged + is, in root coordinates; null when nothing is dragged. */
    var dragPoint by mutableStateOf<Offset?>(null)
        private set

    /** How the confirmation phrases dates (the user's clock), kept current by the app. */
    var format: DateDisplayFormat = DateDisplayFormat.system(true)

    /** Opens quick add with a dropped target's date and project. */
    var onDrop: (FabDropTarget) -> Unit = {}

    /** The + was tapped: quick add opens (counted for the hint). */
    var onTapped: () -> Unit = {}

    /**
     * The + was held: starts listening. True when the voice card is up; otherwise the gesture is
     * over (a prompt, the system speech screen, or [openQuickAdd] when the device cannot listen).
     */
    fun startHold(defaults: TaskDefaults, openQuickAdd: () -> Unit): Boolean = begin(handsFree = false, defaults, openQuickAdd)

    /** "Say a task" for a screen reader: listens without a hold; the card has Add and Cancel. */
    fun sayTask(defaults: TaskDefaults, openQuickAdd: () -> Unit) {
        begin(handsFree = true, defaults, openQuickAdd)
    }

    private fun begin(handsFree: Boolean, defaults: TaskDefaults, openQuickAdd: () -> Unit): Boolean {
        hint.dismiss()
        return when (holdToTalk.begin(format, handsFree, defaults)) {
            VoiceBegin.STARTED -> true
            VoiceBegin.UNAVAILABLE -> {
                openQuickAdd()
                false
            }
            else -> false
        }
    }

    fun startDrag(screen: String?, at: Offset) {
        hint.dismiss()
        dropTargets.dragging = true
        dragTo(screen, at)
    }

    fun dragTo(screen: String?, at: Offset) {
        dragPoint = at
        dropTargets.hovered = dropTargets.hit(screen, at.x, at.y)?.first
    }

    /** The + was let go at [at]: quick add with the target under it, nothing elsewhere. */
    fun drop(screen: String?, at: Offset) {
        val target = dropTargets.hit(screen, at.x, at.y)?.second
        endDrag()
        if (target != null) onDrop(target)
    }

    fun endDrag() {
        dragPoint = null
        dropTargets.dragging = false
        dropTargets.hovered = null
    }
}
